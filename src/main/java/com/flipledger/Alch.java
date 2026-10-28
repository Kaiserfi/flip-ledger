package com.flipledger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntToLongFunction;

/**
 * High Level Alchemy maths from the cached bulk Wiki prices (no extra requests).
 */
final class Alch
{
	static final int COINS = 995;
	static final int NATURE_RUNE = 561;
	static final int FIRE_RUNE = 554;
	static final int BOND = 13190;
	/** Roughly how many High Alchs fit in an hour, and in a 4-hour buy-limit window. */
	static final int CASTS_PER_HOUR = 1200;
	static final int MAGIC_LEVEL = 55;
	private static final long MIN_HOURLY_VOLUME = 30;

	static class Pick
	{
		int id;
		String name;
		long buy;      // cautious price to buy one
		long highAlch;
		long profit;   // per cast, after the nature rune (and fire runes without a staff)
		int limit;     // GE buy limit, 0 if unknown
		long limitLeft;
		long perLimit; // profit from one 4-hour window, capped at what you can cast in 4 hours
		long volume;
	}

	private Alch()
	{
	}

	/** Cautious price to buy one: the higher of the hour and 5-minute averages buyers paid. */
	static long buyPrice(PriceService prices, int id)
	{
		PriceService.Window h = prices.hour.get(id);
		PriceService.Window f = prices.fiveMin.get(id);
		long a = h == null ? 0 : h.high, b = f == null ? 0 : f.high;
		return Math.max(a, b);
	}

	/** Runes for one cast: a nature rune, plus 5 fire runes unless you use a fire staff. */
	static long castCost(PriceService prices, boolean fireStaff)
	{
		long nat = buyPrice(prices, NATURE_RUNE);
		return nat + (fireStaff ? 0 : 5 * buyPrice(prices, FIRE_RUNE));
	}

	/**
	 * The best items to alch right now, most profit per cast first. Casting speed is the real limit
	 * (about 4,800 casts per 4 hours), so each cast should go to the best item until its buy limit runs out.
	 */
	static List<Pick> best(PriceService prices, boolean members, boolean fireStaff, IntToLongFunction boughtRecently, int count)
	{
		long runes = castCost(prices, fireStaff);
		List<Pick> out = new ArrayList<>();
		if (runes <= 0)
		{
			return out;
		}
		for (PriceService.Item it : prices.mapping.values())
		{
			if (it.highAlch <= 0 || (it.members && !members))
			{
				continue;
			}
			PriceService.Window h = prices.hour.get(it.id);
			if (h == null || h.high <= 0 || Math.min(h.highVol, h.lowVol) < MIN_HOURLY_VOLUME)
			{
				continue; // must actually be buyable in quantity
			}
			long buy = buyPrice(prices, it.id);
			long profit = it.highAlch - buy - runes;
			if (profit <= 0)
			{
				continue;
			}
			Pick p = new Pick();
			p.id = it.id;
			p.name = it.name;
			p.buy = buy;
			p.highAlch = it.highAlch;
			p.profit = profit;
			p.limit = it.limit;
			p.limitLeft = it.limit > 0 ? Math.max(0, it.limit - boughtRecently.applyAsLong(it.id)) : 4 * CASTS_PER_HOUR;
			p.perLimit = profit * Math.min(p.limitLeft, 4L * CASTS_PER_HOUR);
			p.volume = Math.min(h.highVol, h.lowVol);
			if (p.limitLeft > 0)
			{
				out.add(p);
			}
		}
		out.sort(Comparator.comparingLong((Pick p) -> p.profit).reversed());
		return out.size() > count ? new ArrayList<>(out.subList(0, count)) : out;
	}
}
