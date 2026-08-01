// Streamer.bot inline C# — first sub-action in R9 - Presentation.
// Reads globals set by ParseChatResponse.cs before RunAction (queued actions lose parent args).

using System;

public class CPHInline
{
    public bool Execute()
    {
        try
        {
            string user = CPH.GetGlobalVar<string>("r9_userName", false);
            if (!string.IsNullOrWhiteSpace(user))
                CPH.SetArgument("userName", user);

            string chat = CPH.GetGlobalVar<string>("r9_apiPresentationChat", false) ?? "";
            CPH.SetArgument("apiPresentationChat", chat);

            string platform = CPH.GetGlobalVar<string>("r9_commandSource", false);
            if (!string.IsNullOrWhiteSpace(platform))
                CPH.SetArgument("commandSource", platform);
        }
        catch (Exception ex)
        {
            CPH.LogInfo("R9LoadContext: " + ex.Message);
        }
        return true;
    }
}
