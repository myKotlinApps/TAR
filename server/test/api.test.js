const request = require("supertest");
const assert = require("assert");
process.env.VEIL_PANEL_PASSWORD = "test_password";
process.env.VEIL_ENROLL_KEY = "test_enroll";
process.env.VEIL_AUTH_TOKEN = "test_token";
const { app, server } = require("../src/index");

(async () => {
    const loginOk = await request(app).post("/api/login").send({ password: "test_password" });
    assert.strictEqual(loginOk.status, 200, "login with valid password should return 200");
    assert.ok(loginOk.body.token, "login should return a token");
    const token = loginOk.body.token;
    console.log("[+] login with valid password: OK");

    const loginBad = await request(app).post("/api/login").send({ password: "wrong" });
    assert.strictEqual(loginBad.status, 401, "login with wrong password should return 401");
    console.log("[+] login with wrong password: OK");

    const noAuth = await request(app).get("/api/clients");
    assert.strictEqual(noAuth.status, 401, "clients without auth should return 401");
    console.log("[+] list requires auth: OK");

    const withAuth = await request(app).get("/api/clients").set("x-auth-token", token);
    assert.strictEqual(withAuth.status, 200, "clients with auth should return 200");
    assert.ok(Array.isArray(withAuth.body), "clients response should be an array");
    console.log("[+] list with auth: OK");

    const invalidUid = await request(app).post("/api/cmd/invalid..uid%2F").set("x-auth-token", token).send({ cmd: "ls" });
    assert.strictEqual(invalidUid.status, 400, "invalid uid should return 400");
    console.log("[+] uid validation: OK");

    const invalidCmd = await request(app).post("/api/cmd/abc").set("x-auth-token", token).send({ cmd: "bad cmd!" });
    assert.strictEqual(invalidCmd.status, 400, "invalid cmd should return 400");
    console.log("[+] cmd validation: OK");

    const queued = await request(app).post("/api/cmd/queued_test_1").set("x-auth-token", token).send({ cmd: "ls" });
    assert.strictEqual(queued.status, 200, "cmd to offline client should queue");
    assert.strictEqual(queued.body.queued, true, "cmd should be queued for offline client");
    console.log("[+] cmd queuing: OK");

    const logout = await request(app).post("/api/logout").set("x-auth-token", token);
    assert.strictEqual(logout.status, 200, "logout should return 200");
    console.log("[+] logout: OK");

    server.close();
    process.exit(0);
})().catch((e) => {
    console.error("[-] FAILED:", e.message);
    server.close();
    process.exit(1);
});
