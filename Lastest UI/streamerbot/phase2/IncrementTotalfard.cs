// Streamer.bot inline C# — increment totalfard.txt for !fard presentation (R9).
// Update TOTALFARD_PATH if your Lastest UI folder is elsewhere.

using System;
using System.IO;

public class CPHInline
{
    const string TOTALFARD_PATH = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\totalfard.txt";

    public bool Execute()
    {
        try
        {
            int n = 0;
            if (File.Exists(TOTALFARD_PATH))
            {
                string s = File.ReadAllText(TOTALFARD_PATH).Trim();
                int.TryParse(s, out n);
            }
            n += 1;
            File.WriteAllText(TOTALFARD_PATH, n.ToString());
            CPH.SetArgument("totalfard", n.ToString());
        }
        catch (Exception ex)
        {
            CPH.LogInfo("IncrementTotalfard: " + ex.Message);
        }
        return true;
    }
}
