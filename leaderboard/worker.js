// Flip Ledger crew leaderboard: Cloudflare Worker + D1
//
// Bindings (Worker > Settings > Bindings / Variables):
//   DB         D1 database
//   GROUP_KEY  secret: the one crew key everyone pastes into Flip Ledger
//
// Names are claimed automatically: the first submit for a RuneScape name creates a private
// token that only that player's plugin gets back and stores. Later submits for that name must
// include it, so nobody else can post as them. To free a name (e.g. a friend reinstalled),
// run in the D1 console:  DELETE FROM players WHERE canon = 'their name in lowercase';
//
// Endpoints:
//   POST /submit        Flip Ledger sends a player's current 1h / 12h / 24h results (needs X-Group-Key)
//   GET  /leaderboard   The website reads current and previous windows (public, read-only)

const WINDOWS = { h1: 1, h12: 12, h24: 24 }; // hours; all reset on Eastern time
const MAX_NAME = 20;
const MAX_ABS_PROFIT = 50_000_000_000; // sanity cap: 50B per window
const MIN_SECONDS_BETWEEN_SUBMITS = 30;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    try {
      await ensureTable(env);
      if (url.pathname === "/submit" && request.method === "POST") return await submit(request, env);
      if (url.pathname === "/leaderboard" && request.method === "GET") return cors(await leaderboard(env));
      return cors(json({ error: "not found" }, 404));
    } catch (e) {
      return cors(json({ error: "server error" }, 500));
    }
  },
};

let tableReady = false;
async function ensureTable(env) {
  if (tableReady) return;
  await env.DB.prepare(
    `CREATE TABLE IF NOT EXISTS players (
       canon TEXT PRIMARY KEY,
       name TEXT NOT NULL,
       token TEXT NOT NULL,
       created INTEGER NOT NULL
     )`
  ).run();
  await env.DB.prepare(
    `CREATE TABLE IF NOT EXISTS scores (
       name TEXT NOT NULL,
       win TEXT NOT NULL,
       start INTEGER NOT NULL,
       profit INTEGER NOT NULL,
       flips INTEGER NOT NULL,
       updated INTEGER NOT NULL,
       PRIMARY KEY (name, win, start)
     )`
  ).run();
  // Columns added later for "best single flip" and "biggest loss"; ignore the error if they already exist
  for (const col of ["best INTEGER", "best_item TEXT", "worst INTEGER", "worst_item TEXT"]) {
    try { await env.DB.prepare(`ALTER TABLE scores ADD COLUMN ${col}`).run(); } catch {}
  }
  tableReady = true;
}

// Start (unix seconds) of the window containing `nowSec`, using Eastern wall-clock time.
// Same method as the plugin: subtract the time elapsed since the last reset hour.
function windowStart(hours, nowSec) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", {
      timeZone: "America/New_York",
      hour: "numeric", minute: "numeric", second: "numeric", hourCycle: "h23",
    }).formatToParts(new Date(nowSec * 1000)).map(p => [p.type, p.value])
  );
  const h = Number(parts.hour) % 24, m = Number(parts.minute), s = Number(parts.second);
  const elapsed = (h % hours) * 3600 + m * 60 + s;
  return nowSec - elapsed;
}

