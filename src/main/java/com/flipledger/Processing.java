package com.flipledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * "Can I turn this into something worth more?" Looks up to three steps ahead
 * (grimy herb -> clean -> unfinished potion -> potion, logs -> shafts -> headless arrows, ore -> bar)
 * using only cached bulk prices.
 */
final class Processing
{
	static final int MAX_STEPS = 3;
	private static final long MIN_HOURLY_VOLUME = 20;      // selling the item as it is
	private static final long PRODUCT_HOURLY_VOLUME = 100; // anything we'd suggest making must sell steadily

	static class Path
	{
		/** What one unit of the starting item is worth at the end, after tax and after buying any other inputs. */
		double valueEach;
		final List<Recipes.Recipe> steps = new ArrayList<>();
		/** Highest unmet skill requirement on this path, e.g. "Herblore 45", or null. */
		String needs;
	}

	private final PriceService prices;
	private final Recipes recipes;
	private final ToIntFunction<String> level;
	private final boolean members;
	private final boolean applyTax;
	/** True: skip recipes you can't make yet. False: allow them and note the level needed. */
	private final boolean strict;
	private final Map<Long, Path> memo = new HashMap<>();

	/** @param level the player's real level by skill name ("Herblore") */
	Processing(PriceService prices, Recipes recipes, ToIntFunction<String> level, boolean members, boolean applyTax)
	{
		this(prices, recipes, level, members, applyTax, true);
	}

	private Processing(PriceService prices, Recipes recipes, ToIntFunction<String> level, boolean members,
		boolean applyTax, boolean strict)
	{
		this.strict = strict;
		this.prices = prices;
		this.recipes = recipes;
		this.level = level;
		this.members = members;
		this.applyTax = applyTax;
	}

	/** What one unit sells for right now after tax (cautious price), or 0 if it barely trades. */
	double sellNet(int id)
	{
		PriceService.Window h = prices.hour.get(id);
		if (h == null || h.high <= 0 || Math.min(h.highVol, h.lowVol) < MIN_HOURLY_VOLUME)
		{
			return 0;
		}
		return cautious(id, h);
	}

	/** Like sellNet, but stricter for things you'd make: it must sell steadily (busy, and a tight buy/sell gap). */
	private double productNet(int id)
	{
		PriceService.Window h = prices.hour.get(id);
		if (h == null || h.high <= 0 || h.low <= 0 || Math.min(h.highVol, h.lowVol) < PRODUCT_HOURLY_VOLUME
			|| h.high - h.low > h.high * 0.15)
		{
			return 0;
		}
		return cautious(id, h);
	}

	private double cautious(int id, PriceService.Window h)
	{
		long p = h.high;
		PriceService.Window f = prices.fiveMin.get(id);
		if (f != null && f.high > 0)
		{
			p = Math.min(p, f.high);
		}
		return p - (applyTax ? Gp.taxPerItem(p) : 0);
	}

	/** What one unit costs to buy (cautious: the price buyers have been paying), or -1 if unknown. */
	long buyCost(int id)
	{
		if (id == 995)
		{
			return 1; // coins (sawmill fees and similar)
		}
		PriceService.Window h = prices.hour.get(id);
		PriceService.Window f = prices.fiveMin.get(id);
		long a = h == null ? 0 : h.high, b = f == null ? 0 : f.high;
		long p = Math.max(a, b);
		return p > 0 ? p : -1;
	}

	/** Best way to process this item (at least one step), or null if nothing is worth it or possible. */
	Path best(int id)
	{
		return process(id, MAX_STEPS, true);
	}

	/** Same, ignoring your levels: to show "you'd need Herblore 45 for something better". */
	Path bestIgnoringLevels(int id)
	{
		return new Processing(prices, recipes, level, members, applyTax, false).process(id, MAX_STEPS, true);
	}

	private Path process(int id, int depth, boolean mustProcess)
	{
		long key = id * 8L + depth * 2L + (mustProcess ? 1 : 0);
		if (memo.containsKey(key))
		{
			return memo.get(key);
		}
		memo.put(key, null); // guards against recipe loops
		Path best = null;
		if (!mustProcess)
		{
			double s = productNet(id);
			if (s > 0)
			{
				best = new Path();
				best.valueEach = s;
			}
		}
		if (depth > 0)
		{
			for (Recipes.Recipe r : recipes.using(id))
			{
				PriceService.Item out = prices.mapping.get(r.o);
				if (out == null || (out.members && !members))
				{
					continue;
				}
				String unmet = unmet(r);
				if (unmet != null && strict)
				{
					continue;
				}
				double mine = r.qtyOf(id);
				double others = 0;
				boolean priced = true;
				for (double[] x : r.m)
				{
					if ((int) x[0] == id)
					{
						continue;
					}
					long c = buyCost((int) x[0]);
					if (c < 0)
					{
						priced = false;
						break;
					}
					others += c * x[1];
				}
				if (!priced || mine <= 0)
				{
					continue;
				}
				Path next = process(r.o, depth - 1, false);
				if (next == null)
				{
					continue;
				}
				double value = (r.q * next.valueEach - others) / mine;
				if (best == null || value > best.valueEach)
				{
					best = new Path();
					best.valueEach = value;
					best.steps.add(r);
					best.steps.addAll(next.steps);
					best.needs = unmet != null ? unmet : next.needs;
				}
			}
		}
		memo.put(key, best);
		return best;
	}

	/** First skill requirement the player doesn't meet, e.g. "Herblore 45", or null. */
	private String unmet(Recipes.Recipe r)
	{
		if (r.s == null)
		{
			return null;
		}
		for (Object[] s : r.s)
		{
			String skill = String.valueOf(s[0]);
			int need = ((Number) s[1]).intValue();
			if (level.applyAsInt(skill) < need)
			{
				return Character.toUpperCase(skill.charAt(0)) + skill.substring(1) + " " + need;
			}
		}
		return null;
	}
}
