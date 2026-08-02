// Streamer.bot inline C# — parse /api/channel-points/convert JSON response.
// No Newtonsoft / System.Core — plain string parse.
// Used by CPR - 25/60/80 Donor Points actions.

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
                        CPH.SetArgument("apiOk", "false");
                        CPH.SetArgument("apiMessage", "Channel Points convert failed (empty response). Is the overlay running?");
                        return true;
                    }

        try
        {
            bool ok = ParseBool(raw, "ok");
            CPH.SetArgument("apiOk", ok ? "true" : "false");
            string msg = ParseStringOrNull(raw, "message");
            if (string.IsNullOrWhiteSpace(msg))
                msg = ok ? "Donor points granted." : "Channel Points convert failed.";
            CPH.SetArgument("apiMessage", msg);
        }
        catch (Exception ex)
        {
            CPH.LogInfo("ParseCprConvertResponse: " + ex.Message);
            CPH.SetArgument("apiOk", "false");
            CPH.SetArgument("apiMessage", "Channel Points convert parse error.");
        }
        return true;
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

    static string ParseStringOrNull(string json, string key)
    {
        int i = ValueIndex(json, key);
        if (i < 0) return "";
        return ReadJsonStringValue(json, i);
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
                else if (n == 'u' && i + 4 < tail.Length)
                {
                    string hex = tail.Substring(i + 1, 4);
                    int code;
                    if (int.TryParse(hex, System.Globalization.NumberStyles.HexNumber, null, out code))
                    {
                        sb.Append((char)code);
                        i += 4;
                    }
                    else sb.Append(n);
                }
                else sb.Append(n);
            }
            else sb.Append(c);
        }
        return sb.ToString();
    }
}
