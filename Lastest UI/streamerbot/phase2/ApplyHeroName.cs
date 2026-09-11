// Streamer.bot inline C# — N04 Apply Hero Name (Stream Deck / hotkey)
// Writes empty JSON body for a following Run Program curl to /api/hero-name/apply.
// No System.Diagnostics — same pattern as R01.

using System;
using System.IO;

public class CPHInline
{
    const string BODY_FILE =
        @"C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\hero_name_apply_body.json";

    public bool Execute()
    {
        try
        {
            File.WriteAllText(BODY_FILE, "{}");
            CPH.SetArgument("heroNameApplyBodyPath", BODY_FILE);
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("ApplyHeroName write: " + ex.Message);
            return false;
        }
    }
}
