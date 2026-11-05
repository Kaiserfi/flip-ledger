package com.flipledger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks steady flips using the same rules as the Steady Flips checklist,
 * from bulk price data only (plus cached week history for the top few).
 */
class Suggester
{
	static final int TREND_CANDIDATES = 8;
	static final int SHOWN = 15;
	/** The best few are re-priced with the same 30-minute data the price check uses (refreshed at most every 5 min). */
	static final int RECENT_CHECKS = 10;
	static final long RECENT_MAX_AGE = 10 * 60_000L;
	/** Latest trades for the best few come from one shared bulk request, refreshed at most this often. */
	static final long LATEST_MAX_AGE = 3 * 60_000L;
	/** Both sides must have traded within this many seconds, or the item isn't trading right now. */
	static final long FRESH_SEC = 600;
	/** What the last compute() hid, so an empty list can say why. */
	static volatile int hiddenQueue;
	static volatile int hiddenRisky;

	static class Suggestion
	{
		int id;
		String name;
		long buy;
		long sell;
		long qty;
		long profit;
		long cost;
		long breakEven;
		long perHour;
		int fillMinutes;
		String status; // steady, settled, checking...
		String why;
		long limitLeft;
		long vol;
		/** Hourly average buyer / seller price: caps what we expect to sell for, and the second margin check. */
		long hi;
		long lo;
		/** Latest trade far from the typical price (a hint the price is moving), or null. */
		String odd;
		/** Last 30 minutes: "ok", "warn" or "bad"; null when not checked. */
		String steadiness;
		String steadyWhy;
		/** Set by size() when it refuses because of the queue-only rule. */
		boolean droppedByQueue;
	}

	/** One tick: about 0.1% of the price, at least 1 gp. The smallest step that jumps a queue. */
	static long tick(long p)
	{
		return Math.max(1, p / 1000);
	}

	/** Bid a tick over the sellers on items from 1,000 gp, so you skip the queue; cheap items bid at the price. */
	static long bidTick(long p)
	{
		return p < 1000 ? 0 : tick(p);
	}

	static final class Stability
	{
		final String level;
		final String why;

		Stability(String level, String why)
		{
			this.level = level;
			this.why = why;
		}
	}

	private static long median(List<Long> v)
	{
		List<Long> s = new ArrayList<>(v);
		java.util.Collections.sort(s);
		int m = s.size() / 2;
		return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
	}

	/** How far the second half of the list's median moved from the first half's. */
	private static double move(List<Long> a)
	{
		if (a.size() < 3)
		{
			return 0;
		}
		int k = a.size() / 2;
		return median(a.subList(a.size() - k, a.size())) / (double) median(a.subList(0, k)) - 1;
	}

	private static double range(List<Long> a)
	{
		return (java.util.Collections.max(a) - java.util.Collections.min(a)) / (double) median(a);
	}

	private static boolean tiny(List<Long> a)
	{
		return java.util.Collections.max(a) - java.util.Collections.min(a) <= 2;
	}

	/**
	 * Are both price columns steady over the last 30 minutes (5-minute averages, oldest first)?
	 * The same rules as the Steady Flips website: a wide swing, sellers drying up, or buyers (your exit) falling is bad.
	 */
	static Stability stability(List<Long> sellers, List<Long> buyers)
	{
		if (sellers == null || buyers == null || sellers.isEmpty() || buyers.isEmpty())
		{
			return new Stability("warn", "Quiet: no recent trades on one side");
		}
		double sMove = move(sellers);
		double bMove = move(buyers);
		double worst = Math.max(range(sellers), range(buyers));
		boolean cheapSteady = median(sellers) < 200 && tiny(sellers) && tiny(buyers);
		String level;
		if (cheapSteady)
		{
			level = "ok";
		}
		else
		{
			level = worst <= 0.012 && Math.abs(bMove) <= 0.004 && sMove <= 0.004 ? "ok" : worst <= 0.025 ? "warn" : "bad";
			if (bMove < -0.008)
			{
				level = "bad";
			}
			else if (bMove < -0.003 && "ok".equals(level))
			{
				level = "warn";
			}
		}
		String why = String.format("30 min: sellers %,d-%,d, buyers %,d-%,d%s", java.util.Collections.min(sellers),
			java.util.Collections.max(sellers), java.util.Collections.min(buyers), java.util.Collections.max(buyers),
			bMove < -0.003 ? String.format(", buyers down %.1f%%", -bMove * 100) : "");
		return new Stability(level, why);
	}

