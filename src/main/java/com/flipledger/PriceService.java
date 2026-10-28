package com.flipledger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads public prices from the OSRS Wiki real-time price API, kept deliberately light:
 * bulk files only (one request covers every item), long refresh intervals, and per-item
 * history only for a handful of top candidates, cached for hours.
 */
class PriceService
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PriceService.class);
	private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
	private static final String USER_AGENT = "Flip Ledger RuneLite plugin - flip suggestions";

	static final long HOUR_INTERVAL = 10 * 60_000L;      // /1h and /5m
	static final long DAY_INTERVAL = 30 * 60_000L;       // /24h
	static final long MAPPING_INTERVAL = 24 * 3600_000L; // /mapping
	static final long TREND_TTL = 2 * 3600_000L;         // per-item 6h history

	static class Item
	{
		int id;
		String name;
		boolean members;
		int limit;
		int highAlch;
	}

	static class Window
	{
		long high;   // average price buyers paid (you sell here)
		long low;    // average price sellers got (you buy here)
		long highVol;
		long lowVol;
	}

	private final OkHttpClient http;
	private final Gson gson;

	volatile Map<Integer, Item> mapping = Collections.emptyMap();
	volatile Map<Integer, Window> hour = Collections.emptyMap();
	volatile Map<Integer, Window> fiveMin = Collections.emptyMap();
	volatile Map<Integer, Window> day = Collections.emptyMap();
	volatile long hourAt;
	volatile long dayAt;
	private volatile long mappingAt;
	private final Map<Integer, Trend> trends = new ConcurrentHashMap<>();
	private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

	static class Trend
	{
		final long at;
		final List<Long> mids;

		Trend(long at, List<Long> mids)
		{
			this.at = at;
			this.mids = mids;
		}
	}

	PriceService(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** Fetch whatever is due. onChange runs (on an OkHttp thread) after each file arrives. */
	void refreshDue(Runnable onChange, boolean force)
	{
		long now = System.currentTimeMillis();
		if (force || now - mappingAt > MAPPING_INTERVAL || mapping.isEmpty())
		{
			get("mapping", body -> {
				Map<Integer, Item> m = new HashMap<>();
				for (JsonElement e : gson.fromJson(body, JsonArray.class))
				{
					JsonObject o = e.getAsJsonObject();
					Item it = new Item();
					it.id = o.get("id").getAsInt();
					it.name = o.get("name").getAsString();
					it.members = o.has("members") && o.get("members").getAsBoolean();
					it.limit = o.has("limit") && !o.get("limit").isJsonNull() ? o.get("limit").getAsInt() : 0;
					it.highAlch = o.has("highalch") && !o.get("highalch").isJsonNull() ? o.get("highalch").getAsInt() : 0;
					m.put(it.id, it);
				}
				mapping = m;
				mappingAt = System.currentTimeMillis();
				onChange.run();
			});
		}
		if (force || now - hourAt > HOUR_INTERVAL)
		{
			get("1h", body -> {
				hour = windows(body);
				hourAt = System.currentTimeMillis();
				onChange.run();
			});
			get("5m", body -> {
				fiveMin = windows(body);
				onChange.run();
			});
		}
		if (force || now - dayAt > DAY_INTERVAL)
		{
			get("24h", body -> {
				day = windows(body);
				dayAt = System.currentTimeMillis();
				onChange.run();
			});
		}
	}

	/** Cached week history (28 x 6h mid prices) or null; requests it if missing/stale. */
	Trend trend(int id, Runnable onChange)
	{
		Trend t = trends.get(id);
		if (t != null && System.currentTimeMillis() - t.at < TREND_TTL)
		{
			return t;
		}
		get("timeseries?timestep=6h&id=" + id, body -> {
			JsonArray arr = gson.fromJson(body, JsonObject.class).getAsJsonArray("data");
			List<Long> mids = new ArrayList<>();
			int start = Math.max(0, arr.size() - 28);
			for (int i = start; i < arr.size(); i++)
			{
				JsonObject o = arr.get(i).getAsJsonObject();
				long hi = num(o, "avgHighPrice");
				long lo = num(o, "avgLowPrice");
				if (hi > 0 && lo > 0)
				{
					mids.add((hi + lo) / 2);
				}
			}
			trends.put(id, new Trend(System.currentTimeMillis(), mids));
			onChange.run();
		});
		return t; // stale copy (or null) until the new one arrives
	}

	static final long RECENT_TTL = 60_000L;

	/** Most recent single trades: the fastest signal that a price is moving. */
	static class Latest
	{
		long high;     // last price a buyer paid
		long highTime; // unix seconds
		long low;      // last price a seller got
		long lowTime;
	}

	private final Map<Integer, Latest> latest = new ConcurrentHashMap<>();
	private final Map<Integer, Long> latestAt = new ConcurrentHashMap<>();

	Latest latestFor(int id)
	{
		return latest.get(id);
	}

	/** Refresh latest trades: one tiny request for a single item, one bulk request for several. */
	void refreshLatest(java.util.Collection<Integer> ids, long maxAgeMs, Runnable onChange)
	{
		long now = System.currentTimeMillis();
		List<Integer> stale = new ArrayList<>();
		for (int id : ids)
		{
			Long at = latestAt.get(id);
			if (at == null || now - at > maxAgeMs)
			{
				stale.add(id);
			}
		}
		if (stale.isEmpty())
		{
			return;
		}
		String path = stale.size() == 1 ? "latest?id=" + stale.get(0) : "latest";
		get(path, body -> {
			JsonObject data = gson.fromJson(body, JsonObject.class).getAsJsonObject("data");
			long t = System.currentTimeMillis();
			for (int id : stale)
			{
				JsonElement e = data.get(String.valueOf(id));
				if (e == null || !e.isJsonObject())
				{
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				Latest l = new Latest();
				l.high = num(o, "high");
				l.highTime = num(o, "highTime");
				l.low = num(o, "low");
				l.lowTime = num(o, "lowTime");
				latest.put(id, l);
				latestAt.put(id, t);
			}
			onChange.run();
		});
	}

	private final Map<Integer, Recent> recents = new ConcurrentHashMap<>();

	/** Typical (median) seller and buyer price over the last 30 minutes. */
	static class Recent
	{
		final long at;
		final long seller;
		final long buyer;

		Recent(long at, long seller, long buyer)
		{
			this.at = at;
			this.seller = seller;
			this.buyer = buyer;
		}
	}

	/** Cached 30-minute typical prices for one item (only for the item you're making an offer for). */
	Recent recent(int id, Runnable onChange)
	{
		Recent r = recents.get(id);
		if (r != null && System.currentTimeMillis() - r.at < RECENT_TTL)
		{
			return r;
		}
		get("timeseries?timestep=5m&id=" + id, body -> {
			JsonArray arr = gson.fromJson(body, JsonObject.class).getAsJsonArray("data");
			List<Long> lows = new ArrayList<>(), highs = new ArrayList<>();
			for (int i = Math.max(0, arr.size() - 6); i < arr.size(); i++)
			{
				JsonObject o = arr.get(i).getAsJsonObject();
				long lo = num(o, "avgLowPrice"), hi = num(o, "avgHighPrice");
				if (lo > 0)
				{
					lows.add(lo);
				}
				if (hi > 0)
				{
					highs.add(hi);
				}
			}
			recents.put(id, new Recent(System.currentTimeMillis(), median(lows), median(highs)));
			if (recents.size() > 200)
			{
				recents.clear();
			}
			onChange.run();
		});
		return r;
	}

	private static long median(List<Long> v)
	{
		if (v.isEmpty())
		{
			return 0;
		}
		List<Long> s = new ArrayList<>(v);
		Collections.sort(s);
		int m = s.size() / 2;
		return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
	}

	private Map<Integer, Window> windows(Reader body)
	{
		JsonObject data = gson.fromJson(body, JsonObject.class).getAsJsonObject("data");
		Map<Integer, Window> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : data.entrySet())
		{
			JsonObject o = e.getValue().getAsJsonObject();
			Window w = new Window();
			w.high = num(o, "avgHighPrice");
			w.low = num(o, "avgLowPrice");
			w.highVol = num(o, "highPriceVolume");
			w.lowVol = num(o, "lowPriceVolume");
			out.put(Integer.parseInt(e.getKey()), w);
		}
		return out;
	}

	private static long num(JsonObject o, String k)
	{
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0 : e.getAsLong();
	}

	private interface Handler
	{
		void handle(Reader body) throws Exception;
	}

	private void get(String path, Handler handler)
	{
		if (inFlight.putIfAbsent(path, Boolean.TRUE) != null)
		{
			return;
		}
		Request req = new Request.Builder().url(BASE + path).header("User-Agent", USER_AGENT).build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				inFlight.remove(path);
				log.debug("Flip Ledger: price request failed: {}", path, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (response.isSuccessful() && body != null)
					{
						handler.handle(body.charStream());
					}
				}
				catch (Exception e)
				{
					log.debug("Flip Ledger: couldn't read {}", path, e);
				}
				finally
				{
					inFlight.remove(path);
				}
			}
		});
	}
}
