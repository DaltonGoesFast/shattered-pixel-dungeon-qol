// Streamer.bot inline C# — parse /api/chat-command JSON response.
// No Newtonsoft / System.Core — plain string parse (same constraint as earn-points C#).
// Sub-action 1c in R1 - Chat router.

using System;
using System.Text;

public class CPHInline
{
    public bool Execute()
    {
        string raw = "";
        if (!TryGetNonempty("webRequestResponse", out raw))
            if (!TryGetNonempty("lastSubActionResponse", out raw))
                if (!TryGetNonempty("output0", out raw))
                    if (!TryGetNonempty("output1", out raw))
                    {
                        // Server down / restart: keep apiMessage empty so R1's
                        // "If apiMessage Is Null or Empty" branch stays silent
                        // (avoids chat spam on every message / !command).
                        CPH.LogInfo("ParseChatResponse: empty API response (is Lastest UI server.py running?)");
                        CPH.SetArgument("apiOk", "false");
                        CPH.SetArgument("apiMessage", "");
                        CPH.SetArgument("apiPts", "");
                        CPH.SetArgument("apiHasPresentation", "0");
                        CPH.SetArgument("apiPresentationChat", "");
                        return true;
                    }

        try
        {
            bool ok = ParseBool(raw, "ok");
            CPH.SetArgument("apiOk", ok ? "true" : "false");
            CPH.SetArgument("apiMessage", ParseStringOrNull(raw, "message"));
            CPH.SetArgument("apiPts", ParseNumberOrNull(raw, "pts"));

            bool hasPres = HasObject(raw, "presentation");
            CPH.SetArgument("apiHasPresentation", hasPres ? "1" : "0");
            string presChat = hasPres ? ParseNestedString(raw, "presentation", "chat") : "";
            CPH.SetArgument("apiPresentationChat", presChat);

            if (hasPres)
            {
                string kind = ParseNestedString(raw, "presentation", "kind");
                if (string.IsNullOrWhiteSpace(kind))
                    kind = "fard";

                if (kind.Equals("summon", StringComparison.OrdinalIgnoreCase))
                {
                    TryPlayPresentationSound(raw);
                }
                else
                {
                    TryPlayPresentationSound(raw);

                    string platform = "twitch";
                    string es = null;
                    string cs = null;
                    if (CPH.TryGetArg("eventSource", out es) && !string.IsNullOrWhiteSpace(es))
                        platform = es.Trim().ToLowerInvariant();
                    else if (CPH.TryGetArg("commandSource", out cs) && !string.IsNullOrWhiteSpace(cs))
                        platform = cs.Trim().ToLowerInvariant();

                    string user = "";
                    if (!CPH.TryGetArg("userName", out user) || string.IsNullOrWhiteSpace(user))
                        CPH.TryGetArg("user", out user);

                    // Queued RunAction does not inherit parent args — stash for R9LoadContext.cs.
                    CPH.SetGlobalVar("r9_userName", user ?? "", false);
                    CPH.SetGlobalVar("r9_apiPresentationChat", presChat ?? "", false);
                    CPH.SetGlobalVar("r9_commandSource", platform, false);

                    CPH.RunAction("R09 - Presentation", false);
                }
            }
        }
        catch (Exception ex)
        {
            // Silent in chat — bad/HTML responses during downtime would spam otherwise.
            CPH.LogInfo("ParseChatResponse: " + ex.Message);
            CPH.SetArgument("apiOk", "false");
            CPH.SetArgument("apiMessage", "");
            CPH.SetArgument("apiPts", "");
            CPH.SetArgument("apiHasPresentation", "0");
            CPH.SetArgument("apiPresentationChat", "");
        }
        return true;
    }

    void TryPlayPresentationSound(string raw)
    {
        string sound = ParseNestedString(raw, "presentation", "sound");
        float vol = ParseNestedFloat(raw, "presentation", "sound_volume", 1f);
        if (string.IsNullOrWhiteSpace(sound))
            return;
        try { CPH.PlaySound(sound, vol, false, "presentation", true); }
        catch (Exception ex) { CPH.LogInfo("ParseChatResponse sound: " + ex.Message); }
    }

    bool TryGetNonempty(string name, out string val)
    {
        val = "";
        string t = null;
        if (!CPH.TryGetArg(name, out t) || string.IsNullOrWhiteSpace(t))
            return false;
        val = t;
        return true;
    }

