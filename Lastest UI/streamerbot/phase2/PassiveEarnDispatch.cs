// Streamer.bot inline C# — R03 Passive earn: build batch JSON for all present viewers.
// No System.Net / System.Diagnostics — writes passive_earn_batch.json; Run Program runs passive_earn_batch.py.

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BATCH_FILE =
        @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\passive_earn_batch.json";

    public bool Execute()
    {
        string platform = ResolvePlatform();
        var bodies = new List<string>();

        List<Dictionary<string, object>> users;
        if (CPH.TryGetArg("users", out users) && users != null && users.Count > 0)
        {
            foreach (var user in users)
            {
                string name = GetUserName(user);
                if (string.IsNullOrWhiteSpace(name))
                    continue;
                bool isSub = GetBool(user, "isSubscribed");
                bool isMember = GetBool(user, "isMember") || GetBool(user, "userIsSponsor");
                bodies.Add(BuildBody(name, platform, isSub, isMember));
            }
        }
        else
        {
            string name = "";
            if (!CPH.TryGetArg("presentUserName", out name) || string.IsNullOrWhiteSpace(name))
                CPH.TryGetArg("userName", out name);
            if (!string.IsNullOrWhiteSpace(name))
            {
                bool isSub = ArgBool("isSubscribed");
                bool isMember = ArgBool("userIsSponsor") || ArgBool("isMember");
                bodies.Add(BuildBody(name, platform, isSub, isMember));
            }
        }

        try
        {
            var sb = new StringBuilder();
            sb.Append("[");
            for (int i = 0; i < bodies.Count; i++)
            {
                if (i > 0) sb.Append(",");
                sb.Append(bodies[i]);
            }
            sb.Append("]");
            File.WriteAllText(BATCH_FILE, sb.ToString());
        }
        catch (Exception ex)
        {
            CPH.LogInfo("PassiveEarnDispatch write: " + ex.Message);
            return false;
        }

        CPH.LogInfo("PassiveEarnDispatch: queued " + bodies.Count + " viewer(s)");
        return bodies.Count > 0;
    }

    string ResolvePlatform()
    {
        string cs = null;
        string es = null;
        string pf = null;
        if (CPH.TryGetArg("commandSource", out cs) && !string.IsNullOrWhiteSpace(cs))
            return cs.Trim().ToLowerInvariant();
        if (CPH.TryGetArg("eventSource", out es) && !string.IsNullOrWhiteSpace(es))
            return es.Trim().ToLowerInvariant();
        if (CPH.TryGetArg("platform", out pf) && !string.IsNullOrWhiteSpace(pf))
            return pf.Trim().ToLowerInvariant();
        return "twitch";
    }

    static string GetUserName(Dictionary<string, object> user)
    {
        if (user == null)
            return "";
        object v;
        if (user.TryGetValue("userName", out v) && v != null)
            return v.ToString().Trim();
        if (user.TryGetValue("login", out v) && v != null)
            return v.ToString().Trim();
        if (user.TryGetValue("display", out v) && v != null)
            return v.ToString().Trim();
        return "";
    }

    static bool GetBool(Dictionary<string, object> user, string key)
    {
        if (user == null || !user.ContainsKey(key) || user[key] == null)
            return false;
        object v = user[key];
        if (v is bool)
            return (bool)v;
        string s = v.ToString().Trim().ToLowerInvariant();
        return s == "true" || s == "1" || s == "yes";
    }

    bool ArgBool(string name)
    {
        string v = null;
        if (!CPH.TryGetArg(name, out v) || string.IsNullOrWhiteSpace(v))
            return false;
        v = v.Trim().ToLowerInvariant();
        return v == "true" || v == "1" || v == "yes";
    }

    static string BuildBody(string username, string platform, bool isSub, bool isMember)
    {
        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"type\":\"earn.passive\",");
        sb.Append("\"username\":\"").Append(JsonEsc(username)).Append("\",");
        sb.Append("\"platform\":\"").Append(JsonEsc(platform)).Append("\",");
        sb.Append("\"context\":{");
        sb.Append("\"isSubscribed\":").Append(isSub ? "true" : "false").Append(",");
        sb.Append("\"isMember\":").Append(isMember ? "true" : "false").Append(",");
        sb.Append("\"isBroadcaster\":false,");
        sb.Append("\"bits\":0");
        sb.Append("}}");
        return sb.ToString();
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
