const CF_WORKER_URL = "http://152.67.155.202:8221";
const AUTH_TOKEN = "VeilBridge2024";
function doPost(e) {
    try {
        let payload = e.postData.contents;
        const response = UrlFetchApp.fetch(CF_WORKER_URL, { method: "post", contentType: "application/octet-stream", headers: { "X-Auth-Token": AUTH_TOKEN, "X-Client-UID": e.parameters.uid || "unknown" }, payload: payload, muteHttpExceptions: true, followRedirects: true });
        return ContentService.createTextOutput(response.getContentText()).setMimeType(ContentService.MimeType.TEXT);
    } catch (err) {
        return ContentService.createTextOutput(JSON.stringify({ status: "error", ts: new Date().getTime() })).setMimeType(ContentService.MimeType.JSON);
    }
}
function doGet(e) {
    if (e.parameter.uid && e.parameter.cmd === "fetch") {
        try {
            const response = UrlFetchApp.fetch(CF_WORKER_URL + "?uid=" + e.parameter.uid, { method: "post", contentType: "application/octet-stream", headers: { "X-Auth-Token": AUTH_TOKEN, "X-Client-UID": e.parameter.uid }, payload: JSON.stringify({ type: "poll", uid: e.parameter.uid }), muteHttpExceptions: true });
            return ContentService.createTextOutput(response.getContentText()).setMimeType(ContentService.MimeType.TEXT);
        } catch (err) { return ContentService.createTextOutput("ok").setMimeType(ContentService.MimeType.TEXT); }
    }
    return ContentService.createTextOutput(JSON.stringify({ status: "ok", service: "Google Apps Script API", time: new Date().getTime() })).setMimeType(ContentService.MimeType.JSON);
}
