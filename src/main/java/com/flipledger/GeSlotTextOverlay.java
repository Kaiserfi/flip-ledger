package com.flipledger;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Floats text up over the matching Grand Exchange offer slot each time it fills:
 * profit for sales, quantity for buys.
 */
class GeSlotTextOverlay extends Overlay
{
	static final Color BOUGHT = new Color(0xF1C40F);
	static final Color NEUTRAL = new Color(0xBDC3C7);

	private static final long MERGE_WINDOW_MS = 1500;
	private static final long DURATION_MS = 2500;
	private static final int RISE_PIXELS = 40;

	private final Client client;
	private final FlipLedgerConfig config;
	private final List<Entry> entries = new CopyOnWriteArrayList<>();

	private static class Entry
	{
		final int slot;
		final Kind kind;
		long amount;
		long start;

		Entry(int slot, Kind kind, long amount, long start)
		{
			this.slot = slot;
			this.kind = kind;
			this.amount = amount;
			this.start = start;
		}
	}

	enum Kind
	{
		PROFIT,
		BOUGHT,
		SOLD_UNTRACKED
	}

	@Inject
	GeSlotTextOverlay(Client client, FlipLedgerConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	void show(int slot, Kind kind, long amount)
	{
		long now = System.currentTimeMillis();
		for (Entry e : entries)
		{
			if (e.slot == slot && e.kind == kind && now - e.start < MERGE_WINDOW_MS)
			{
				e.amount += amount;
				e.start = now;
				return;
			}
		}
		entries.add(new Entry(slot, kind, amount, now));
	}

	void clear()
	{
		entries.clear();
	}

	private static String text(Entry e)
	{
		switch (e.kind)
		{
			case BOUGHT:
				return String.format("+%,d bought", e.amount);
			case SOLD_UNTRACKED:
				return String.format("%,d sold", e.amount);
			default:
				return Gp.signed(e.amount);
		}
	}

	private static Color color(Entry e)
	{
		switch (e.kind)
		{
			case BOUGHT:
				return BOUGHT;
			case SOLD_UNTRACKED:
				return NEUTRAL;
			default:
				return Gp.color(e.amount);
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (entries.isEmpty())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		entries.removeIf(e -> now - e.start > DURATION_MS);
		if (!config.showOnGeSlots())
		{
			return null;
		}

		Font font = FontManager.getRunescapeBoldFont().deriveFont(16f);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		for (Entry e : entries)
		{
			Widget w = client.getWidget(InterfaceID.GeOffers.INDEX_0 + e.slot);
			if (w == null || w.isHidden())
			{
				continue; // GE closed, or an offer's detail screen is open
			}
			Rectangle b = w.getBounds();
			if (b == null || b.width <= 0)
			{
				continue;
			}

			String s = text(e);
			float progress = (float) (now - e.start) / DURATION_MS;
			int x = b.x + (b.width - fm.stringWidth(s)) / 2;
			int y = b.y + b.height / 2 + fm.getAscent() / 2 - Math.round(progress * RISE_PIXELS);

			float alpha = progress < 0.6f ? 1f : Math.max(0f, 1f - (progress - 0.6f) / 0.4f);
			Composite old = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			g.setColor(Color.BLACK);
			g.drawString(s, x + 1, y + 1);
			g.setColor(color(e));
			g.drawString(s, x, y);
			g.setComposite(old);
		}
		return null;
	}
}
