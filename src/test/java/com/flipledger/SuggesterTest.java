package com.flipledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SuggesterTest
{
	private static List<Long> l(Long... v)
	{
		return Arrays.asList(v);
	}

	@Test
	public void amethystDartColumnsAreFlaggedUnsteady()
	{
		// The last 30 minutes from the Steady Flips site: sellers 173,173,176,169,170,170 / buyers 181,180,181,180,180,179
		Suggester.Stability s = Suggester.stability(l(173L, 173L, 176L, 169L, 170L, 170L), l(181L, 180L, 181L, 180L, 180L, 179L));
		assertEquals("bad", s.level);
	}

	@Test
	public void flatColumnsAreSteady()
	{
		assertEquals("ok", Suggester.stability(l(1000L, 1001L, 1000L, 1000L, 1001L, 1000L),
			l(1030L, 1031L, 1030L, 1030L, 1031L, 1030L)).level);
	}

	@Test
	public void cheapItemWobbleOfAFewGpIsSteady()
	{
		assertEquals("ok", Suggester.stability(l(20L, 21L, 20L, 21L, 20L, 20L), l(23L, 22L, 23L, 23L, 22L, 23L)).level);
	}

	@Test
	public void fallingBuyersAreFlagged()
	{
		// Buyers are your exit price: down 2% across the half hour
		Suggester.Stability s = Suggester.stability(l(1000L, 1000L, 1000L, 1000L, 1000L, 1000L),
			l(1050L, 1050L, 1040L, 1030L, 1030L, 1020L));
		assertEquals("bad", s.level);
	}

	@Test
	public void sellersDryingUpIsNotSteadyForABuyer()
	{
		assertEquals("warn", Suggester.stability(l(1000L, 1000L, 1000L, 1006L, 1010L, 1012L),
			l(1050L, 1050L, 1050L, 1050L, 1050L, 1050L)).level);
	}

	@Test
	public void noTradesOnOneSideIsAWarning()
	{
		assertEquals("warn", Suggester.stability(l(), l(100L, 100L)).level);
	}

	@Test
	public void queueJumpBidOnlyOnPricierItems()
	{
		assertEquals(0, Suggester.bidTick(170));
		assertEquals(0, Suggester.bidTick(999));
		assertEquals(1, Suggester.bidTick(1000));
		assertEquals(150, Suggester.bidTick(150_000));
		assertEquals(1, Suggester.tick(170));
	}

	@Test
	public void oddTradeIsReportedWhenLatestIsFarFromTypical()
	{
		PriceService.Latest l = new PriceService.Latest();
		l.high = 173; // latest buyer, typical 180
		l.low = 171;  // latest seller, typical 172
		String odd = Suggester.odd(l, 172, 180);
		assertNotNull(odd);
		assertTrue(odd, odd.contains("latest buyer 173 vs typical 180"));
		assertTrue(odd, !odd.contains("seller"));
	}

	@Test
	public void noOddTradeWhenLatestMatchesTypical()
	{
		PriceService.Latest l = new PriceService.Latest();
		l.high = 180;
		l.low = 172;
		assertNull(Suggester.odd(l, 172, 180));
		assertNull(Suggester.odd(null, 172, 180));
	}
}
