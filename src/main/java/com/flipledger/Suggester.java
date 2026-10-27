package com.flipledger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks consumable flips using the same rules as the Steady Flips checklist,
 * from bulk price data only (plus cached week history for the top few).
 */
class Suggester
{
	static final int TREND_CANDIDATES = 8;
	static final int SHOWN = 10;

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
	}

	static List<Suggestion> compute(PriceService prices, FlipLedgerConfig cfg, boolean membersWorld,
		java.util.function.IntToLongFunction boughtRecently, Runnable onTrend)
	{
		List<Suggestion> out = new ArrayList<>();
		if (prices.mapping.isEmpty() || prices.hour.isEmpty())
		{
			return out;
		}

		long budget = parse(cfg.budget(), 50_000_000L);
		long perSlot = Math.min(budget, parse(cfg.maxPerSlot(), 10_000_000L));
		long minProfit = parse(cfg.minProfit(), 25_000L);
		int minutes = cfg.fillWindow().minutes;

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
			if (it.limit <= 0 || (it.members && !membersWorld) || blocked.contains(it.id))
			{
				continue;
			}
			Consumables.Group g = Consumables.groupOf(it.name);
			if (g == null || !Consumables.enabled(g, cfg))
			{
				continue;
			}
			PriceService.Window h = prices.hour.get(it.id);
			if (h == null || h.high <= 0 || h.low <= 0 || h.highVol < 5 || h.lowVol < 5)
			{
				continue;
			}

			long buy = h.low;
			long sell = h.high;
			// Use the most cautious sell price we know: a brief spike in the hour average shouldn't make a margin
			PriceService.Window f5 = prices.fiveMin.get(it.id);
			if (f5 != null && f5.high > 0)
			{
				sell = Math.min(sell, f5.high);
			}
			PriceService.Window d0 = prices.day.get(it.id);
			if (d0 != null && d0.high > 0)
			{
				sell = Math.min(sell, d0.high);
			}
			if (buy < 10)
			{
				continue; // 1 gp margins under 10 gp sit in a huge queue and rarely fill in practice
			}
			long margin = sell - Gp.taxPerItem(sell) - buy;
			long minMargin = Math.max(1, Math.round(buy * 0.005));
			if (margin < minMargin || margin > buy * 0.6)
			{
				continue; // no room after tax, or a margin so wide it's probably one odd trade
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
			s.buy = buy;
			s.sell = sell;
			s.qty = qty;
			s.profit = profit;
			s.cost = qty * buy;
			s.breakEven = be;
			s.perHour = vol;
			s.fillMinutes = (int) Math.max(1, Math.round(qty * 60.0 / Math.max(1, vol)));
			candidates.add(s);
		}

		candidates.sort(Comparator.comparingLong((Suggestion s) -> s.profit).reversed());

		// Week trend only for the top few, cached for hours
		int checked = 0;
		for (Suggestion s : candidates)
		{
			if (out.size() >= SHOWN)
			{
				break;
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
			boolean risky = "sliding".equals(s.status) || "spiking".equals(s.status) || "swingy".equals(s.status);
			if (risky && !cfg.showRisky())
			{
				continue;
			}
			out.add(s);
		}

		// Fit the list inside the total budget, best first
		long left = budget;
		List<Suggestion> fitted = new ArrayList<>();
		for (Suggestion s : out)
		{
			if (s.cost <= left)
			{
				fitted.add(s);
				left -= s.cost;
			}
		}
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
