package com.flipledger;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;

/**
 * Draws the game's own coin sprite. Asking for a quantity gives the matching pile
 * (a few coins, a stack, a big heap), so bigger profits get a bigger pile.
 */
final class Coins
{
	private static final int COINS = 995;

	private Coins()
	{
	}

	/** Draw the pile for {@code amount} just left of text whose baseline is at (textX, baseline). */
	static void draw(Graphics2D g, ItemManager items, long amount, int textX, int baseline, int fontSize)
	{
		BufferedImage img = items.getImage(COINS, (int) Math.min(Integer.MAX_VALUE, Math.max(1, amount)), false);
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
