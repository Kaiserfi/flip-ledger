package com.flipledger;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;

/**
 * Draws the game's own coin sprite. Asking for a quantity gives the matching pile
 * (a few coins, a stack, a big heap), so bigger profits get a bigger pile, exactly like in the inventory.
 */
final class Coins
{
	private static final int COINS = 995;

	private Coins()
	{
	}

	/** The game's coin piles change at 1, 2, 3, 4, 5, 25, 100, 250, 1,000 and 10,000: use the pile's own amount. */
	static int pile(long amount)
	{
		int[] steps = {10_000, 1_000, 250, 100, 25, 5, 4, 3, 2};
		for (int s : steps)
		{
			if (amount >= s)
			{
				return s;
			}
		}
		return 1;
	}

	/** Draw the pile for {@code amount} just left of text whose baseline is at (textX, baseline). */
	static void draw(Graphics2D g, ItemManager items, long amount, int textX, int baseline, int fontSize)
	{
		BufferedImage img = items.getImage(COINS, pile(amount), false);
		if (img == null || img.getWidth() <= 1)
		{
			return; // still loading; it appears on the next frame
		}
		int h = Math.round(fontSize * 1.5f);
		int w = Math.round(img.getWidth() * (h / (float) img.getHeight()));
		Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, textX - w - 2, baseline - Math.round(fontSize * 1.05f), w, h, null);
		if (hint != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		}
	}
}
