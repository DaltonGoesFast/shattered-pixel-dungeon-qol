// Streamer.bot inline C# — build POST body for /api/chat-command (chat router).
// No Newtonsoft / System.Core — manual JSON string build.
// Sub-action 1a in R1 - Chat router. Also writes chat_command_body.json for curl (step 1b).

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\chat_command_body.json";
    public bool Execute()
    {
        string user = "";
        CPH.TryGetArg("userName", out user);
        string msg = "";
        if (!CPH.TryGetArg("rawMessage", out msg) || string.IsNullOrEmpty(msg))
            CPH.TryGetArg("message", out msg);

        // Dedicated Command actions handle these — skip R1 so chat is not spammed with "Unknown command".
        if (IsStreamInfoCommand(msg))
        {
            CPH.LogInfo("BuildChatCommandBody: skip stream-info command for R1: " + msg);
            return false;
        }

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

        // Message Received uses eventSource, not commandSource — set both for reply If/Else branches.
        CPH.SetArgument("commandSource", platform);

        bool isSub = ArgBool("isSubscribed");
        bool isMember = ArgBool("userIsSponsor") || ArgBool("isMember");
        bool isBroad = ArgBool("isBroadcaster");

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"rawMessage\":\"").Append(JsonEsc(msg)).Append("\",");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"platform\":\"").Append(JsonEsc(platform)).Append("\",");
        sb.Append("\"context\":{");
        sb.Append("\"isSubscribed\":").Append(isSub ? "true" : "false").Append(",");
        sb.Append("\"isMember\":").Append(isMember ? "true" : "false").Append(",");
        sb.Append("\"isBroadcaster\":").Append(isBroad ? "true" : "false").Append(",");
        sb.Append("\"bits\":0");
        sb.Append("}}");

        CPH.SetArgument("chatCommandBody", sb.ToString());
        // Prefer a unique body file so overlapping R01 runs cannot clobber each other.
        // Curl args can use: --data-binary "@%chatCommandBodyPath%"
        // Still write BODY_FILE for older curl lines that use the fixed path.
        try
        {
            string dir = Path.GetDirectoryName(BODY_FILE) ?? ".";
            string unique = Path.Combine(dir, "chat_command_body_" + Guid.NewGuid().ToString("N") + ".json");
            File.WriteAllText(unique, sb.ToString());
            CPH.SetArgument("chatCommandBodyPath", unique);
            File.WriteAllText(BODY_FILE, sb.ToString());
        }
        catch (Exception ex) { CPH.LogInfo("BuildChatCommandBody file: " + ex.Message); }
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

    static bool IsStreamInfoCommand(string msg)
    {
        if (string.IsNullOrWhiteSpace(msg))
            return false;
        msg = msg.Trim();
        if (!msg.StartsWith("!"))
            return false;
        int end = 1;
        while (end < msg.Length && !char.IsWhiteSpace(msg[end]))
            end++;
        if (end <= 1)
            return false;
        string cmd = msg.Substring(1, end - 1).ToLowerInvariant();
        return cmd == "kesha" || cmd == "mimic" || cmd == "tooth"
            || cmd == "seed" || cmd == "challenge" || cmd == "challenges";
    }
}
