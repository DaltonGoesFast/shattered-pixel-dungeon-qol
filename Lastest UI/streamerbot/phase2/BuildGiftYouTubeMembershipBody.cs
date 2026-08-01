// Streamer.bot inline C# — YouTube Gift Membership → /api/donation/gift-membership (R6c).
// Credits gifterUserName (payer). Writes donation_gift_body.json for curl.

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_gift_body.json";

    public bool Execute()
    {
        string user = "";
        CPH.TryGetArg("gifterUserName", out user);

        string tier = "";
        CPH.TryGetArg("tier", out tier);

        bool isMember = ArgBool("gifterIsSponsor") || ArgBool("userIsSponsor");

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"tier\":\"").Append(JsonEsc(tier)).Append("\",");
        sb.Append("\"isSubscribed\":false,");
        sb.Append("\"userIsSponsor\":").Append(isMember ? "true" : "false");
        sb.Append("}");

        try { File.WriteAllText(BODY_FILE, sb.ToString()); } catch (Exception ex) { CPH.LogInfo("BuildGiftYouTubeMembershipBody: " + ex.Message); }
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
