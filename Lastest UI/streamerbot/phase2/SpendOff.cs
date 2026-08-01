// Streamer.bot inline C# — create spend_disabled.txt (legacy; use SpendToggle.cs).
// Sub-action in R8 - Spend OFF (replaces Action 23).
using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spend_disabled.txt";

    public bool Execute()
    {
        try { File.WriteAllText(FILE, "1"); }
        catch (Exception ex) { CPH.LogInfo("SpendOff: " + ex.Message); }
        return true;
    }
}
