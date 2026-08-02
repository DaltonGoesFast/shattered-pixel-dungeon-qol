// Streamer.bot inline C# — build + send paid-notice JSON to SPD Companion (UDP port 5100).
// Uses CPH.BroadcastUdp only (System.Net / System.Diagnostics are NOT referenced by default).
// Also writes companion_paid_notice_udp.json + sets companionPaidNoticeJson for a Run Program
// PowerShell unicast fallback if broadcast does not reach Godot (see apply doc).
//
// Optional args (Set Argument before this step):
//   companionUi        — superchat | gifted_membership | sub | highlight  (default: superchat)
//   companionTtlSec    — hold seconds (default: 6)
//   companionUdpPort   — default 5100
// Also reads trigger vars: userName/user, message/msg/comment, amount/amountFormatted,
//   gifterUserName, totalGifts/count, tier/subTier, cumulativeMonths/months

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE =
        @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\companion_paid_notice_udp.json";

    public bool Execute()
    {
        string ui = Arg("companionUi", "superchat").Trim().ToLowerInvariant();
        if (string.IsNullOrWhiteSpace(ui))
            ui = "superchat";

        // C02: companionUi=sub for Twitch sub + YouTube New Sponsor → YT becomes membership.
        // C03: companionUi=gifted_membership for YT gift + Twitch gift sub → Twitch becomes gifted_subs.
        string platform = NormalizePlatform(FirstArg("eventSource", "commandSource", "platform"));
        if ((ui == "sub" || ui == "subscription" || ui == "subscribe" || ui == "resub")
            && platform == "youtube")
            ui = "membership";
        if ((ui == "gifted_membership" || ui == "gift_membership" || ui == "membership_gift"
                || ui == "gifted" || ui == "yt_gift")
            && platform == "twitch")
            ui = "gifted_subs";

        string user = FirstArg("userName", "user", "gifterUserName", "displayName");
        string message = FirstArg("message", "msg", "comment", "text");
        string amount = FirstArg("amount", "amountFormatted", "value");
        string tier = FirstArg("tier", "subTier");
        string months = FirstArg("months", "cumulativeMonths", "multiMonthDuration");
        string count = FirstArg("count", "totalGifts", "gift_count", "total");

        double ttl = 6.0;
        string ttlRaw = Arg("companionTtlSec", "");
        if (!string.IsNullOrWhiteSpace(ttlRaw))
            double.TryParse(ttlRaw.Trim(), out ttl);
        if (ttl < 0.5)
            ttl = 6.0;

        int port = 5100;
        string portRaw = Arg("companionUdpPort", "");
        if (!string.IsNullOrWhiteSpace(portRaw))
            int.TryParse(portRaw.Trim(), out port);
        if (port < 1 || port > 65535)
            port = 5100;

        var sb = new StringBuilder(256);
        sb.Append('{');
        sb.Append("\"ui\":\"").Append(JsonEsc(ui)).Append("\",");
        if (!string.IsNullOrWhiteSpace(platform))
            sb.Append("\"platform\":\"").Append(JsonEsc(platform)).Append("\",");
        sb.Append("\"username\":\"").Append(JsonEsc(user)).Append("\",");
        sb.Append("\"message\":\"").Append(JsonEsc(message)).Append("\",");
        sb.Append("\"amount\":\"").Append(JsonEsc(amount)).Append("\",");
        sb.Append("\"tier\":\"").Append(JsonEsc(tier)).Append("\",");
        sb.Append("\"months\":\"").Append(JsonEsc(months)).Append("\",");
        sb.Append("\"count\":\"").Append(JsonEsc(count)).Append("\",");
        sb.Append("\"ttl_sec\":").Append(ttl.ToString(System.Globalization.CultureInfo.InvariantCulture));
        sb.Append('}');

        string json = sb.ToString();
        try
        {
            File.WriteAllText(BODY_FILE, json);
            CPH.SetArgument("companionPaidNoticeJson", json);
            CPH.SetArgument("companionPaidNoticeBodyPath", BODY_FILE);
            CPH.BroadcastUdp(port, json);
            CPH.LogInfo("SendCompanionPaidNoticeUdp: BroadcastUdp " + ui + " port=" + port);
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("SendCompanionPaidNoticeUdp FAILED: " + ex.Message);
            return false;
        }
    }

    static string NormalizePlatform(string raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
            return "";
        string p = raw.Trim().ToLowerInvariant();
        if (p.IndexOf("youtube") >= 0 || p == "yt")
            return "youtube";
        if (p.IndexOf("twitch") >= 0)
            return "twitch";
        return p;
    }

    string FirstArg(params string[] names)
    {
        foreach (string n in names)
        {
            string v = Arg(n, null);
            if (!string.IsNullOrWhiteSpace(v))
                return v.Trim();
        }
        return "";
    }

    string Arg(string name, string fallback)
    {
        string v = null;
        if (CPH.TryGetArg(name, out v) && !string.IsNullOrWhiteSpace(v))
            return v;
        return fallback ?? "";
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s))
            return "";
        return s.Replace("\\", "\\\\")
            .Replace("\"", "\\\"")
            .Replace("\r", "\\r")
            .Replace("\n", "\\n")
            .Replace("\t", "\\t");
    }
}
