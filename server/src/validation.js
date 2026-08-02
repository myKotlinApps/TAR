function validateHost(v) {
    return typeof v === "string" && /^[0-9a-zA-Z.-]+$/.test(v) && v.length <= 255;
}
function validatePort(v) {
    const n = Number(v);
    return Number.isInteger(n) && n >= 1 && n <= 65535;
}
function validateCryptoKey(v) {
    return typeof v === "string" && /^[a-zA-Z0-9_]+$/.test(v) && v.length >= 8 && v.length <= 64;
}
function validateUid(v) {
    return typeof v === "string" && /^[a-zA-Z0-9_-]+$/.test(v) && v.length <= 128;
}
function validateClassName(v) {
    return typeof v === "string" && /^([a-zA-Z_][a-zA-Z0-9_]*)(\.[a-zA-Z_][a-zA-Z0-9_]*)+$/.test(v) && v.length <= 255;
}
function validateFilename(v) {
    if (typeof v !== "string" || v.length === 0 || v.length > 255) return false;
    return v.includes("..") === false && /[\\/]/.test(v) === false;
}
function safeFilename(name) {
    const basename = require("path").basename(name || "").replace(/[^a-zA-Z0-9._-]/g, "_");
    return basename.length > 0 ? basename.substring(0, 255) : "file";
}

module.exports = { validateHost, validatePort, validateCryptoKey, validateUid, validateClassName, validateFilename, safeFilename };