    static bool ParseBool(string json, string key)
    {
        int i = ValueIndex(json, key);
        if (i < 0) return false;
        string tail = json.Substring(i).TrimStart();
        return tail.StartsWith("true", StringComparison.OrdinalIgnoreCase);
    }

    static string ParseNumberOrNull(string json, string key)
    {
        int i = ValueIndex(json, key);
        if (i < 0) return "";
        string tail = json.Substring(i).TrimStart();
        if (tail.StartsWith("null", StringComparison.OrdinalIgnoreCase)) return "";
        int end = 0;
        while (end < tail.Length && (char.IsDigit(tail[end]) || tail[end] == '-')) end++;
        return end > 0 ? tail.Substring(0, end) : "";
    }

    static string ParseStringOrNull(string json, string key)
    {
        int i = ValueIndex(json, key);
        if (i < 0) return "";
        return ReadJsonStringValue(json, i);
    }

    static bool HasObject(string json, string key)
    {
        int i = ValueIndex(json, key);
        if (i < 0) return false;
        string tail = json.Substring(i).TrimStart();
        return tail.Length > 0 && tail[0] == '{';
    }

    static string ParseNestedString(string json, string objectKey, string fieldKey)
    {
        int i = IndexOfKey(json, objectKey);
        if (i < 0) return "";
        int start = json.IndexOf('{', i);
        if (start < 0) return "";
        int depth = 0;
        for (int p = start; p < json.Length; p++)
        {
            if (json[p] == '{') depth++;
            else if (json[p] == '}')
            {
                depth--;
                if (depth == 0)
                {
                    string block = json.Substring(start, p - start + 1);
                    int f = ValueIndex(block, fieldKey);
                    if (f < 0) return "";
                    return ReadJsonStringValue(block, f);
                }
            }
        }
        return "";
    }

    static int IndexOfKey(string json, string key)
    {
        string pat = "\"" + key + "\":";
        return json.IndexOf(pat, StringComparison.OrdinalIgnoreCase);
    }

    static int ValueIndex(string json, string key)
    {
        int i = IndexOfKey(json, key);
        if (i < 0) return -1;
        int p = i + ("\"" + key + "\":").Length;
        while (p < json.Length && char.IsWhiteSpace(json[p])) p++;
        return p;
    }

    static string ReadJsonStringValue(string json, int valueStart)
    {
        string tail = json.Substring(valueStart).TrimStart();
        if (tail.StartsWith("null", StringComparison.OrdinalIgnoreCase)) return "";
        if (tail.Length == 0 || tail[0] != '"') return "";

        var sb = new StringBuilder();
        for (int i = 1; i < tail.Length; i++)
        {
            char c = tail[i];
            if (c == '"') break;
            if (c == '\\' && i + 1 < tail.Length)
            {
                char n = tail[++i];
                if (n == '"' || n == '\\' || n == '/') sb.Append(n);
                else if (n == 'n') sb.Append('\n');
                else if (n == 'r') sb.Append('\r');
                else if (n == 't') sb.Append('\t');
                else sb.Append(n);
            }
            else sb.Append(c);
        }
        return sb.ToString();
    }

    static float ParseNestedFloat(string json, string objectKey, string fieldKey, float fallback)
    {
        int i = IndexOfKey(json, objectKey);
        if (i < 0) return fallback;
        int start = json.IndexOf('{', i);
        if (start < 0) return fallback;
        int depth = 0;
        for (int p = start; p < json.Length; p++)
        {
            if (json[p] == '{') depth++;
            else if (json[p] == '}')
            {
                depth--;
                if (depth == 0)
                {
                    string block = json.Substring(start, p - start + 1);
                    int f = ValueIndex(block, fieldKey);
                    if (f < 0) return fallback;
                    string tail = block.Substring(f).TrimStart();
                    if (tail.StartsWith("null", StringComparison.OrdinalIgnoreCase)) return fallback;
                    int end = 0;
                    while (end < tail.Length &&
                           (char.IsDigit(tail[end]) || tail[end] == '.' || tail[end] == '-'))
                        end++;
                    if (end == 0) return fallback;
                    float v;
                    return float.TryParse(tail.Substring(0, end), out v) ? v : fallback;
                }
            }
        }
        return fallback;
    }
}
