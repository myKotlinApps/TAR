const ORIGIN_SERVER = "http://152.67.155.202:8220";
const AUTH_TOKEN = "VeilBridge2024";
const corsHeaders = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "POST, GET, OPTIONS", "Access-Control-Allow-Headers": "Content-Type, X-Auth-Token, X-Client-UID" };
export default {
    async fetch(request, env) {
        if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
        if (request.method !== "POST") return new Response("Not Found", { status: 404 });
        if (request.headers.get("X-Auth-Token") !== AUTH_TOKEN) return new Response("Unauthorized", { status: 401, headers: corsHeaders });
        try {
            const body = await request.text();
            const originResponse = await fetch(ORIGIN_SERVER, { method: "POST", headers: { "Content-Type": "application/octet-stream", "X-Auth-Token": AUTH_TOKEN, "X-Forwarded-For": request.headers.get("CF-Connecting-IP") || "unknown" }, body: body });
            const responseData = await originResponse.text();
            return new Response(responseData, { status: 200, headers: { ...corsHeaders, "Content-Type": "application/octet-stream" } });
        } catch (e) { return new Response("Error", { status: 502, headers: corsHeaders }); }
    }
};
