package com.flipledger;

import java.awt.Color;
import java.text.DecimalFormat;

final class Gp
{
	static final Color PROFIT = new Color(0x2ECC71);
	static final Color LOSS = new Color(0xE74C3C);

	private static final DecimalFormat ONE = new DecimalFormat("0.0");
	private static final DecimalFormat TWO = new DecimalFormat("0.00");

	private Gp()
	{
	}

	/** GE tax on a single item sold at this price. */
	static long taxPerItem(long price)
	{
		if (price < 50)
		{
			return 0;
		}
		return Math.min(price * 2 / 100, 5_000_000L);
	}

	/** Short form like 65.2k, 1.25M, 2.10B. */
	static String shortGp(long n)
	{
		long a = Math.abs(n);
		String s;
		if (a >= 1_000_000_000L)
		{
			s = TWO.format(a / 1e9) + "B";
		}
		else if (a >= 10_000_000L)
		{
			s = ONE.format(a / 1e6) + "M";
		}
		else if (a >= 1_000_000L)
		{
			s = TWO.format(a / 1e6) + "M";
		}
		else if (a >= 100_000L)
		{
			s = Math.round(a / 1e3) + "k";
		}
		else if (a >= 10_000L)
		{
			s = ONE.format(a / 1e3) + "k";
		}
		else
		{
			s = String.format("%,d", a);
		}
		return (n < 0 ? "-" : "") + s;
	}

	/** Signed form like +65.2k gp / -12k gp. */
	static String signed(long n)
	{
		return (n >= 0 ? "+" : "") + shortGp(n) + " gp";
	}

	static Color color(long n)
	{
		return n >= 0 ? PROFIT : LOSS;
	}

	/** A time later today (or tomorrow) as "in 2h 10m (9:42 PM)". */
	static String clock(long epochMs)
	{
		long mins = Math.max(0, (epochMs - System.currentTimeMillis() + 59_999) / 60_000);
		String at = new java.text.SimpleDateFormat("h:mm a").format(new java.util.Date(epochMs));
		return (mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m" : mins + "m") + " (" + at + ")";
	}
}
