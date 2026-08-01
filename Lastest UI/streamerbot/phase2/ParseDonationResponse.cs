// Streamer.bot inline C# — parse /api/donation/* JSON from curl %output0%.
// Optional thank-you chat (R4/R5/R6 step *c). Sets %donationChatMessage% when points awarded.

using System;
using System.Text;

public class CPHInline
{
    public bool Execute()
    {
        string raw = "";
        if (!TryGetNonempty("output0", out raw))
            if (!TryGetNonempty("lastSubActionResponse", out raw))
            {
                CPH.SetArgument("donationOk", "false");
                CPH.SetArgument("donationSkipped", "false");
                CPH.SetArgument("donationPointsAdded", "");
                CPH.SetArgument("donationChatMessage", "");
                return true;
            }

        try
        {
            bool ok = ParseBool(raw, "ok");
            bool skipped = ParseBool(raw, "skipped");
            string pts = ParseNumberOrNull(raw, "pointsAdded");

            CPH.SetArgument("donationOk", ok ? "true" : "false");
            CPH.SetArgument("donationSkipped", skipped ? "true" : "false");
            CPH.SetArgument("donationPointsAdded", pts);

            int n = 0;
            if (!string.IsNullOrEmpty(pts)) int.TryParse(pts, out n);

            if (ok && n > 0)
                CPH.SetArgument("donationChatMessage", "Thanks for the support! You earned " + n + " points!");
            else
                CPH.SetArgument("donationChatMessage", "");

            string platform = "twitch";
            string es = null;
            string cs = null;
            if (CPH.TryGetArg("eventSource", out es) && !string.IsNullOrWhiteSpace(es))
                platform = es.Trim().ToLowerInvariant();
            else if (CPH.TryGetArg("commandSource", out cs) && !string.IsNullOrWhiteSpace(cs))
                platform = cs.Trim().ToLowerInvariant();
            CPH.SetArgument("commandSource", platform);
        }
        catch (Exception ex)
        {
            CPH.LogInfo("ParseDonationResponse: " + ex.Message);
            CPH.SetArgument("donationOk", "false");
            CPH.SetArgument("donationChatMessage", "");
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
}
