package com.flipledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything saved per RuneScape account (stored in RuneLite's profile config).
 */
class LedgerData
{
	static final int MAX_HISTORY = 300;

	/** Items bought through the GE that haven't been sold yet: itemId -> cost basis. */
	Map<Integer, Position> positions = new HashMap<>();

	/** Last seen state of each GE slot, so progress is only counted once. */
	SlotSnapshot[] slots = new SlotSnapshot[8];

	long allTimeProfit;
	int allTimeFlips;

	/** Profit and completed-flip events from the last ~26 hours, for the crew leaderboard. */
	List<Event> events = new ArrayList<>();

	synchronized List<Event> eventsCopy()
	{
		return new ArrayList<>(events);
	}

	synchronized void addEvent(long profit, int flips, long flipTotal, String item)
	{
		long now = System.currentTimeMillis() / 1000;
		Event ev = new Event(now, profit, flips);
		if (flips > 0)
		{
			ev.flipTotal = flipTotal;
			ev.item = item;
		}
		events.add(ev);
		events.removeIf(e -> e.t < now - 26 * 3600);
	}

	static class Event
	{
		long t;
		long profit;
		int flips;
		/** Set when a flip finished: its whole profit and the item, for "best single flip". */
		long flipTotal;
		String item;

		Event(long t, long profit, int flips)
		{
			this.t = t;
			this.profit = profit;
			this.flips = flips;
		}
	}

	/** GE buy limits: units bought per item in its current 4-hour window. */
	static final long LIMIT_WINDOW_MS = 4L * 3600 * 1000;
	Map<Integer, LimitWindow> limits = new HashMap<>();

	static class LimitWindow
	{
		/** When the first buy of this window filled; the limit resets 4 hours after this. */
		long start;
		long bought;
		String name;
		int limit;
	}

	/** Count units that just filled on a buy offer. The window starts with the first buy after the last reset. */
	synchronized void addBought(int itemId, long qty, long now, String name, int limit)
	{
		if (limits == null)
		{
			limits = new HashMap<>();
		}
		limits.values().removeIf(w -> now >= w.start + LIMIT_WINDOW_MS);
		LimitWindow w = limits.get(itemId);
		if (w == null)
		{
			w = new LimitWindow();
			w.start = now;
			limits.put(itemId, w);
		}
		w.bought += qty;
		w.name = name;
		if (limit > 0)
		{
			w.limit = limit;
		}
	}

	/** The item's current window, or null if nothing was bought in the last 4 hours. */
	synchronized LimitWindow limitWindow(int itemId, long now)
	{
		LimitWindow w = limits == null ? null : limits.get(itemId);
		return w == null || now >= w.start + LIMIT_WINDOW_MS ? null : w;
	}

	/** Active windows, soonest reset first. */
	synchronized List<LimitWindow> activeLimits(long now)
	{
		List<LimitWindow> out = new ArrayList<>();
		if (limits != null)
		{
			for (LimitWindow w : limits.values())
			{
				if (now < w.start + LIMIT_WINDOW_MS)
				{
					out.add(w);
				}
			}
		}
		out.sort((a, b) -> Long.compare(a.start, b.start));
		return out;
	}

	/**
	 * You made something: move what the inputs cost onto the output, so selling it shows the real profit.
	 * Inputs you bought through the GE carry their actual cost; any other input (mined, picked up, bought
	 * before the plugin) is counted at today's price, {@code marketEach}.
	 */
	synchronized void transferMade(int outId, long outQty, String outName, int[] inputs, long[] used, long[] marketEach,
		long now)
	{
		long total = 0;
		for (int i = 0; i < inputs.length; i++)
		{
			int id = inputs[i];
			long left = used[i];
			Position p = positions.get(id);
			if (p != null && p.qty > 0 && left > 0)
			{
				long moved = Math.min(left, p.qty);
				long cost = p.totalCost * moved / p.qty;
				p.qty -= moved;
				p.totalCost -= cost;
				if (p.qty <= 0)
				{
					positions.remove(id);
				}
				total += cost;
				left -= moved;

				// The flip follows the items: the input's entry shrinks
				Flip f = openFlip(id);
				if (f != null)
				{
					f.boughtQty -= moved;
					f.boughtCost -= cost;
					if (f.boughtQty <= 0 && f.qty <= 0)
					{
						history.remove(f);
					}
					else if (!positions.containsKey(id))
					{
						f.open = false;
					}
				}
			}
			total += left * Math.max(0, marketEach[i]);
		}

		Position o = positions.get(outId);
		if (o == null)
		{
			positions.put(outId, new Position(outQty, total));
		}
		else
		{
			o.qty += outQty;
			o.totalCost += total;
		}
		Flip of = openFlip(outId);
		if (of == null)
		{
			of = new Flip(now, outId, outName + " (made)");
		}
		of.boughtQty += outQty;
		of.boughtCost += total;
		of.time = now;
		moveToTop(of);
	}

	/** Newest activity first. One entry per flip: from the first buy until everything bought is sold. */
	List<Flip> history = new ArrayList<>();

	Flip openFlip(int itemId)
	{
		for (Flip f : history)
		{
			if (f.open && f.itemId == itemId)
			{
				return f;
			}
		}
		return null;
	}

	void moveToTop(Flip f)
	{
		history.remove(f);
		history.add(0, f);
		while (history.size() > MAX_HISTORY)
		{
			history.remove(history.size() - 1);
		}
	}

	static class Position
	{
		long qty;
		long totalCost;

		Position(long qty, long totalCost)
		{
			this.qty = qty;
			this.totalCost = totalCost;
		}
	}

	static class SlotSnapshot
	{
		int itemId;
		boolean buy;
		int qty;
		long spent;

		SlotSnapshot(int itemId, boolean buy, int qty, long spent)
		{
			this.itemId = itemId;
			this.buy = buy;
			this.qty = qty;
			this.spent = spent;
		}
	}

	static class Flip
	{
		/** Last activity. */
		long time;
		long started;
		int itemId;
		String name;
		/** Units sold so far. */
		long qty;
		/** Realised profit so far (after tax). */
		long profit;
		/** Cost of the units sold so far. */
		long cost;
		/** Proceeds after tax of the units sold so far. */
		long proceeds;
		/** Units bought in this flip and what they cost. */
		long boughtQty;
		long boughtCost;
		/** True until everything bought has been sold. */
		boolean open;

		Flip(long now, int itemId, String name)
		{
			this.time = now;
			this.started = now;
			this.itemId = itemId;
			this.name = name;
			this.open = true;
		}

		long avgBuy()
		{
			return boughtQty > 0 ? boughtCost / boughtQty : 0;
		}
	}
}
