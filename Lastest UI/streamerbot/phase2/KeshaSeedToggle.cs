// LEGACY — superseded by Set Command State. Do not use.
// Streamer.bot inline C# — Stream Deck toggle for native !kesha / !seed Command actions only.using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\kesha_seed_disabled.txt";

    public bool Execute()
    {
        try
        {
            string state = "0";
            CPH.TryGetArg("state", out state);

            if (state == "0")
            {
                File.WriteAllText(FILE, "1");   // !kesha / !seed disabled
                CPH.LogInfo("KeshaSeedToggle: OFF (state=0)");
            }
            else
            {
                if (File.Exists(FILE)) File.Delete(FILE);
                CPH.LogInfo("KeshaSeedToggle: ON (state=1)");
            }
        }
        catch (Exception ex) { CPH.LogInfo("KeshaSeedToggle: " + ex.Message); }
        return true;
    }
}
