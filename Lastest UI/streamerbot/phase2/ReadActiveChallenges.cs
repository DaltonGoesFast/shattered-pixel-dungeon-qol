// Streamer.bot inline C# — !challenge / !challenges
// Sets %activeChallenges% from game_summary.json.

using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json.Linq;

public class CPHInline
{
    const string GameSummaryPath =
        @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\game_summary.json";

    public bool Execute()
    {
        if (!File.Exists(GameSummaryPath))
            return false;

        try
        {
            string jsonContent;
            using (var fs = new FileStream(GameSummaryPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (var sr = new StreamReader(fs))
                jsonContent = sr.ReadToEnd();

            JObject data = JObject.Parse(jsonContent);
            var challenges = data["challenges"] != null
                ? data["challenges"].ToObject<List<string>>() ?? new List<string>()
                : new List<string>();

            string result;
            if (challenges.Count == 9)
                result = "All Challenges Active (9 Challenges)";
            else if (challenges.Count > 0)
                result = string.Join(", ", challenges);
            else
                result = "None";

            CPH.SetArgument("activeChallenges", result);
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("ReadActiveChallenges: " + ex.Message);
            return false;
        }
    }
}
