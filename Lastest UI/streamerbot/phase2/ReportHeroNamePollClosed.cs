// Streamer.bot inline C# — N02 (Twitch Poll Completed) / N03 (YouTube Poll Closed)
// Writes a platform-specific body JSON for the following curl sub-action.
// No System.Diagnostics / System.Net — same constraint as R01 BuildChatCommandBody.
// Optional: Set Argument heroNamePollPlatform = twitch|youtube before this runs.

using System;
using System.IO;
using System.Text;

public class CPHInline
{
    const string BODY_DIR =
        @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI";
    const string EXPECTED_TITLE = "Name the hero";

    public bool Execute()
    {
        CPH.SetArgument("heroNamePollResultBodyPath", "");

        string platform = DetectPlatform();
        // Twitch docs: poll.Title; YouTube docs: poll.question
        string title = FirstArg(
            "poll.question", "poll.Title", "poll.title", "pollTitle", "title"
        );
        if (string.IsNullOrWhiteSpace(title))
            title = EXPECTED_TITLE;

        if (!title.Trim().Equals(EXPECTED_TITLE, StringComparison.OrdinalIgnoreCase))
        {
            CPH.LogInfo("ReportHeroNamePollClosed: skip title='" + title + "'");
            return false;
        }

        bool youtube = platform == "youtube";
        var choices = new StringBuilder();
        choices.Append("[");
        int count = 0;
        int sumVotes = 0;
        // Deduplicate text (Twitch may expose overlapping indexes).
        // No List/HashSet — Streamer.bot inline C# is very limited.
        string seenKeys = "|";
        var voteLog = new StringBuilder();

        // YouTube: option0..; Twitch docs: choice# (try 1-based first, then 0-based).
        for (int i = 0; i < 5; i++)
        {
            string text = youtube ? YoutubeText(i) : TwitchText(i);
            if (string.IsNullOrWhiteSpace(text))
                continue;
            string key = text.Trim().ToLowerInvariant();
            if (seenKeys.IndexOf("|" + key + "|", StringComparison.Ordinal) >= 0)
                continue;
            seenKeys = seenKeys + key + "|";
            int votes = youtube ? YoutubeVotes(i) : TwitchVotes(i);
            sumVotes += votes;
            if (count > 0) choices.Append(",");
            choices.Append("{");
            choices.Append("\"text\":\"").Append(JsonEsc(text.Trim())).Append("\",");
            choices.Append("\"votes\":").Append(votes);
            choices.Append("}");
            if (voteLog.Length > 0) voteLog.Append(" | ");
            voteLog.Append(text.Trim()).Append("=").Append(votes);
            count++;
        }
        choices.Append("]");

        if (count < 2)
        {
            CPH.LogInfo("ReportHeroNamePollClosed: fewer than 2 choices for " + platform
                + " title='" + title + "' (poll vars may be unavailable)");
            return false;
        }

        int reportedTotal = ParseInt(FirstArg(
            "poll.totalVotes", "poll.TotalVotes", "poll.votes"
        ), -1);
        int reportedCount = ParseInt(FirstArg(
            "poll.options.count", "poll.choices.count", "poll.Choices.count"
        ), -1);

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"platform\":\"").Append(JsonEsc(platform)).Append("\",");
        sb.Append("\"title\":\"").Append(JsonEsc(title.Trim())).Append("\",");
        if (reportedTotal >= 0)
            sb.Append("\"totalVotes\":").Append(reportedTotal).Append(",");
        sb.Append("\"options\":").Append(choices.ToString());
        sb.Append("}");

        string bodyFile = Path.Combine(BODY_DIR, "hero_name_poll_result_body_" + platform + ".json");
        // Keep legacy shared path updated too (older curl configs).
        string legacyFile = Path.Combine(BODY_DIR, "hero_name_poll_result_body.json");

        try
        {
            File.WriteAllText(bodyFile, sb.ToString());
            File.WriteAllText(legacyFile, sb.ToString());
            CPH.SetArgument("heroNamePollResultBodyPath", bodyFile);
            CPH.LogInfo(
                "ReportHeroNamePollClosed " + platform
                + " choices=" + count
                + (reportedCount >= 0 ? (" reportedCount=" + reportedCount) : "")
                + " sumVotes=" + sumVotes
                + (reportedTotal >= 0 ? (" totalVotes=" + reportedTotal) : "")
                + " [" + voteLog.ToString() + "]"
            );
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
        string probe;
        if (CPH.TryGetArg("poll.option0.text", out probe) && !string.IsNullOrWhiteSpace(probe))
            return "youtube";
        return "twitch";
    }

    string YoutubeText(int i)
    {
        return FirstArg(
            "poll.option" + i + ".text",
            "poll.option" + i + ".title",
            "poll.options." + i + ".text"
        ).Trim();
    }

    int YoutubeVotes(int i)
    {
        return ParseInt(FirstArg(
            "poll.option" + i + ".votes",
            "poll.options." + i + ".votes"
        ), 0);
    }

    // Twitch Poll Completed docs use poll.choice# (1-based in practice).
    string TwitchText(int i)
    {
        // i is 0..4 → prefer choice1..choice5, then choice0..choice4
        string t = FirstArg(
            "poll.choice" + (i + 1) + ".title",
            "poll.choice" + (i + 1) + ".text"
        );
        if (!string.IsNullOrWhiteSpace(t)) return t.Trim();
        return FirstArg(
            "poll.choice" + i + ".title",
            "poll.choice" + i + ".text"
        ).Trim();
    }

    int TwitchVotes(int i)
    {
        // Prefer totalVotes (includes channel-point votes) over votes-only.
        int n = ParseInt(FirstArg(
            "poll.choice" + (i + 1) + ".totalVotes",
            "poll.choice" + (i + 1) + ".votes"
        ), -1);
        if (n >= 0) return n;
        return ParseInt(FirstArg(
            "poll.choice" + i + ".totalVotes",
            "poll.choice" + i + ".votes"
        ), 0);
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

    static int ParseInt(string v, int fallback)
    {
        int n;
        if (!string.IsNullOrWhiteSpace(v) && int.TryParse(v.Trim(), out n))
            return Math.Max(0, n);
        return fallback;
    }

    static string JsonEsc(string s)
    {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
            .Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
    }
}
