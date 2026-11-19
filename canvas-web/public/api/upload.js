const crypto = require("crypto");
const { guard } = require("./_guard");

const PROJECT_ID = "zenith-canvas-17133";
const SUPABASE_URL = "https://ljckgisqhlypvszlckvf.supabase.co";
const FS = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents";
const DAILY_LIMIT = 5;
const MAX_BYTES = 3 * 1024 * 1024;
const tokenCache = { value: "", exp: 0 };

function b64u(obj) {
  return Buffer.from(JSON.stringify(obj)).toString("base64url");
}

function norm(text) {
  return String(text || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s]/g, " ").replace(/\s+/g, " ").trim();
}

async function googleToken() {
  const now = Math.floor(Date.now() / 1000);
  if (tokenCache.value && tokenCache.exp - 60 > now) return tokenCache.value;
  const sa = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  const head = b64u({ alg: "RS256", typ: "JWT" });
  const claim = b64u({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  });
  const sig = crypto.createSign("RSA-SHA256").update(head + "." + claim).sign(sa.private_key).toString("base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: head + "." + claim + "." + sig
    }),
    signal: AbortSignal.timeout(8000)
  });
  const json = await res.json();
  if (!res.ok || !json.access_token) throw new Error("google token " + res.status);
  tokenCache.value = json.access_token;
  tokenCache.exp = now + (json.expires_in || 3600);
  return tokenCache.value;
}

function readSession(req) {
  const header = String(req.headers.authorization || "");
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const parts = token.split(".");
  if (parts.length !== 2) return null;
  const want = crypto.createHmac("sha256", process.env.SESSION_SECRET).update(parts[0]).digest("base64url");
  const a = Buffer.from(parts[1]);
  const b = Buffer.from(want);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[0], "base64url").toString());
    return payload.uid && payload.exp > Date.now() / 1000 ? payload : null;
  } catch (error) {
    return null;
  }
}

async function isMember(userId) {
  const url = "https://api.telegram.org/bot" + process.env.TELEGRAM_BOT_TOKEN + "/getChatMember?chat_id=" +
    encodeURIComponent(process.env.TELEGRAM_CHAT_ID) + "&user_id=" + encodeURIComponent(userId);
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  const json = await res.json();
  if (!json.ok) throw new Error("telegram check failed");
  const status = json.result.status;
  return status === "creator" || status === "administrator" || status === "member" ||
    (status === "restricted" && json.result.is_member === true);
}

async function recentUploads(uid) {
  const token = await googleToken();
  const res = await fetch(FS + ":runQuery", {
    method: "POST",
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "canvases" }],
        where: { fieldFilter: { field: { fieldPath: "uid" }, op: "EQUAL", value: { stringValue: uid } } },
        limit: 200
      }
    }),
    signal: AbortSignal.timeout(8000)
  });
  if (!res.ok) throw new Error("firestore query " + res.status);
  const rows = await res.json();
  const since = Date.now() - 24 * 60 * 60 * 1000;
  return rows.filter((row) => {
    const field = row.document && row.document.fields && row.document.fields.createdAt;
    return field && Date.parse(field.timestampValue) >= since;
  }).length;
}

function esc(text) {
  return String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

async function notifyGroup(docId, item) {
  try {
    const base = "https://api.telegram.org/bot" + process.env.TELEGRAM_BOT_TOKEN;
    const text = "\uD83C\uDFAC <b>Canvas baru menunggu persetujuan</b>\n\n" +
      "<b>Judul:</b> " + esc(item.song) + "\n" +
      "<b>Artis:</b> " + esc(item.artist) + "\n" +
      "<b>Album:</b> " + esc(item.album || "-") + "\n" +
      "<b>Pengunggah:</b> " + esc(item.uname || "-") + "\n" +
      "<b>Durasi:</b> " + item.duration + " detik\n\n" +
      '<a href="' + esc(item.url) + '">Buka video</a>';
    const common = {
      chat_id: process.env.TELEGRAM_CHAT_ID,
      parse_mode: "HTML",
      reply_markup: {
        inline_keyboard: [[
          { text: "\u2705 Setujui", callback_data: "ok:" + docId },
          { text: "\u274C Tolak", callback_data: "no:" + docId }
        ]]
      }
    };
    if (process.env.TELEGRAM_THREAD_ID) common.message_thread_id = Number(process.env.TELEGRAM_THREAD_ID);
    const send = async (method, extra) => {
      const r = await fetch(base + "/" + method, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(Object.assign({}, common, extra)),
        signal: AbortSignal.timeout(8000)
      });
      return r.json();
    };
    const sent = await send("sendVideo", { video: item.url, caption: text, supports_streaming: true });
    if (!sent.ok) await send("sendMessage", { text, disable_web_page_preview: false });
  } catch (error) {}
}

