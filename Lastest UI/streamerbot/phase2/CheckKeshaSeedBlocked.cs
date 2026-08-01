// LEGACY — superseded by Set Command State on Kesha/Seed Command entries. Do not use.
// Streamer.bot inline C# — gate native Command actions !kesha / !seed (not R1).using System;
using System.IO;

public class CPHInline
{
    const string FILE = @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\kesha_seed_disabled.txt";

    public bool Execute()
    {
        try
        {
            bool blocked = File.Exists(FILE);
            CPH.SetArgument("keshaSeedBlocked", blocked ? "True" : "False");
            CPH.LogInfo("CheckKeshaSeedBlocked: " + (blocked ? "blocked" : "allowed"));
        }
        catch (Exception ex)
        {
            CPH.LogInfo("CheckKeshaSeedBlocked: " + ex.Message);
            CPH.SetArgument("keshaSeedBlocked", "False");
        }
        return true;
    }
}
