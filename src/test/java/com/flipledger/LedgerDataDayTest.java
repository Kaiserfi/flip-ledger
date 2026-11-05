package com.flipledger;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LedgerDataDayTest
{
	@Test
	public void profitAddsUpWithinADay()
	{
		LedgerData d = new LedgerData();
		assertEquals(0, d.dayProfitToday());
		assertEquals(1_000, d.addDayProfit(1_000));
		assertEquals(400, d.addDayProfit(-600));
		assertEquals(400, d.dayProfitToday());
	}

	@Test
	public void aNewDayStartsFromZero()
	{
		LedgerData d = new LedgerData();
		d.dayKey = "2000-01-01";
		d.dayProfit = 999_999;
		assertEquals(0, d.dayProfitToday());
		assertEquals(250, d.addDayProfit(250));
		assertEquals(250, d.dayProfitToday());
	}
}
