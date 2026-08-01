// Streamer.bot inline C# — delete spend_disabled.txt (legacy; use SpendToggle.cs).
// Sub-action in R8 - Spend ON (replaces Action 24).

using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\spend_disabled.txt";

    public bool Execute()
    {
        try { if (File.Exists(FILE)) File.Delete(FILE); }
        catch (Exception ex) { CPH.LogInfo("SpendOn: " + ex.Message); }
        return true;
    }
}