module.exports = async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  if (!guard(req, res, "upload", 20, 60 * 1000)) return;
  if (req.method !== "POST") {
    res.status(405).json({ error: "method_not_allowed" });
    return;
  }
  const needed = ["SESSION_SECRET", "TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID", "FIREBASE_SERVICE_ACCOUNT", "SUPABASE_SECRET_KEY"];
  if (needed.some((key) => !process.env[key])) {
    res.status(500).json({ error: "not_configured" });
    return;
  }
  const session = readSession(req);
  if (!session) {
    res.status(401).json({ error: "login_required" });
    return;
  }
  const body = req.body && typeof req.body === "object" ? req.body : {};
  try {
    if (!(await isMember(session.uid))) {
      res.status(403).json({ error: "not_member" });
      return;
    }
    const used = await recentUploads(session.uid);
    if (used >= DAILY_LIMIT) {
      res.status(429).json({ error: "limit_reached", limit: DAILY_LIMIT });
      return;
    }

    if (body.action === "ticket") {
      const name = session.uid + "-" + crypto.randomUUID() + ".mp4";
      const r = await fetch(SUPABASE_URL + "/storage/v1/object/upload/sign/canvas/" + name, {
        method: "POST",
        headers: { apikey: process.env.SUPABASE_SECRET_KEY, "Content-Type": "application/json" },
        body: "{}",
        signal: AbortSignal.timeout(8000)
      });
      if (!r.ok) throw new Error("supabase sign " + r.status);
      const data = await r.json();
      res.status(200).json({ name, uploadUrl: SUPABASE_URL + "/storage/v1" + data.url, remaining: DAILY_LIMIT - used });
      return;
    }

    if (body.action === "submit") {
      const name = String(body.name || "");
      if (!new RegExp("^" + session.uid + "-[0-9a-f-]{36}\\.mp4$").test(name)) {
        res.status(400).json({ error: "bad_file" });
        return;
      }
      const song = String(body.song || "").trim();
      const artist = String(body.artist || "").trim();
      const album = String(body.album || "").trim();
      if (!song || song.length >= 120 || !artist || artist.length >= 120 || album.length >= 120) {
        res.status(400).json({ error: "bad_text" });
        return;
      }
      const duration = Number(body.duration);
      const width = Number(body.width);
      const height = Number(body.height);
      if (!(duration >= 3 && duration <= 8) || width !== 1280 || height !== 1280) {
        res.status(400).json({ error: "spec_invalid" });
        return;
      }
      const url = SUPABASE_URL + "/storage/v1/object/public/canvas/" + name;
      const head = await fetch(url, { method: "HEAD", signal: AbortSignal.timeout(8000) });
      const size = Number(head.headers.get("content-length") || 0);
      const type = head.headers.get("content-type") || "";
      if (!head.ok || !size || size > MAX_BYTES || !type.startsWith("video/mp4")) {
        res.status(400).json({ error: "file_invalid" });
        return;
      }
      const token = await googleToken();
      const created = await fetch(FS + "/canvases", {
        method: "POST",
        headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
        body: JSON.stringify({
          fields: {
            song: { stringValue: song },
            artist: { stringValue: artist },
            album: { stringValue: album },
            key: { stringValue: norm(song) + "|" + norm(artist) },
            url: { stringValue: url },
            status: { stringValue: "pending" },
            likes: { integerValue: "0" },
            createdAt: { timestampValue: new Date().toISOString() },
            size: { integerValue: String(size) },
            duration: { doubleValue: duration },
            width: { integerValue: String(width) },
            height: { integerValue: String(height) },
            specVersion: { integerValue: "1" },
            uid: { stringValue: session.uid },
            uname: { stringValue: String(session.name || "") }
          }
        }),
        signal: AbortSignal.timeout(8000)
      });
      if (!created.ok) throw new Error("firestore create " + created.status);
      const createdDoc = await created.json();
      const docId = String(createdDoc.name || "").split("/").pop();
      if (docId) await notifyGroup(docId, { song, artist, album, url, duration, uname: session.name });
      res.status(200).json({ ok: true });
      return;
    }

    res.status(400).json({ error: "bad_action" });
  } catch (error) {
    res.status(502).json({ error: "server_error", detail: String(error.message || error).slice(0, 120) });
  }
};