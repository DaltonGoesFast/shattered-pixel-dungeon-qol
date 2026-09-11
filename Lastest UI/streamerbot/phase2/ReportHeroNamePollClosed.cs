// Streamer.bot inline C# — N02 (Twitch Poll Completed) / N03 (YouTube Poll Closed)
// Builds hero_name_poll_result_body.json for a following Run Program curl sub-action.
// No System.Diagnostics / System.Net — same constraint as R01 BuildChatCommandBody.
// Optional: Set Argument heroNamePollPlatform = twitch|youtube before this runs.

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_FILE =
        @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\hero_name_poll_result_body.json";
    const string EXPECTED_TITLE = "Name the hero";

    public bool Execute()
    {
        CPH.SetArgument("heroNamePollResultBodyPath", "");

        string platform = DetectPlatform();
        string title = FirstArg("poll.question", "poll.title", "pollTitle", "title");
        if (string.IsNullOrWhiteSpace(title))
            title = EXPECTED_TITLE;

        // Skip unrelated polls
        if (!title.Trim().Equals(EXPECTED_TITLE, StringComparison.OrdinalIgnoreCase))
        {
            CPH.LogInfo("ReportHeroNamePollClosed: skip title='" + title + "'");
            return false;
        }

        var choices = new StringBuilder();
        choices.Append("[");
        int count = 0;
        // Deduplicate text (Twitch sometimes exposes 0-based and 1-based overlapping).
        // No List/HashSet — Streamer.bot inline C# is very limited.
        string seenKeys = "|";
        for (int i = 0; i < 5; i++)
        {
            string text = ChoiceText(i);
            if (string.IsNullOrWhiteSpace(text))
                continue;
            string key = text.Trim().ToLowerInvariant();
            if (seenKeys.IndexOf("|" + key + "|", StringComparison.Ordinal) >= 0)
                continue;
            seenKeys = seenKeys + key + "|";
            int votes = ChoiceVotes(i);
            if (count > 0) choices.Append(",");
            choices.Append("{");
            choices.Append("\"text\":\"").Append(JsonEsc(text.Trim())).Append("\",");
            choices.Append("\"votes\":").Append(votes);
            choices.Append("}");
            count++;
        }
        choices.Append("]");

        if (count < 2)
        {
            CPH.LogInfo("ReportHeroNamePollClosed: fewer than 2 choices for " + platform
                + " title='" + title + "' (YouTube vars may be unavailable)");
            return false;
        }

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"platform\":\"").Append(JsonEsc(platform)).Append("\",");
        sb.Append("\"title\":\"").Append(JsonEsc(title.Trim())).Append("\",");
        sb.Append("\"options\":").Append(choices.ToString());
        sb.Append("}");

        try
        {
            File.WriteAllText(BODY_FILE, sb.ToString());
            CPH.SetArgument("heroNamePollResultBodyPath", BODY_FILE);
            CPH.LogInfo("ReportHeroNamePollClosed " + platform + " choices=" + count + " wrote body");
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("ReportHeroNamePollClosed write: " + ex.Message);
            return false;
        }
    }

    string DetectPlatform()
    {
        string p;
        if (CPH.TryGetArg("heroNamePollPlatform", out p) && !string.IsNullOrWhiteSpace(p))
            return p.Trim().ToLowerInvariant();
        if (CPH.TryGetArg("eventSource", out p) && !string.IsNullOrWhiteSpace(p))
        {
            p = p.Trim().ToLowerInvariant();
            if (p.Contains("youtube")) return "youtube";
            if (p.Contains("twitch")) return "twitch";
        }
        if (CPH.TryGetArg("commandSource", out p) && !string.IsNullOrWhiteSpace(p))
        {
            p = p.Trim().ToLowerInvariant();
            if (p.Contains("youtube")) return "youtube";
            if (p.Contains("twitch")) return "twitch";
        }
        // Twitch poll vars often use poll.choice#; YouTube uses poll.option#
        string probe;
        if (CPH.TryGetArg("poll.option0.text", out probe) && !string.IsNullOrWhiteSpace(probe))
            return "youtube";
        return "twitch";
    }

    string ChoiceText(int i)
    {
        // YouTube Poll Closed (docs: poll.option0.text)
        string t = FirstArg(
            "poll.option" + i + ".text",
            "poll.option" + i + ".title",
            "poll.options." + i + ".text",
            "poll.Option" + i + ".text"
        );
        if (!string.IsNullOrWhiteSpace(t)) return t.Trim();
        // Twitch Poll Completed (1-based and 0-based variants)
        t = FirstArg(
            "poll.choice" + (i + 1) + ".title",
            "poll.choice" + i + ".title",
            "poll.choice" + (i + 1) + ".text",
            "poll.choice" + i + ".text"
        );
        return (t ?? "").Trim();
    }

    int ChoiceVotes(int i)
    {
        string v = FirstArg(
            "poll.option" + i + ".votes",
            "poll.options." + i + ".votes",
            "poll.choice" + (i + 1) + ".totalVotes",
            "poll.choice" + i + ".totalVotes",
            "poll.choice" + (i + 1) + ".votes",
            "poll.choice" + i + ".votes"
        );
        int n;
        if (!string.IsNullOrWhiteSpace(v) && int.TryParse(v.Trim(), out n))
            return Math.Max(0, n);
        return 0;
    }

    string FirstArg(params string[] names)
    {
        foreach (string name in names)
        {
            string v;
            if (CPH.TryGetArg(name, out v) && !string.IsNullOrWhiteSpace(v))
                return v;
        }
        return "";
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
