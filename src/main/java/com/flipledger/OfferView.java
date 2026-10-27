package com.flipledger;

/**
 * A snapshot of one active GE slot, built on the client thread for the side panel.
 */
class OfferView
{
	enum Status
	{
		ACTIVE,
		COMPLETE,
		CANCELLED
	}

	final int slot;
	final int itemId;
	final String name;
	final boolean buy;
	final Status status;
	final int filled;
	final int total;
	final long price;
	final long spent;
	/** Sells only: average cost of what you're holding, or -1 if unknown (bought before the plugin). */
	final long avgCost;

	OfferView(int slot, int itemId, String name, boolean buy, Status status, int filled, int total, long price, long spent, long avgCost)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.name = name;
		this.buy = buy;
		this.status = status;
		this.filled = filled;
		this.total = total;
		this.price = price;
		this.spent = spent;
		this.avgCost = avgCost;
	}

	/** Sells only: profit if the whole offer sells at the listed price, after tax. */
	long estProfit()
	{
		long netEach = price - Gp.taxPerItem(price);
		return (netEach - avgCost) * total;
	}

	/** Sells only: lowest price that doesn't lose money. */
	long breakEven()
	{
		long s = Math.max(0, (long) Math.ceil(avgCost / 0.98) - 2);
		while (s - Gp.taxPerItem(s) < avgCost)
		{
			s++;
		}
		return s;
	}
}
