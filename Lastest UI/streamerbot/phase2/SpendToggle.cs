// Streamer.bot inline C# — toggle spend_disabled.txt from Stream Deck Action Switch (R8).
// Assign this action to BOTH Toggle On and Toggle Off slots; branch on %state%.

using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spend_disabled.txt";

    public bool Execute()
    {
        try
        {
            string state = "0";
            CPH.TryGetArg("state", out state);

            if (state == "0")
            {
                File.WriteAllText(FILE, "1");   // spend disabled
                CPH.LogInfo("SpendToggle: OFF (state=0)");
            }
            else
            {
                if (File.Exists(FILE)) File.Delete(FILE);
                CPH.LogInfo("SpendToggle: ON (state=1)");
            }
        }
        catch (Exception ex) { CPH.LogInfo("SpendToggle: " + ex.Message); }
        return true;
    }
}
