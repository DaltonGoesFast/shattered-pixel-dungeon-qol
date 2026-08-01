// Streamer.bot inline C# — build POST body for /api/donation/cheer (R4).
// Writes donation_cheer_body.json for curl sub-action.

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_cheer_body.json";

    public bool Execute()
    {
        string user = "";
        CPH.TryGetArg("userName", out user);

        int bits = ArgInt("bits");
        bool isSub = ArgBool("isSubscribed");
        bool isMember = ArgBool("userIsSponsor");

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"bits\":").Append(bits).Append(",");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"isSubscribed\":").Append(isSub ? "true" : "false").Append(",");
        sb.Append("\"userIsSponsor\":").Append(isMember ? "true" : "false");
        sb.Append("}");

        WriteBody(sb.ToString());
        return true;
    }

    int ArgInt(string name)
    {
        string v = null;
        if (!CPH.TryGetArg(name, out v) || string.IsNullOrWhiteSpace(v))
            return 0;
        int n = 0;
        int.TryParse(v.Trim(), out n);
        return n;
    }

    bool ArgBool(string name)
    {
        string v = null;
        if (!CPH.TryGetArg(name, out v) || string.IsNullOrWhiteSpace(v))
            return false;
        v = v.Trim().ToLowerInvariant();
        return v == "true" || v == "1" || v == "yes";
    }

    void WriteBody(string json)
    {
        try { File.WriteAllText(BODY_FILE, json); } catch (Exception ex) { CPH.LogInfo("BuildCheerBody: " + ex.Message); }
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
