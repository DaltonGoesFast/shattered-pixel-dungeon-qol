// Streamer.bot inline C# — control SPD Companion UI elements over UDP.
// Create one action (C06 - Companion Control) and point every Stream Deck key at it.
// Set these String arguments on each key:
//   companionControlTarget — companion element key (default: nine_challenge_deaths)
//   companionControlAction — show | hide | toggle (default: toggle)
//   companionUdpPort       — optional; default 5100

using System;

public class CPHInline
{
    public bool Execute()
    {
        string target = Arg("companionControlTarget", "nine_challenge_deaths")
            .Trim().ToLowerInvariant();
        string action = Arg("companionControlAction", "toggle")
            .Trim().ToLowerInvariant();

        int port = 5100;
        string portRaw = Arg("companionUdpPort", "");
        if (!string.IsNullOrWhiteSpace(portRaw))
            int.TryParse(portRaw.Trim(), out port);
        if (port < 1 || port > 65535)
            port = 5100;

        string json =
            "{\"ui\":\"control\",\"target\":\"" + JsonEsc(target)
            + "\",\"action\":\"" + JsonEsc(action) + "\"}";

        try
        {
            CPH.BroadcastUdp(port, json);
            CPH.LogInfo(
                "SendCompanionControlUdp: " + action + " " + target + " port=" + port
            );
            return true;
        }
        catch (Exception ex)
        {
            CPH.LogInfo("SendCompanionControlUdp FAILED: " + ex.Message);
            return false;
        }
    }

    string Arg(string name, string fallback)
    {
        string value = null;
        if (CPH.TryGetArg(name, out value) && !string.IsNullOrWhiteSpace(value))
            return value;
        return fallback ?? "";
    }

    static string JsonEsc(string value)
    {
        if (string.IsNullOrEmpty(value))
            return "";
        return value.Replace("\\", "\\\\")
            .Replace("\"", "\\\"")
            .Replace("\r", "\\r")
            .Replace("\n", "\\n")
            .Replace("\t", "\\t");
    }
}
