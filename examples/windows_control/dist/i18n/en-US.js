"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.WINDOWS_SETUP_EN_US = void 0;
exports.WINDOWS_SETUP_EN_US = {
    saving: "Saving connection settings", activating: "Activating Windows tools",
    title: "Connect Windows", subtitle: "Let AI organize files, edit content and run tasks on your PC.",
    changed: "Configuration changed. Save to verify this connection.", operationFailed: "Operation failed", hostUnavailable: "Host configuration API unavailable. Update Kiyori.",
    activationFailed: "Windows tools could not be enabled. Check the toolkit in Extensions.", notConfigured: "Not verified", checking: "Verifying connection and authentication…",
    invalidResponse: "Unrecognized response. Check the target URL and proxy configuration.", connected: "Connected and authenticated", saved: "Configuration saved. Connection status is shown above.",
    invalidConfig: "Paste complete JSON configuration with string URL and token fields.", failed: "Connection or operation incomplete", connection: "PC connection",
    addressHelp: "Use the PC IP and port for LAN, or a public FRP URL. HTTPS defaults to port 443 and supports a proxy path prefix.",
    address: "Connection URL", token: "Access token", hideToken: "Hide token", showToken: "Show token", hideAdvanced: "Hide advanced settings", advanced: "Advanced settings",
    timeout: "Request timeout (ms)", save: "Save and connect", recheck: "Verify saved connection", hideImport: "Hide import", import: "Import PC configuration",
    importHelp: "Paste connection configuration copied from the PC console. It contains an access token and is cleared after import.", config: "Connection JSON", applyImport: "Import and connect",
    hideSetup: "Hide PC setup", setup: "Prepare your PC", setupHelp: "1. Share and extract the PC archive on Windows.\n2. Run kiyori_pc_agent.bat and select LAN or FRP.\n3. Copy the connection configuration and import it here.",
    export: "Export and share PC agent", resourceMissing: "PC resources missing. Reinstall the toolkit.", exported: "PC agent sharing opened.",
    networkHelp: "LAN: bind to the PC LAN address and allow its firewall port.\nFRP: forward only the execution port (58321 by default), using HTTPS or a protected private tunnel. The console opens only on the PC.\n127.0.0.1 on a phone refers to the phone itself.",
    usage: "Tasks for AI", usageHelp: "For example: list D:\\Work, read the plan, make the requested edits, then move it to the specified directory and verify.\nPaths refer to the PC. Move and copy refuse existing destinations. If a request is interrupted, inspect the result before repeating it."
};
