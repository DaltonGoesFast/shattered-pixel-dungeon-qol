// Streamer.bot inline C# — Twitch Gift Bomb: POST each recipient to /api/donation/gift-membership (R6b).
// Single sub-action replaces build+curl loop. Requires System.Diagnostics (Process + curl.exe).
// If this fails to compile, keep old Action 40 gift-bomb Run Program until Phase 4.

using System;
using System.Diagnostics;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\donation_gift_body.json";
    const string API_URL = "http://127.0.0.1:5000/api/donation/gift-membership";

    public bool Execute()
    {
        int total = ArgInt("totalGifts");
        if (total <= 0) total = 1;
        if (total > 100) total = 100;

        string tier = "";
        CPH.TryGetArg("tier", out tier);
        bool isSub = ArgBool("isSubscribed");
        bool isMember = ArgBool("userIsSponsor");

        int posted = 0;
        for (int i = 0; i < total; i++)
        {
            string key = "gift.recipientUserName" + i;
            string user = "";
            if (!CPH.TryGetArg(key, out user) || string.IsNullOrWhiteSpace(user))
                continue;

            string json = BuildJson(user, tier, isSub, isMember);
            try
            {
                File.WriteAllText(BODY_FILE, json);
                if (PostCurl()) posted++;
            }
            catch (Exception ex)
            {
                CPH.LogInfo("BuildGiftTwitchGiftBomb idx " + i + ": " + ex.Message);
            }
        }

        CPH.SetArgument("giftBombPosted", posted.ToString());
        return true;
    }

    string BuildJson(string user, string tier, bool isSub, bool isMember)
    {
        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"tier\":\"").Append(JsonEsc(tier)).Append("\",");
        sb.Append("\"isSubscribed\":").Append(isSub ? "true" : "false").Append(",");
        sb.Append("\"userIsSponsor\":").Append(isMember ? "true" : "false");
        sb.Append("}");
        return sb.ToString();
    }

    bool PostCurl()
    {
        var psi = new ProcessStartInfo();
        psi.FileName = "C:\\Windows\\System32\\curl.exe";
        psi.Arguments = "-s -S -m 12 -X POST -H \"Content-Type: application/json\" --data-binary \"@" + BODY_FILE + "\" " + API_URL;
        psi.UseShellExecute = false;
        psi.RedirectStandardOutput = true;
        psi.CreateNoWindow = true;
        using (var proc = Process.Start(psi))
        {
            proc.StandardOutput.ReadToEnd();
            proc.WaitForExit(15000);
            return proc.ExitCode == 0;
        }
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

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
