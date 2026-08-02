const sqlite3 = require("sqlite3").verbose();
const path = require("path");
const fs = require("fs");

const DB_PATH = path.join(__dirname, "..", "rat.db");
const db = new sqlite3.Database(DB_PATH);

function init() {
    db.serialize(() => {
        db.run("CREATE TABLE IF NOT EXISTS clients (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT UNIQUE, device_model TEXT, android_version TEXT, manufacturer TEXT, ip TEXT, country TEXT, first_seen INTEGER, last_seen INTEGER, online INTEGER DEFAULT 0, tag TEXT)");
        db.run("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT, client_uid TEXT, cmd TEXT, result TEXT, timestamp INTEGER)");
        db.run("CREATE TABLE IF NOT EXISTS pending_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, client_uid TEXT, cmd TEXT, data TEXT, created_at INTEGER, delivered INTEGER DEFAULT 0)");
        db.run("CREATE TABLE IF NOT EXISTS files (id INTEGER PRIMARY KEY AUTOINCREMENT, client_uid TEXT, filename TEXT, filepath TEXT, filesize INTEGER, file_type TEXT, timestamp INTEGER)");
        db.run("CREATE TABLE IF NOT EXISTS plugins (id TEXT PRIMARY KEY, name TEXT, filepath TEXT, className TEXT, size INTEGER, checksum TEXT, created_at INTEGER)");
        db.run("CREATE TABLE IF NOT EXISTS builds (buildId TEXT PRIMARY KEY, host TEXT, port INTEGER, cryptoKey TEXT, status TEXT, size INTEGER, downloadUrl TEXT, created_at INTEGER)");
        db.run("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");
        db.run("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT NOT NULL, actor TEXT, target TEXT, ip TEXT, details TEXT, success INTEGER, timestamp INTEGER NOT NULL)");
    });
    fs.mkdirSync(path.join(__dirname, "..", "uploads"), { recursive: true });
    fs.mkdirSync(path.join(__dirname, "..", "plugins"), { recursive: true });
    fs.mkdirSync(path.join(__dirname, "..", "builds"), { recursive: true });
}

function dbAll(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.all(sql, params, (err, rows) => {
            if (err) reject(err);
            else resolve(rows || []);
        });
    });
}

function dbRun(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.run(sql, params, function(err) {
            if (err) reject(err);
            else resolve(this.lastID);
        });
    });
}

function dbGet(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.get(sql, params, (err, row) => {
            if (err) reject(err);
            else resolve(row);
        });
    });
}

function auditLog(action, actor, target, ip, details = {}, success = true) {
    return db.run("INSERT INTO audit_log (action, actor, target, ip, details, success, timestamp) VALUES (?,?,?,?,?,?,?)",
        [action, actor, target, ip, JSON.stringify(details), success ? 1 : 0, Date.now()]);
}

module.exports = { db, init, dbAll, dbRun, dbGet, auditLog };
