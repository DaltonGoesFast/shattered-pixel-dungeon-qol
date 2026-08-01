// Streamer.bot inline C# — !mimic / !tooth
// Sets %mimicPresent% to True if MimicTooth is in inventory or equipped.

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
        {
            CPH.LogInfo("CheckMimicTooth: file not found: " + GameSummaryPath);
            CPH.SetArgument("mimicPresent", "False");
            return false;
        }

        try
        {
            string jsonContent;
            using (var fs = new FileStream(GameSummaryPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (var sr = new StreamReader(fs))
                jsonContent = sr.ReadToEnd();

            JObject data = JObject.Parse(jsonContent);
            bool found = false;

            if (data["inventory"] != null)
            {
                foreach (var bag in (JArray)data["inventory"])
                {
                    if (bag["items"] == null)
                        continue;
                    foreach (var item in (JArray)bag["items"])
                    {
                        if (item["name"] != null &&
                            item["name"].ToString().Equals("MimicTooth", StringComparison.OrdinalIgnoreCase))
                        {
                            found = true;
                            break;
                        }
                    }
                    if (found)
                        break;
                }
            }

            if (!found && data["equipped"] != null)
            {
                foreach (var slot in (JObject)data["equipped"])
                {
                    if (slot.Value == null || slot.Value.Type == JTokenType.Null)
                        continue;
                    if (slot.Value["name"] != null &&
                        slot.Value["name"].ToString().Equals("MimicTooth", StringComparison.OrdinalIgnoreCase))
                    {
                        found = true;
                        break;
                    }
                }
            }

            CPH.SetArgument("mimicPresent", found ? "True" : "False");
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("CheckMimicTooth: " + ex.Message);
            CPH.SetArgument("mimicPresent", "False");
            return false;
        }
    }
}