	/** A hint when the very latest trade is far from the typical price (one odd trade, or the price is moving). */
	static String odd(PriceService.Latest l, long typicalSeller, long typicalBuyer)
	{
		if (l == null)
		{
			return null;
		}
		List<String> o = new ArrayList<>();
		if (l.low > 0 && typicalSeller > 0 && Math.abs(l.low - typicalSeller) > Math.max(3, 0.02 * typicalSeller))
		{
			o.add(String.format("latest seller %,d vs typical %,d", l.low, typicalSeller));
		}
		if (l.high > 0 && typicalBuyer > 0 && Math.abs(l.high - typicalBuyer) > Math.max(3, 0.02 * typicalBuyer))
		{
			o.add(String.format("latest buyer %,d vs typical %,d", l.high, typicalBuyer));
		}
		return o.isEmpty() ? null : String.join("; ", o);
	}

	/** Size a suggestion at these prices. False if it isn't worth a slot (thin margin or too little profit). */
	private static boolean size(Suggestion s, long buy, long sell, long minProfit, long perSlot, int minutes, boolean queueSafe)
	{
		if (buy < 10 || sell <= 0)
		{
			return false;
		}
		long margin = sell - Gp.taxPerItem(sell) - buy;
		if (s.hi > 0 && s.lo > 0)
		{
			margin = Math.min(margin, s.hi - Gp.taxPerItem(s.hi) - s.lo); // the hourly averages must leave room too
		}
		long minMargin = Math.max(1, Math.round(buy * 0.005));
		if (margin < minMargin || margin > buy * 0.6)
		{
			return false;
		}
		if (queueSafe && margin < 3 * tick(buy))
		{
			s.droppedByQueue = true;
			return false; // a tick or two of margin: you'd sit behind the queue and never fill
		}
		long qty = Math.min(s.limitLeft, (long) (s.vol * (minutes / 60.0) * 0.5));
		qty = Math.min(qty, perSlot / buy);
		if (qty < 1 || margin * qty < minProfit)
		{
			return false;
		}
		s.buy = buy;
		s.sell = sell;
		s.qty = qty;
		s.profit = margin * qty;
		s.cost = qty * buy;
		s.breakEven = breakEven(buy);
		s.fillMinutes = (int) Math.max(1, Math.round(qty * 60.0 / Math.max(1, s.vol)));
		return true;
	}

