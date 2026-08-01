// Streamer.bot inline C# — !seed
// Sets %gameSeed% from game_summary.json.

using System;
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
            string seed = data["seed"]?.ToString() ?? "Unknown";

            CPH.SetArgument("gameSeed", seed);
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("ReadGameSeed: " + ex.Message);
            return false;
        }
    }
}
