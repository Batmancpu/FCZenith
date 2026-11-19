const PROJECT_ID = "zenith-canvas-17133";
const API_KEY = "AIzaSyCO8txNS4UPwjrRKplPenuL-RA557gGhco";
const OLD_MANIFEST = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json";

function norm(text) {
  return String(text || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s]/g, " ").replace(/\s+/g, " ").trim();
}

function str(field) {
  return field && field.stringValue !== undefined ? field.stringValue : "";
}

function num(field) {
  return field && field.integerValue !== undefined ? Number(field.integerValue) : 0;
}

async function loadOwn() {
  const url = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID +
    "/databases/(default)/documents:runQuery?key=" + API_KEY;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "canvases" }],
        where: {
          fieldFilter: {
            field: { fieldPath: "status" },
            op: "EQUAL",
            value: { stringValue: "approved" }
          }
        },
        limit: 1000
      }
    })
  });
  if (!res.ok) throw new Error("Firestore " + res.status);
  const rows = await res.json();
  const best = new Map();
  for (const row of rows) {
    if (!row.document) continue;
    const f = row.document.fields || {};
    const item = {
      song: str(f.song),
      artist: str(f.artist),
      album: str(f.album),
      url: str(f.url),
      likes: num(f.likes)
    };
    if (!item.song || !item.artist || !item.url) continue;
    const id = norm(item.song) + "|" + norm(item.artist) + "|" + norm(item.album);
    const current = best.get(id);
    if (!current || item.likes > current.likes) best.set(id, item);
  }
  return [...best.values()]
    .sort((a, b) => b.likes - a.likes)
    .map(({ song, artist, album, url }) => ({ song, artist, album, url }));
}

async function loadOld() {
  try {
    const res = await fetch(OLD_MANIFEST, { signal: AbortSignal.timeout(5000) });
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data.items) ? data.items : [];
  } catch (error) {
    return [];
  }
}

module.exports = async (req, res) => {
  try {
    const own = await loadOwn();
    const old = await loadOld();
    const seen = new Set(own.map((item) => item.url));
    const items = own.concat(old.filter((item) => item && item.url && !seen.has(item.url)));
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.setHeader("Cache-Control", "public, s-maxage=3600, stale-while-revalidate=600");
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.status(200).send(JSON.stringify({ items }));
  } catch (error) {
    res.setHeader("Cache-Control", "no-store");
    res.status(502).json({ error: String(error.message || error) });
  }
};