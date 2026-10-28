package com.flipledger;

import com.google.gson.Gson;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Opt-in: sends only this player's own 1h / 12h / 24h profit and flip count to the crew's
 * leaderboard Worker. Off by default; nothing is sent without a URL and group key.
 */
class CrewLeaderboard
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CrewLeaderboard.class);
	static final ZoneId EASTERN = ZoneId.of("America/New_York");
	static final int[] WINDOW_HOURS = {1, 12, 24};
	private static final String[] WINDOW_KEYS = {"h1", "h12", "h24"};
	private static final long MIN_GAP_MS = 60_000;
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;
	private volatile long lastSent;
	private volatile boolean pending;
	volatile String lastStatus = "Not sent yet";

	CrewLeaderboard(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** Start (unix seconds) of the Eastern-time window containing nowSec. Same rule as the Worker. */
	static long windowStart(int hours, long nowSec)
	{
		ZonedDateTime z = Instant.ofEpochSecond(nowSec).atZone(EASTERN);
		long elapsed = (z.getHour() % hours) * 3600L + z.getMinute() * 60L + z.getSecond();
		return nowSec - elapsed;
	}

	void markDirty()
	{
		pending = true;
	}

	/** Call periodically and after sales. Sends at most once a minute, only when something changed. */
	void maybeSend(FlipLedgerConfig cfg, String playerName, List<LedgerData.Event> events, String token, Consumer<String> saveToken)
	{
		if (!pending || !cfg.crewEnabled() || events == null)
		{
			return;
		}
		String url = cfg.crewUrl().trim();
		String key = cfg.crewKey().trim();
		// Always the logged-in RuneScape name; it can't be typed in
		String name = playerName == null ? null : playerName.replace('\u00A0', ' ').trim();
		if (url.isEmpty() || key.isEmpty() || name == null || name.isEmpty())
		{
			lastStatus = "Needs a submit URL and crew key (and to be logged in)";
			return;
		}
		if (System.currentTimeMillis() - lastSent < MIN_GAP_MS)
		{
			return;
		}
		HttpUrl base = HttpUrl.parse(url);
		if (base == null || !"https".equals(base.scheme()))
		{
			lastStatus = "Submit URL must start with https://";
			return;
		}
		HttpUrl submit = base.newBuilder().encodedPath("/submit").build();

		long now = System.currentTimeMillis() / 1000;
		Map<String, Object> windows = new LinkedHashMap<>();
		for (int i = 0; i < WINDOW_HOURS.length; i++)
		{
			long start = windowStart(WINDOW_HOURS[i], now);
			long profit = 0;
			int flips = 0;
			LedgerData.Event best = null;
			LedgerData.Event worst = null; // biggest losing flip, if any
			for (LedgerData.Event e : events)
			{
				if (e.t >= start)
				{
					profit += e.profit;
					flips += e.flips;
					if (e.flips > 0 && e.item != null && (best == null || e.flipTotal > best.flipTotal))
					{
						best = e;
					}
					if (e.flips > 0 && e.item != null && e.flipTotal < 0 && (worst == null || e.flipTotal < worst.flipTotal))
					{
						worst = e;
					}
				}
			}
			Map<String, Object> w = new LinkedHashMap<>();
			w.put("start", start);
			w.put("profit", profit);
			w.put("flips", flips);
			if (best != null)
			{
				w.put("best", best.flipTotal);
				w.put("bestItem", best.item);
			}
			if (worst != null)
			{
				w.put("worst", worst.flipTotal);
				w.put("worstItem", worst.item);
			}
			windows.put(WINDOW_KEYS[i], w);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("windows", windows);

		pending = false;
		lastSent = System.currentTimeMillis();
		Request.Builder rb = new Request.Builder().url(submit).header("X-Group-Key", key);
		if (token != null && !token.isEmpty())
		{
			rb.header("X-Player-Token", token);
		}
		Request req = rb
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				pending = true;
				lastStatus = "Couldn't reach the leaderboard";
				log.debug("Flip Ledger: leaderboard submit failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response r = response)
				{
					// body read below
					String text = r.body() != null ? r.body().string() : "";
					if (r.code() == 403)
					{
						lastStatus = text.contains("claimed")
							? "Your name is claimed on another device; ask your crew leader to free it"
							: "Crew key was rejected";
					}
					else if (!r.isSuccessful())
					{
						pending = true;
						lastStatus = "Leaderboard error " + r.code();
					}
					else
					{
						com.google.gson.JsonObject o = gson.fromJson(text, com.google.gson.JsonObject.class);
						if (o != null && o.has("token") && !o.get("token").isJsonNull())
						{
							saveToken.accept(o.get("token").getAsString());
						}
						lastStatus = "Sent " + java.time.LocalTime.now().withNano(0);
					}
				}
			}
		});
	}
}
