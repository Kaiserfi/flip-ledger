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
		assertFalse(a.stop);
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
		assertTrue(a.stop);
		assertTrue(a.loss);
	}

	@Test
	public void buyCorrectionShowsTheMarginLeft()
	{
		Correction.Advice a = Correction.advise(true, 940, 10, latest(981, 950, 60), -1, true, NOW);
		assertNotNull(a);
		assertTrue(a.text, a.text.contains("still +12 each after tax")); // 981 - 19 tax - 950
	}

	@Test
	public void freshBuyOnlyFlaggedWhenGapIsBig()
	{
		assertNull(Correction.advise(true, 1000, 10, latest(1100, 1010, 60), -1, false, NOW)); // 1%
		assertNotNull(Correction.advise(true, 1000, 10, latest(1100, 1030, 60), -1, false, NOW)); // 3%
	}

	@Test
	public void stalledSellAboveWhatBuyersPayIsLoweredWhileStillProfitable()
	{
		Correction.Advice a = Correction.advise(false, 1100, 10, latest(1000, 950, 60), 800, true, NOW);
		assertNotNull(a);
		assertEquals(1000, a.price);
		assertFalse(a.loss);
		assertFalse(a.stop);
		assertTrue(a.text, a.text.contains("+180 each"));
	}

	@Test
	public void sellBelowMarketIsFine()
	{
		assertNull(Correction.advise(false, 900, 10, latest(1000, 950, 60), 800, true, NOW));
	}

	@Test
	public void sellIsHeldWhenLoweringWouldLoseMoney()
	{
		// Raw pyre fox: bought at 832, asking 849, buyers pay 832
		Correction.Advice a = Correction.advise(false, 849, 208, latest(832, 800, 60), 832, true, NOW);
		assertNotNull(a);
		assertTrue(a.stop);
		assertTrue(a.loss);
		assertTrue(a.text, a.text.startsWith("Hold"));
	}

	@Test
	public void sellIsHeldWhenLoweringWouldEarnNothing()
	{
		// Adamant arrow: bought at 20, asking 21, buyers pay 20 (no tax under 50 gp)
		Correction.Advice a = Correction.advise(false, 21, 11000, latest(20, 19, 60), 20, true, NOW);
		assertNotNull(a);
		assertTrue(a.stop);
		assertTrue(a.text, a.text.contains("earns nothing"));
	}

	@Test
	public void sellWithUnknownCostIsStillLowered()
	{
		Correction.Advice a = Correction.advise(false, 1100, 10, latest(1000, 950, 60), -1, true, NOW);
		assertNotNull(a);
		assertEquals(1000, a.price);
		assertFalse(a.stop);
	}

	@Test
	public void staleOrMissingPricesGiveNoAdvice()
	{
		assertNull(Correction.advise(true, 1000, 10, null, -1, true, NOW));
		assertNull(Correction.advise(true, 1000, 10, latest(1200, 1100, 3600), -1, true, NOW));
		assertNull(Correction.advise(true, 1000, 0, latest(1200, 1100, 60), -1, true, NOW));
	}
}
