package com.flipledger;

/**
 * Suggests a new price for a GE offer that is priced away from where the market is trading.
 * A buy bid under what buyers are paying, or a sell ask over it, won't fill; the other direction
 * always fills at the better market price, so it is never flagged.
 */
final class Correction
{
	/** Ignore trades older than this: the price may be stale. */
	static final long MAX_TRADE_AGE_SEC = 30 * 60;
	/** Without a stall, only flag an offer that is at least this far (percent) from the market. */
	static final int BIG_GAP_PERCENT = 2;

	private Correction()
	{
	}

	static final class Advice
	{
		final long price;
		final String text;
		/** A sell correction that would sell below your cost. */
		final boolean loss;

		Advice(long price, String text, boolean loss)
		{
			this.price = price;
			this.text = text;
			this.loss = loss;
		}
	}

	/**
	 * @param price     the offer's price
	 * @param remaining units still unfilled
	 * @param latest    most recent trades for the item (what buyers paid is {@code high})
	 * @param avgCost   sells only: average cost of what you hold, or -1 if unknown
	 * @param stalled   the offer has had no fills for longer than the fill window
	 * @return a correction, or null if the offer is fine or there is no fresh price
	 */
	static Advice advise(boolean buy, long price, long remaining, PriceService.Latest latest, long avgCost,
		boolean stalled, long nowSec)
	{
		if (latest == null || latest.high <= 0 || price <= 0 || remaining <= 0
			|| nowSec - latest.highTime > MAX_TRADE_AGE_SEC)
		{
			return null;
		}
		long market = latest.high;
		long gap = buy ? market - price : price - market;
		if (gap <= 0 || (!stalled && gap * 100 < price * BIG_GAP_PERCENT))
		{
			return null;
		}
		if (buy)
		{
			return new Advice(market, String.format("Raise to %,d (+%,d each)", market, gap), false);
		}
		boolean loss = false;
		String extra = "";
		if (avgCost >= 0)
		{
			long net = market - Gp.taxPerItem(market) - avgCost;
			if (net < 0)
			{
				loss = true;
				extra = String.format(", loses about %,d. Break-even is %,d", -net * remaining, Suggester.breakEven(avgCost));
			}
		}
		return new Advice(market, String.format("Lower to %,d (-%,d each)%s", market, gap, extra), loss);
	}
}
