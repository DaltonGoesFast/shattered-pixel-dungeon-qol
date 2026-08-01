// Streamer.bot inline C# — build POST body for passive earn (Present Viewers).
// No Newtonsoft / System.Core — manual JSON string build.
// Sub-action 3a in R3 - Passive earn. Writes passive_earn_body.json for curl (step 3b).

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\passive_earn_body.json";
    public bool Execute()
    {
        string user = "";
        if (!CPH.TryGetArg("presentUserName", out user) || string.IsNullOrWhiteSpace(user))
            CPH.TryGetArg("userName", out user);

        string platform = "twitch";
        string cs = null;
        string pf = null;
        string es = null;
        if (CPH.TryGetArg("commandSource", out cs) && !string.IsNullOrWhiteSpace(cs))
            platform = cs.Trim().ToLowerInvariant();
        else if (CPH.TryGetArg("eventSource", out es) && !string.IsNullOrWhiteSpace(es))
            platform = es.Trim().ToLowerInvariant();
        else if (CPH.TryGetArg("platform", out pf) && !string.IsNullOrWhiteSpace(pf))
            platform = pf.Trim().ToLowerInvariant();

        bool isSub = ArgBool("isSubscribed");
        bool isMember = ArgBool("userIsSponsor") || ArgBool("isMember");

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"type\":\"earn.passive\",");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"platform\":\"").Append(JsonEsc(platform)).Append("\",");
        sb.Append("\"context\":{");
        sb.Append("\"isSubscribed\":").Append(isSub ? "true" : "false").Append(",");
        sb.Append("\"isMember\":").Append(isMember ? "true" : "false").Append(",");
        sb.Append("\"isBroadcaster\":false,");
        sb.Append("\"bits\":0");
        sb.Append("}}");

        CPH.SetArgument("passiveEarnBody", sb.ToString());
        try { File.WriteAllText(BODY_FILE, sb.ToString()); } catch (Exception ex) { CPH.LogInfo("BuildPassiveEarnBody file: " + ex.Message); }
        return true;
    }

    bool ArgBool(string name)
    {
        string v = null;
        if (!CPH.TryGetArg(name, out v) || string.IsNullOrWhiteSpace(v))
            return false;
        v = v.Trim().ToLowerInvariant();
        return v == "true" || v == "1" || v == "yes";
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
