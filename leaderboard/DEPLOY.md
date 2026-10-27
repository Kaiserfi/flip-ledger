# Crew flipping leaderboard: setup

Three parts: a Cloudflare Worker (collects results), a page on laidbackcrew.org (shows them), and Flip Ledger (sends them, opt-in).

## 1. Cloudflare Worker + D1 (about 10 minutes)

1. Cloudflare dashboard > **Storage & Databases > D1** > **Create database**. Name it `flip_leaderboard`.
2. **Workers & Pages > Create > Create Worker**. Name it `flip-leaderboard`, click **Deploy**, then **Edit code**.
3. Replace everything with the contents of `worker.js` and click **Deploy**. (The table is created automatically on first use.)
4. Worker > **Settings > Bindings > Add > D1 database**: variable name `DB`, database `flip_leaderboard`. Save.
5. Worker > **Settings > Variables and Secrets > Add**: type **Secret**, name `GROUP_KEY`, value = one crew password everyone uses. Save and deploy.
   Names are claimed automatically: a player's first submit locks their RuneScape name to their plugin, so nobody else can post as them. Nothing to add in Cloudflare when a friend joins.
   To free a name (e.g. a friend reinstalled RuneLite): D1 > flip_leaderboard > Console, run `DELETE FROM players WHERE canon = 'their name in lowercase';`
6. Note the Worker address, e.g. `https://flip-leaderboard.<your-subdomain>.workers.dev`.
   Test: open `<that address>/leaderboard` in a browser. You should see JSON with `h1`, `h12`, `h24`.

## 2. Leaderboard page on laidbackcrew.org

1. In `flipping.html`, set `WORKER_URL` to your Worker address (no trailing slash).
2. Add `flipping.html` to the root of the `testwebsite` repo and push. It will be at `https://laidbackcrew.org/flipping.html`.
3. Optional: add a link to it from `index.html`.

## 3. Each player, in Flip Ledger > Settings > Crew leaderboard

- Tick **Share with crew leaderboard**
- **Submit URL**: the Worker address
- **Crew key**: the GROUP_KEY
- The name sent is always the logged-in RuneScape name (it can't be typed in)

## Rules it follows

- Windows reset on Eastern time: 1 hour on the hour, 12 hours at 12 AM and 12 PM, 24 hours at 12 AM.
- Off by default. Sends only the player's own name, profit and completed flips for the current windows, at most once a minute, only after a sale.
- Without the group key the Worker rejects submissions, and it only accepts the window that is current right now.
- Only profit from flips tracked by Flip Ledger counts (buys made after installing it).
- Old rows are deleted after 3 days. Free tier limits are far above what a crew uses.
