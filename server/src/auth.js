const crypto = require("crypto");
const config = require("./config");

const sessions = new Map();
const loginAttempts = new Map();

function createSession(actor) {
    const token = crypto.randomUUID();
    sessions.set(token, { actor, created: Date.now(), lastAccess: Date.now() });
    return token;
}

function validateSession(token) {
    if (!token) return null;
    const s = sessions.get(token);
    if (!s) return null;
    if (Date.now() - s.lastAccess > config.sessionTimeoutMs) {
        sessions.delete(token);
        return null;
    }
    s.lastAccess = Date.now();
    return s;
}

function parseCookie(header) {
    const out = {};
    if (!header) return out;
    for (const part of header.split(";")) {
        const i = part.indexOf("=");
        if (i > 0) {
            const k = part.slice(0, i).trim();
            let v = part.slice(i + 1).trim();
            try { v = decodeURIComponent(v); } catch {}
            out[k] = v;
        }
    }
    return out;
}

function getToken(req) {
    return req.headers["x-auth-token"] || parseCookie(req.headers.cookie || "").veil_session;
}

function rateLimiter() {
    return (req, res, next) => {
        const ip = req.ip;
        const a = loginAttempts.get(ip) || { count: 0, first: Date.now() };
        if (a.count >= config.loginRateMax && Date.now() - a.first < config.loginRateWindowMs) {
            return res.status(429).json({ error: "rate_limited" });
        }
        a.count++;
        if (a.count === 1) a.first = Date.now();
        loginAttempts.set(ip, a);
        next();
    };
}

function clearRateLimit(ip) {
    loginAttempts.delete(ip);
}

function requireAuth(req, res, next) {
    const session = validateSession(getToken(req));
    if (!session) return res.status(401).json({ error: "unauthorized" });
    req.session = session;
    next();
}

function logout(token) {
    sessions.delete(token);
}

setInterval(() => {
    const now = Date.now();
    for (const [k, v] of loginAttempts) {
        if (now - v.first > config.loginRateWindowMs * 2) loginAttempts.delete(k);
    }
    for (const [k, s] of sessions) {
        if (now - s.lastAccess > config.sessionTimeoutMs * 2) sessions.delete(k);
    }
}, 5 * 60 * 1000).unref();

module.exports = { createSession, validateSession, rateLimiter, clearRateLimit, requireAuth, logout };
