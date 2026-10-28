package com.flipledger;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws rising, fading "+65.2k gp" text above the local player.
 */
class FloatingTextOverlay extends Overlay
{
	private static final long MERGE_WINDOW_MS = 1500;
	private static final int RISE_PIXELS = 45;

	private final Client client;
	private final FlipLedgerConfig config;
	private final List<Entry> entries = new CopyOnWriteArrayList<>();

	private static class Entry
	{
		final int itemId;
		long amount;
		long start;

		Entry(int itemId, long amount, long start)
		{
			this.itemId = itemId;
			this.amount = amount;
			this.start = start;
		}
	}

	@Inject
	FloatingTextOverlay(Client client, FlipLedgerConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Called on the client thread when a sale completes. Partial fills of the same item merge into one popup. */
	void show(int itemId, long amount)
	{
		long now = System.currentTimeMillis();
		for (Entry e : entries)
		{
			if (e.itemId == itemId && now - e.start < MERGE_WINDOW_MS)
			{
				e.amount += amount;
				e.start = now;
				return;
			}
		}
		entries.add(new Entry(itemId, amount, now));
	}

	void clear()
	{
		entries.clear();
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (entries.isEmpty())
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		long now = System.currentTimeMillis();
		long duration = config.floatingSeconds() * 1000L;
		Font font = FontManager.getRunescapeBoldFont().deriveFont((float) config.floatingFontSize());
		g.setFont(font);

		int stack = 0;
		for (Entry e : entries)
		{
			long elapsed = now - e.start;
			if (elapsed > duration)
			{
				entries.remove(e);
				continue;
			}

			String text = Gp.signed(e.amount);
			Point base = player.getCanvasTextLocation(g, text, player.getLogicalHeight() + 40);
			if (base == null)
			{
				continue;
			}

			float progress = (float) elapsed / duration;
			int x = base.getX();
			int y = base.getY() - Math.round(progress * RISE_PIXELS) - stack * (font.getSize() + 4);

			// Fade out over the last 40%
			float alpha = progress < 0.6f ? 1f : Math.max(0f, 1f - (progress - 0.6f) / 0.4f);
			Composite old = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

			g.setColor(Color.BLACK);
			g.drawString(text, x + 1, y + 1);
			g.setColor(Gp.color(e.amount));
			g.drawString(text, x, y);

			g.setComposite(old);
			stack++;
		}
		return null;
	}
}
