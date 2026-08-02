require("dotenv").config();

function throwIfMissing(key) {
    throw new Error(`Missing required env var: ${key}`);
}

function envInt(name, fallback) {
    const raw = process.env[name];
    const n = parseInt(raw, 10);
    return Number.isInteger(n) && n > 0 ? n : fallback;
}

module.exports = {
    apiPort: envInt("VEIL_API_PORT", 3000),
    socketPort: envInt("VEIL_SOCKET_PORT", 4444),
    httpBridgePort: envInt("VEIL_HTTP_PORT", 8080),
    panelPassword: process.env.VEIL_PANEL_PASSWORD || throwIfMissing("VEIL_PANEL_PASSWORD"),
    enrollKey: process.env.VEIL_ENROLL_KEY || throwIfMissing("VEIL_ENROLL_KEY"),
    authToken: process.env.VEIL_AUTH_TOKEN || throwIfMissing("VEIL_AUTH_TOKEN"),
    tlsKeyPath: process.env.VEIL_TLS_KEY || "",
    tlsCertPath: process.env.VEIL_TLS_CERT || "",
    sessionTimeoutMs: 30 * 60 * 1000,
    loginRateMax: 5,
    loginRateWindowMs: 60 * 1000,
    maxPluginSize: 5 * 1024 * 1024,
    maxUploadSize: 50 * 1024 * 1024,
    maxFrameSize: envInt("VEIL_MAX_FRAME", 64 * 1024 * 1024),
    maxConnections: envInt("VEIL_MAX_CONNECTIONS", 1000),
    handshakeTimeoutMs: 15000,
};
