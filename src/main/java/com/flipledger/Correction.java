package com.flipledger;

/**
 * Suggests a new price for a GE offer that is priced away from where the market is trading.
 * <p>
 * A buy offer fills when someone sells into it, so the reference is what instant-sellers are getting
 * ({@code low}); a sell offer fills when someone buys, so it is what instant-buyers are paying ({@code high}).
 * A bid above the market, or an ask below it, always fills at the better market price and is never flagged.
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
		/** The correction would lose money (a sell below your cost, or a buy that leaves no margin). */
		final boolean loss;
		/** Buys only: the market has moved so far that no price that fills leaves a profit. */
		final boolean cancel;

		Advice(long price, String text, boolean loss, boolean cancel)
		{
			this.price = price;
			this.text = text;
			this.loss = loss;
			this.cancel = cancel;
		}
	}

	/**
	 * @param price     the offer's price
	 * @param remaining units still unfilled
	 * @param latest    most recent trades for the item
	 * @param avgCost   sells only: average cost of what you hold, or -1 if unknown
	 * @param stalled   the offer has had no fills for longer than the fill window
	 * @return a correction, or null if the offer is fine or there is no fresh price
	 */
	static Advice advise(boolean buy, long price, long remaining, PriceService.Latest latest, long avgCost,
		boolean stalled, long nowSec)
	{
		if (latest == null || price <= 0 || remaining <= 0)
		{
			return null;
		}
		long market = buy ? latest.low : latest.high;
		long tradedAt = buy ? latest.lowTime : latest.highTime;
		if (market <= 0 || nowSec - tradedAt > MAX_TRADE_AGE_SEC)
		{
			return null;
		}
		long gap = buy ? market - price : price - market;
		if (gap <= 0 || (!stalled && gap * 100 < price * BIG_GAP_PERCENT))
		{
			return null;
		}
		if (buy)
		{
			// What you could sell for once it fills: if paying the market price leaves nothing, don't chase it
			boolean sellKnown = latest.high > 0 && nowSec - latest.highTime <= MAX_TRADE_AGE_SEC;
			if (sellKnown && latest.high - Gp.taxPerItem(latest.high) - market <= 0)
			{
				return new Advice(market, String.format(
					"Sellers are at %,d now, which leaves no profit (buyers pay %,d). Consider cancelling", market, latest.high),
					true, true);
			}
			return new Advice(market, String.format("Raise to %,d (+%,d each)", market, gap), false, false);
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
		return new Advice(market, String.format("Lower to %,d (-%,d each)%s", market, gap, extra), loss, false);
	}
}
