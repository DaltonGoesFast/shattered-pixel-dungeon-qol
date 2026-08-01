// Streamer.bot inline C# — build POST body for /api/donation/superchat (R5).
// Writes donation_superchat_body.json for curl sub-action.

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_superchat_body.json";

    public bool Execute()
    {
        string user = "";
        if (!CPH.TryGetArg("userName", out user) || string.IsNullOrWhiteSpace(user))
            CPH.TryGetArg("user", out user);

        string currency = "USD";
        string cur = null;
        if (CPH.TryGetArg("currencyCode", out cur) && !string.IsNullOrWhiteSpace(cur))
            currency = cur.Trim().ToUpperInvariant();

        long micro = ArgLong("microAmount");
        bool isMember = ArgBool("userIsSponsor") || ArgBool("gifterIsSponsor");

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"microAmount\":").Append(micro).Append(",");
        sb.Append("\"currencyCode\":\"").Append(JsonEsc(currency)).Append("\",");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"isSubscribed\":false,");
        sb.Append("\"userIsSponsor\":").Append(isMember ? "true" : "false");
        sb.Append("}");

        WriteBody(sb.ToString());
        return true;
    }

    long ArgLong(string name)
    {
        string v = null;
        if (!CPH.TryGetArg(name, out v) || string.IsNullOrWhiteSpace(v))
            return 0;
        long n = 0;
        long.TryParse(v.Trim(), out n);
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
        try { File.WriteAllText(BODY_FILE, json); } catch (Exception ex) { CPH.LogInfo("BuildSuperchatBody: " + ex.Message); }
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