function sameKey(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

const canon = n => String(n).replace(/[\u00a0_\-]/g, " ").replace(/\s+/g, " ").trim().toLowerCase();

async function submit(request, env) {
  let body;
  try { body = await request.json(); } catch { return json({ error: "bad json" }, 400); }

  const name = String(body.name || "").replace(/\u00a0/g, " ").trim().slice(0, MAX_NAME);
  if (!name || !/^[\w \-]+$/.test(name)) return json({ error: "bad name" }, 400);

  if (!env.GROUP_KEY || !sameKey(request.headers.get("X-Group-Key") || "", env.GROUP_KEY)) {
    return json({ error: "bad key" }, 403);
  }

  // Claim the name on first use; afterwards require that player's token
  const c = canon(name);
  const owner = await env.DB.prepare("SELECT token FROM players WHERE canon = ?").bind(c).first();
  let newToken = null;
  if (!owner) {
    newToken = crypto.randomUUID() + crypto.randomUUID().slice(0, 8);
    await env.DB.prepare("INSERT INTO players (canon, name, token, created) VALUES (?, ?, ?, ?)")
      .bind(c, name, newToken, Math.floor(Date.now() / 1000)).run();
  } else if (!sameKey(request.headers.get("X-Player-Token") || "", owner.token)) {
    return json({ error: "name claimed" }, 403);
  }

  const now = Math.floor(Date.now() / 1000);
  const last = await env.DB.prepare("SELECT MAX(updated) AS u FROM scores WHERE name = ?").bind(name).first();
  if (last && last.u && now - last.u < MIN_SECONDS_BETWEEN_SUBMITS) {
    return json({ ok: true, throttled: true, token: newToken || undefined });
  }

  const stmts = [];
  for (const [win, hours] of Object.entries(WINDOWS)) {
    const w = body.windows && body.windows[win];
    if (!w) continue;
    const start = windowStart(hours, now);
    // Only accept the window that is current right now (stops edits to old windows)
    if (Math.abs(Number(w.start) - start) > 120) continue;
    const profit = Math.trunc(Number(w.profit));
    const flips = Math.trunc(Number(w.flips));
    if (!Number.isFinite(profit) || Math.abs(profit) > MAX_ABS_PROFIT) continue;
    if (!Number.isFinite(flips) || flips < 0 || flips > 100000) continue;
    let best = w.best == null ? null : Math.trunc(Number(w.best));
    if (best != null && (!Number.isFinite(best) || Math.abs(best) > MAX_ABS_PROFIT)) best = null;
    const bestItem = best == null ? null : String(w.bestItem || "").replace(/[^\w '()+\-.,]/g, "").slice(0, 40) || null;
    let worst = w.worst == null ? null : Math.trunc(Number(w.worst));
    if (worst != null && (!Number.isFinite(worst) || worst >= 0 || Math.abs(worst) > MAX_ABS_PROFIT)) worst = null;
    const worstItem = worst == null ? null : String(w.worstItem || "").replace(/[^\w '()+\-.,]/g, "").slice(0, 40) || null;
    stmts.push(env.DB.prepare(
      `INSERT INTO scores (name, win, start, profit, flips, updated, best, best_item, worst, worst_item) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(name, win, start) DO UPDATE SET profit = excluded.profit, flips = excluded.flips, updated = excluded.updated,
         best = excluded.best, best_item = excluded.best_item, worst = excluded.worst, worst_item = excluded.worst_item`
    ).bind(name, win, start, profit, flips, now, best, bestItem, worst, worstItem));
  }
  if (stmts.length) await env.DB.batch(stmts);

  // Keep the table small: drop anything older than 3 days
  await env.DB.prepare("DELETE FROM scores WHERE start < ?").bind(now - 3 * 86400).run();
  return json({ ok: true, saved: stmts.length, token: newToken || undefined });
}

async function leaderboard(env) {
  const now = Math.floor(Date.now() / 1000);
  const out = { now, windows: {} };
  for (const [win, hours] of Object.entries(WINDOWS)) {
    const start = windowStart(hours, now);
    const prevStart = windowStart(hours, start - 1);
    const rows = async s => (await env.DB.prepare(
      "SELECT name, profit, flips, updated, best, best_item AS bestItem, worst, worst_item AS worstItem FROM scores WHERE win = ? AND start = ? ORDER BY profit DESC LIMIT 50"
    ).bind(win, s).all()).results;
    out.windows[win] = {
      start, end: start + hours * 3600,
      current: await rows(start),
      previousStart: prevStart,
      previous: await rows(prevStart),
    };
  }
  const res = json(out);
  res.headers.set("Cache-Control", "public, max-age=30");
  return res;
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json" } });
}

function cors(res) {
  res.headers.set("Access-Control-Allow-Origin", "*");
  res.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.headers.set("Access-Control-Allow-Headers", "Content-Type, X-Group-Key, X-Player-Token");
  return res;
}
