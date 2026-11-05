package com.flipledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CorrectionTest
{
	private static final long NOW = 1_000_000;

	/** high = what instant-buyers paid, low = what instant-sellers got; both traded {@code ageSec} ago. */
	private static PriceService.Latest latest(long high, long low, long ageSec)
	{
		PriceService.Latest l = new PriceService.Latest();
		l.high = high;
		l.highTime = NOW - ageSec;
		l.low = low;
		l.lowTime = NOW - ageSec;
		return l;
	}

	@Test
	public void stalledBuyBelowWhatSellersGetIsRaisedToThatPrice()
	{
		Correction.Advice a = Correction.advise(true, 940, 10, latest(981, 950, 60), -1, true, NOW);
		assertNotNull(a);
		assertEquals(950, a.price); // not 981, which is where you'd sell
		assertFalse(a.cancel);
	}

	@Test
	public void buyAtTheMarketIsLeftAlone()
	{
		// Mahogany repair kit: bid 950, sellers got 950, buyers pay 981
		assertNull(Correction.advise(true, 950, 10, latest(981, 950, 60), -1, true, NOW));
	}

	@Test
	public void buyIsNotChasedWhenFillingLeavesNoProfit()
	{
		Correction.Advice a = Correction.advise(true, 80, 10, latest(100, 99, 60), -1, true, NOW);
		assertNotNull(a);
		assertTrue(a.cancel);
		assertTrue(a.loss);
	}

	@Test
	public void freshBuyOnlyFlaggedWhenGapIsBig()
	{
		assertNull(Correction.advise(true, 1000, 10, latest(1100, 1010, 60), -1, false, NOW)); // 1%
		assertNotNull(Correction.advise(true, 1000, 10, latest(1100, 1030, 60), -1, false, NOW)); // 3%
	}

	@Test
	public void stalledSellAboveWhatBuyersPayIsLowered()
	{
		Correction.Advice a = Correction.advise(false, 1100, 10, latest(1000, 950, 60), 800, true, NOW);
		assertNotNull(a);
		assertEquals(1000, a.price);
		assertFalse(a.loss);
	}

	@Test
	public void sellBelowMarketIsFine()
	{
		assertNull(Correction.advise(false, 900, 10, latest(1000, 950, 60), 800, true, NOW));
	}

	@Test
	public void sellCorrectionBelowCostIsMarkedAsLoss()
	{
		Correction.Advice a = Correction.advise(false, 1100, 10, latest(1000, 950, 60), 1200, true, NOW);
		assertNotNull(a);
		assertTrue(a.loss);
		assertFalse(a.cancel);
	}

	@Test
	public void staleOrMissingPricesGiveNoAdvice()
	{
		assertNull(Correction.advise(true, 1000, 10, null, -1, true, NOW));
		assertNull(Correction.advise(true, 1000, 10, latest(1200, 1100, 3600), -1, true, NOW));
		assertNull(Correction.advise(true, 1000, 0, latest(1200, 1100, 60), -1, true, NOW));
	}
}