	static List<Suggestion> compute(PriceService prices, FlipLedgerConfig cfg, boolean membersWorld,
		java.util.function.IntToLongFunction boughtRecently, java.util.function.IntPredicate busy, long cash, Runnable onTrend)
	{
		List<Suggestion> out = new ArrayList<>();
		hiddenQueue = 0;
		hiddenRisky = 0;
		int hq = 0;
		int hr = 0;
		if (prices.mapping.isEmpty() || prices.hour.isEmpty())
		{
			return out;
		}

		long budget = parse(cfg.budget(), 50_000_000L);
		if (cash >= 0)
		{
			budget = Math.min(budget, cash); // never suggest more than you actually have
		}
		long perSlot = Math.min(budget, parse(cfg.maxPerSlot(), 10_000_000L));
		long minProfit = parse(cfg.minProfit(), 25_000L);
		int minutes = cfg.fillWindow().minutes;
		boolean queueSafe = cfg.skipQueueMargins();

		java.util.Set<Integer> blocked = new java.util.HashSet<>();
		for (String b : cfg.blockedItems().split(","))
		{
			try
			{
				blocked.add(Integer.parseInt(b.trim()));
			}
			catch (NumberFormatException ignored)
			{
			}
		}

		List<Suggestion> candidates = new ArrayList<>();
		for (PriceService.Item it : prices.mapping.values())
		{
			if (it.limit <= 0 || (it.members && !membersWorld) || blocked.contains(it.id) || busy.test(it.id))
			{
				continue;
			}
			Consumables.Group g = Consumables.groupOf(it.name);
			if (g == null ? !cfg.catOther() : !Consumables.enabled(g, cfg))
			{
				continue;
			}
			PriceService.Window h = prices.hour.get(it.id);
			if (h == null || h.high <= 0 || h.low <= 0 || h.highVol < 5 || h.lowVol < 5)
			{
				continue;
			}
			// Both sides must be busy: one side far quieter than the other means a one-way market
			if (Math.min(h.highVol, h.lowVol) < (h.low < 200 ? 0.1 : 0.4) * Math.max(h.highVol, h.lowVol))
			{
				continue;
			}

			// Prefer the last 5 minutes when both sides traded enough there: the hour average lags behind a
			// price that's moving (bid too low on a rise, sell too high after a spike)
			long buy = h.low;
			long sell = h.high;
			PriceService.Window f5 = prices.fiveMin.get(it.id);
			long need5 = Math.max(3, Math.min(h.highVol, h.lowVol) / 40);
			if (f5 != null && f5.low > 0 && f5.high > 0 && f5.lowVol >= need5 && f5.highVol >= need5)
			{
				buy = f5.low;
				sell = f5.high;
			}
			else if (f5 != null)
			{
				// Too few recent trades to trust on their own: just stay cautious
				buy = f5.low > 0 ? Math.max(buy, f5.low) : buy;
				sell = f5.high > 0 ? Math.min(sell, f5.high) : sell;
			}
			buy += bidTick(buy);          // a tick over the sellers on pricey items, to skip the queue
			sell = Math.min(sell, h.high); // never count on more than the hourly average buyer
			if (buy < 10)
			{
				continue; // 1 gp margins under 10 gp sit in a huge queue and rarely fill in practice
			}
			// The smaller of today's margin and the hourly one: both must leave room after tax
			long margin = Math.min(sell - Gp.taxPerItem(sell) - buy, h.high - Gp.taxPerItem(h.high) - h.low);
			long minMargin = Math.max(1, Math.round(buy * 0.005));
			if (margin < minMargin || margin > buy * (buy < 200 ? 0.6 : 0.05))
			{
				continue; // no room after tax, or a margin so wide it's probably one odd trade
			}
			if (queueSafe && margin < 3 * tick(buy))
			{
				hq++;
				continue;
			}

			// Spike / slide check against the day, ignoring 1-2 gp wobble on cheap items
			PriceService.Window d = prices.day.get(it.id);
			if (d != null && d.low > 0 && d.high > 0)
			{
				boolean cheap = buy < 200;
				long dl = Math.abs(buy - d.low), dh = Math.abs(sell - d.high);
				if ((!cheap && (dl > d.low * 0.06 || dh > d.high * 0.06)) || (cheap && (dl > 3 && dl > d.low * 0.08)))
				{
					continue;
				}
			}

			// Latest 5 minutes: buyers must still be above break-even
			long be = breakEven(buy);
			PriceService.Window f = prices.fiveMin.get(it.id);
			if (f != null && f.high > 0 && f.high < be)
			{
				continue;
			}

			long limitLeft = it.limit - boughtRecently.applyAsLong(it.id);
			if (limitLeft <= 0)
			{
				continue; // buy limit used up for this 4-hour window
			}
			long vol = Math.min(h.highVol, h.lowVol);
			if (d != null)
			{
				vol = Math.min(vol, (long) (Math.min(d.highVol, d.lowVol) / 24.0 * 1.5)); // an hour that's busier than the day is a spike
			}
			if (vol < 3)
			{
				continue;
			}
			long qty = Math.min(limitLeft, (long) (vol * (minutes / 60.0) * 0.5));
			qty = Math.min(qty, perSlot / buy);
			if (qty < 1)
			{
				continue;
			}
			long profit = margin * qty;
			if (profit < minProfit)
			{
				continue;
			}

			Suggestion s = new Suggestion();
			s.id = it.id;
			s.name = it.name;
			s.perHour = vol;
			s.vol = vol;
			s.limitLeft = limitLeft;
			s.hi = h.high;
			s.lo = h.low;
			if (size(s, buy, sell, minProfit, perSlot, minutes, queueSafe))
			{
				candidates.add(s);
			}
			else if (s.droppedByQueue)
			{
				hq++;
			}
		}

		candidates.sort(Comparator.comparingLong((Suggestion s) -> s.profit).reversed());

		// Latest trades for the best few: one shared bulk request, only to spot items that aren't trading right now
		List<Integer> best = new ArrayList<>();
		for (int i = 0; i < Math.min(RECENT_CHECKS, candidates.size()); i++)
		{
			best.add(candidates.get(i).id);
		}
		if (best.size() > 1)
		{
			prices.refreshLatest(best, LATEST_MAX_AGE, onTrend);
		}
		long nowSec = System.currentTimeMillis() / 1000;

		// Week trend only for the top few, cached for hours
		int checked = 0;
		int repriced = 0;
		for (Suggestion s : candidates)
		{
			if (out.size() >= SHOWN)
			{
				break;
			}
			if (repriced < RECENT_CHECKS)
			{
				// Re-price with the typical prices of the last 30 minutes, like the price check box
				repriced++;
				PriceService.Latest lt = prices.latestFor(s.id);
				if (lt != null && lt.high > 0 && lt.low > 0 && (nowSec - lt.highTime > FRESH_SEC || nowSec - lt.lowTime > FRESH_SEC))
				{
					continue; // one side hasn't traded in 10 minutes: not trading right now
				}
				PriceService.Recent r = prices.recent(s.id, onTrend, RECENT_MAX_AGE);
				if (r != null && r.seller > 0 && r.buyer > 0)
				{
					long buy = r.seller + bidTick(r.seller);
					long sell = Math.min(r.buyer, s.hi); // typical buyer, never above the hourly average
					if (!size(s, buy, sell, minProfit, perSlot, minutes, queueSafe))
					{
						if (s.droppedByQueue)
						{
							hq++;
						}
						continue; // the margin isn't there on recent trades
					}
					Stability st = stability(r.sellers, r.buyers);
					s.steadiness = st.level;
					s.steadyWhy = st.why;
					s.odd = odd(lt, r.seller, r.buyer);
				}
			}
			if (checked < TREND_CANDIDATES)
			{
				checked++;
				PriceService.Trend t = prices.trend(s.id, onTrend);
				if (t == null)
				{
					s.status = "checking";
					s.why = "Loading the week chart";
				}
				else
				{
					String[] c = classify(t.mids);
					s.status = c[0];
					s.why = c[1];
				}
			}
			else
			{
				s.status = "unchecked";
				s.why = "Week chart not checked (keeps Wiki requests low)";
			}
			if ("bad".equals(s.steadiness))
			{
				s.status = "unsteady";
				s.why = s.steadyWhy;
			}
			boolean risky = "sliding".equals(s.status) || "spiking".equals(s.status) || "swingy".equals(s.status)
				|| "unsteady".equals(s.status);
			if (risky && !cfg.showRisky())
			{
				hr++;
				continue;
			}
			out.add(s);
		}

		// Re-pricing can change the order; ones we couldn't check go after the ones we could
		out.sort(Comparator.comparingInt((Suggestion s) -> "unchecked".equals(s.status) ? 1 : 0)
			.thenComparing(Comparator.comparingLong((Suggestion s) -> s.profit).reversed()));

		// Each suggestion only has to fit your budget on its own: you place them one at a time, and once one is
		// in the GE your cash drops and the rest are re-sized to what's left
		List<Suggestion> fitted = new ArrayList<>();
		for (Suggestion s : out)
		{
			if (s.cost <= budget)
			{
				fitted.add(s);
			}
		}
		hiddenQueue = hq;
		hiddenRisky = hr;
		return fitted;
	}

