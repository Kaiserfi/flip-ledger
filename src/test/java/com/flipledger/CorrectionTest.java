package com.flipledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CorrectionTest
{
	private static final long NOW = 1_000_000;

	private static PriceService.Latest latest(long high, long ageSec)
	{
		PriceService.Latest l = new PriceService.Latest();
		l.high = high;
		l.highTime = NOW - ageSec;
		return l;
	}

	@Test
	public void stalledBuyBelowMarketIsRaised()
	{
		Correction.Advice a = Correction.advise(true, 1000, 10, latest(1010, 60), -1, true, NOW);
		assertNotNull(a);
		assertEquals(1010, a.price);
	}

	@Test
	public void freshBuyOnlyFlaggedWhenGapIsBig()
	{
		assertNull(Correction.advise(true, 1000, 10, latest(1010, 60), -1, false, NOW)); // 1%
		assertNotNull(Correction.advise(true, 1000, 10, latest(1030, 60), -1, false, NOW)); // 3%
	}

	@Test
	public void buyAtOrAboveMarketIsFine()
	{
		assertNull(Correction.advise(true, 1000, 10, latest(990, 60), -1, true, NOW));
	}

	@Test
	public void stalledSellAboveMarketIsLowered()
	{
		Correction.Advice a = Correction.advise(false, 1100, 10, latest(1000, 60), 800, true, NOW);
		assertNotNull(a);
		assertEquals(1000, a.price);
		assertTrue(!a.loss);
	}

	@Test
	public void sellCorrectionBelowCostIsMarkedAsLoss()
	{
		Correction.Advice a = Correction.advise(false, 1100, 10, latest(1000, 60), 1200, true, NOW);
		assertNotNull(a);
		assertTrue(a.loss);
	}

	@Test
	public void staleOrMissingPricesGiveNoAdvice()
	{
		assertNull(Correction.advise(true, 1000, 10, null, -1, true, NOW));
		assertNull(Correction.advise(true, 1000, 10, latest(1100, 3600), -1, true, NOW));
		assertNull(Correction.advise(true, 1000, 0, latest(1100, 60), -1, true, NOW));
	}
}
