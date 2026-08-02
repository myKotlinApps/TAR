const express = require("express");
const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");
const config = require("./config");
const { init } = require("./db");
const { startTcpServer } = require("./protocol");
const { startHttpBridge } = require("./bridge");
const routes = require("./routes/index");

init();

const app = express();
app.use(express.json({ limit: config.maxUploadSize }));
app.use((req, res, next) => {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("X-Frame-Options", "DENY");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("Cache-Control", "no-store");
    next();
});
app.use("/api", routes);
app.use(express.static(path.join(__dirname, "..", "panel")));

let server;
if (config.tlsCertPath && config.tlsKeyPath && fs.existsSync(config.tlsCertPath) && fs.existsSync(config.tlsKeyPath)) {
    server = https.createServer({
        cert: fs.readFileSync(config.tlsCertPath),
        key: fs.readFileSync(config.tlsKeyPath),
    }, app);
    console.log("[*] HTTPS enabled");
} else {
    server = http.createServer(app);
    console.log("[!] WARNING: running without TLS - set VEIL_TLS_CERT/VEIL_TLS_KEY for HTTPS");
}

server.listen(config.apiPort, () => {
    console.log("[*] REST API on http" + (config.tlsCertPath ? "s" : "") + "://0.0.0.0:" + config.apiPort);
});

startTcpServer();
startHttpBridge();

module.exports = { app, server };