	/** Same week-chart rules as Steady Flips. Returns {status, reason}. */
	static String[] classify(List<Long> p)
	{
		if (p.size() < 16)
		{
			return new String[]{"unchecked", "Not enough history"};
		}
		double mean = p.stream().mapToLong(Long::longValue).average().orElse(0);
		double sd = Math.sqrt(p.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / p.size());
		double last = p.get(p.size() - 1);
		double week = last / p.get(0) - 1, dev = last / mean - 1, cv = sd / mean;
		List<Long> l6 = p.subList(p.size() - 6, p.size());
		double lmean = l6.stream().mapToLong(Long::longValue).average().orElse(1);
		double rng = (l6.stream().mapToLong(Long::longValue).max().orElse(0) - l6.stream().mapToLong(Long::longValue).min().orElse(0)) / lmean;
		double tr = last / l6.get(0) - 1;
		boolean cheap = mean < 200;
		double wLim = mean >= 5e6 ? 0.08 : 0.12;
		String why = String.format("Week %+.1f%%, last 36h %+.1f%%", week * 100, tr * 100);
		if (cheap && (p.stream().mapToLong(Long::longValue).max().orElse(0) - p.stream().mapToLong(Long::longValue).min().orElse(0)) <= 2)
		{
			return new String[]{"steady", "Within 2 gp all week"};
		}
		if (week < -wLim && Math.abs(tr) < 0.03 && rng < 0.05 && dev < 0.04)
		{
			return new String[]{"settled", "Fell earlier (week " + String.format("%+.1f%%", week * 100) + ") but flat for 36h: sell promptly"};
		}
		if (week < -wLim || tr < -0.05)
		{
			return new String[]{"sliding", why};
		}
		if (dev > 0.06 || tr > 0.05)
		{
			return new String[]{"spiking", why};
		}
		if (Math.abs(week) >= wLim || cv >= 0.08 || rng >= 0.07 || Math.abs(dev) >= 0.06)
		{
			return new String[]{"swingy", why};
		}
		return new String[]{"steady", why};
	}

	static long breakEven(long cost)
	{
		long s = Math.max(0, (long) Math.ceil(cost / 0.98) - 2);
		while (s - Gp.taxPerItem(s) < cost)
		{
			s++;
		}
		return s;
	}

	static long parse(String s, long dflt)
	{
		if (s == null)
		{
			return dflt;
		}
		String t = s.trim().toLowerCase().replace(",", "").replace(" ", "");
		if (t.isEmpty())
		{
			return dflt;
		}
		double mult = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k' || last == 'm' || last == 'b')
		{
			mult = last == 'k' ? 1e3 : last == 'm' ? 1e6 : 1e9;
			t = t.substring(0, t.length() - 1);
		}
		try
		{
			return Math.round(Double.parseDouble(t) * mult);
		}
		catch (NumberFormatException e)
		{
			return dflt;
		}
	}
}
