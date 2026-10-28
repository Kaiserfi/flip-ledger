package com.flipledger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Recommended prices for one item, from the cached bulk Wiki prices (no extra requests
 * except the item's week chart, cached for hours).
 */
final class PriceCheck
{
	private static final Color GOLD = new Color(0xF1C40F);
	private static final Color GREEN = new Color(0x2ECC71);
	private static final Color RED = new Color(0xE74C3C);
	private static final Color GRAY = Color.LIGHT_GRAY;

	private PriceCheck()
	{
	}

	static List<PriceCheckOverlay.Line> lines(PriceService prices, int id, boolean sell, int qty, long avgCost,
		boolean applyTax, LedgerData.LimitWindow lw,
		Processing proc, Runnable onTrend)
	{
		List<PriceCheckOverlay.Line> out = new ArrayList<>();
		PriceService.Window h = prices.hour.get(id);
		PriceService.Window f = prices.fiveMin.get(id);
		// Prefer the typical price over the last 30 minutes (one odd trade can't steer it)
		PriceService.Recent r = prices.recent(id, onTrend);
		long seller = r != null && r.seller > 0 ? r.seller : pick(f == null ? 0 : f.low, h == null ? 0 : h.low);
		long buyer = r != null && r.buyer > 0 ? r.buyer : pick(f == null ? 0 : f.high, h == null ? 0 : h.high);
		if (seller <= 0 || buyer <= 0)
		{
			out.add(new PriceCheckOverlay.Line(prices.hourAt == 0 ? "Prices loading..." : "Not enough trades", "", GRAY));
			return out;
		}
		long taxB = applyTax ? Gp.taxPerItem(buyer) : 0;
		long n = Math.max(1, qty);

		if (!sell)
		{
			long each = buyer - taxB - seller;
			long maxBid = buyer - taxB - Math.max(1, Math.round(seller * 0.005));
			out.add(new PriceCheckOverlay.Line("Buy at", String.format("%,d", seller), GOLD));
			out.add(new PriceCheckOverlay.Line("Sell later at", String.format("%,d", buyer), GREEN));
			boolean thin = maxBid < seller;
			out.add(new PriceCheckOverlay.Line("Don't bid above", maxBid > 0 ? String.format("%,d", maxBid) : "-", thin ? RED : GRAY));
			if (thin)
			{
				out.add(new PriceCheckOverlay.Line("Margin too thin", "skip / lowball", RED));
			}
			out.add(new PriceCheckOverlay.Line("Profit each", Gp.signed(each), each > 0 ? GREEN : RED));
			out.add(new PriceCheckOverlay.Line("x " + String.format("%,d", n), Gp.signed(each * n), each > 0 ? GREEN : RED));
			out.add(new PriceCheckOverlay.Line("Break-even", String.format("%,d", Suggester.breakEven(seller)), GRAY));
			limitLines(out, prices, id, qty, lw);
			PriceService.Item item = prices.mapping.get(id);
			long runes = Alch.castCost(prices, true);
			if (item != null && item.highAlch > 0 && runes > 0 && item.highAlch - seller - runes > 0)
			{
				long a = item.highAlch - seller - runes;
				out.add(new PriceCheckOverlay.Line("High Alch each", Gp.signed(a), GREEN));
			}
		}
		else
		{
			out.add(new PriceCheckOverlay.Line("List at", String.format("%,d", buyer), GREEN));
			if (avgCost > 0)
			{
				long each = buyer - taxB - avgCost;
				long be = Suggester.breakEven(avgCost);
				out.add(new PriceCheckOverlay.Line("You paid", String.format("%,d", avgCost), GRAY));
				out.add(new PriceCheckOverlay.Line("Break-even", String.format("%,d", be), GRAY));
				out.add(new PriceCheckOverlay.Line("Profit each", Gp.signed(each), each >= 0 ? GREEN : RED));
				out.add(new PriceCheckOverlay.Line("x " + String.format("%,d", n), Gp.signed(each * n), each >= 0 ? GREEN : RED));
				if (buyer < be)
				{
					out.add(new PriceCheckOverlay.Line("Buyers below BE", "hold or cut", RED));
				}
			}
			else
			{
				out.add(new PriceCheckOverlay.Line("Cost", "unknown", GRAY));
			}
			out.add(new PriceCheckOverlay.Line("Instant sale ~", String.format("%,d", seller), GRAY));
		}

		processLines(out, proc, id, sell ? (avgCost > 0 ? avgCost : -1) : seller, buyer - taxB, n, prices);

		// Latest single trade vs the 30-minute typical: catches a crash in progress
		PriceService.Latest l = prices.latestFor(id);
		if (l != null && l.high > 0)
		{
			long ago = Math.max(0, System.currentTimeMillis() / 1000 - l.highTime);
			String when = ago < 60 ? ago + "s ago" : (ago / 60) + "m ago";
			boolean dropping = l.high < buyer * 0.97 && ago < 600;
			out.add(new PriceCheckOverlay.Line("Latest buyer", String.format("%,d · %s", l.high, when), dropping ? RED : GRAY));
			if (dropping)
			{
				out.add(new PriceCheckOverlay.Line(sell ? "Dropping now" : "Dropping now: wait", String.format("%.1f%%", (l.high / (double) buyer - 1) * 100), RED));
			}
		}

		PriceService.Trend t = prices.trend(id, onTrend);
		if (t != null)
		{
			String[] c = Suggester.classify(t.mids);
			Color col = "steady".equals(c[0]) ? GREEN : "settled".equals(c[0]) ? GOLD : ("unchecked".equals(c[0]) ? GRAY : RED);
			String verdict = c[0];
			if (!sell && ("sliding".equals(c[0]) || "spiking".equals(c[0])))
			{
				verdict = c[0] + ": skip";
			}
			out.add(new PriceCheckOverlay.Line("Week", verdict, col));
		}
		else
		{
			out.add(new PriceCheckOverlay.Line("Week", "checking...", GRAY));
		}
		out.add(new PriceCheckOverlay.Line("Volume/hr", h == null ? "-" : String.format("%,d", Math.min(h.highVol, h.lowVol)), GRAY));
		return out;
	}

	/**
	 * "Is there a better way out?" The best thing to make from this item (up to three steps), what each
	 * unit is worth that way, and how that compares with selling it as it is.
	 * {@code cost} is what each one cost you (or will cost), {@code asIsNet} what one sells for now after tax.
	 */
	private static void processLines(List<PriceCheckOverlay.Line> out, Processing proc, int id, long cost,
		long asIsNet, long n, PriceService prices)
	{
		if (proc == null)
		{
			return;
		}
		Processing.Path p = proc.best(id);
		if (p != null && !p.steps.isEmpty() && p.valueEach > asIsNet * 1.01 + 1)
		{
			out.add(new PriceCheckOverlay.Line("Better: make", short_(name(prices, p.steps.get(p.steps.size() - 1).o)), GOLD));
			if (p.steps.size() > 1)
			{
				StringBuilder via = new StringBuilder();
				for (int i = 0; i < p.steps.size() - 1; i++)
				{
					via.append(i > 0 ? " > " : "").append(name(prices, p.steps.get(i).o));
				}
				out.add(new PriceCheckOverlay.Line("via", short_(via.toString()), GRAY));
			}
			java.util.LinkedHashSet<String> where = new java.util.LinkedHashSet<>();
			for (Recipes.Recipe r : p.steps)
			{
				if (r.f != null && !r.f.isEmpty())
				{
					where.add(r.f);
				}
			}
			if (!where.isEmpty())
			{
				out.add(new PriceCheckOverlay.Line("at", short_(String.join(", ", where)), GRAY));
			}
			long each = Math.round(p.valueEach) - (cost > 0 ? cost : 0);
			if (cost > 0)
			{
				out.add(new PriceCheckOverlay.Line("Made + sold each", Gp.signed(each), each > 0 ? GREEN : RED));
				out.add(new PriceCheckOverlay.Line("x " + String.format("%,d", n), Gp.signed(each * n), each > 0 ? GREEN : RED));
			}
			out.add(new PriceCheckOverlay.Line("vs selling as is", Gp.signed(Math.round(p.valueEach) - asIsNet) + " each", GREEN));
		}
		else
		{
			Processing.Path locked = proc.bestIgnoringLevels(id);
			if (locked != null && locked.needs != null && locked.valueEach > asIsNet * 1.01 + 1)
			{
				out.add(new PriceCheckOverlay.Line("Could make " + short_(name(prices, locked.steps.get(locked.steps.size() - 1).o)),
					"needs " + locked.needs, GRAY));
			}
		}
	}

	private static String name(PriceService prices, int id)
	{
		PriceService.Item it = prices.mapping.get(id);
		return it == null ? "item" : it.name;
	}

	private static String short_(String s)
	{
		return s.length() > 22 ? s.substring(0, 21) + "…" : s;
	}

	/** Buy limit left in this 4-hour window, and a warning if the offer asks for more than that. */
	private static void limitLines(List<PriceCheckOverlay.Line> out, PriceService prices, int id, int qty,
		LedgerData.LimitWindow lw)
	{
		PriceService.Item it = prices.mapping.get(id);
		int limit = it != null && it.limit > 0 ? it.limit : (lw != null ? lw.limit : 0);
		if (limit <= 0)
		{
			return;
		}
		long bought = lw == null ? 0 : lw.bought;
		long left = Math.max(0, limit - bought);
		Color c = left == 0 ? RED : bought > 0 ? GOLD : GRAY;
		out.add(new PriceCheckOverlay.Line("Buy limit left", String.format("%,d / %,d", left, limit), c));
		if (lw != null)
		{
			out.add(new PriceCheckOverlay.Line("Limit resets", Gp.clock(lw.start + LedgerData.LIMIT_WINDOW_MS), GRAY));
		}
		if (qty > left)
		{
			out.add(new PriceCheckOverlay.Line(left == 0 ? "Limit reached" : "Over limit",
				left == 0 ? "won't fill yet" : String.format("only %,d fill", left), RED));
		}
	}

	/** Latest 5-minute price when there is one, otherwise the hour average. */
	private static long pick(long fiveMin, long hour)
	{
		return fiveMin > 0 ? fiveMin : hour;
	}
}
