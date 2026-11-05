package com.flipledger;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws how long each active offer has waited in the top-right corner of its Grand Exchange slot,
 * with the suggested corrected price underneath when the offer is priced away from the market.
 */
class GeSlotTimerOverlay extends Overlay
{
	private static final Color WAITING = new Color(0xECF0F1);
	private static final Color STALLED = new Color(0xE67E22);
	private static final Color LOSS = new Color(0xE74C3C);

	private final Client client;
	private final FlipLedgerConfig config;
	private volatile List<OfferView> offers = Collections.emptyList();
	/** Per slot: when it became empty (ms), or 0 if it's in use. */
	private volatile long[] emptySince = new long[8];
	private static final long IDLE_SHOW_AFTER_MS = 60_000;
	private static final long IDLE_URGENT_MS = 3 * 60_000;

	@Inject
	GeSlotTimerOverlay(Client client, FlipLedgerConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	void update(List<OfferView> views)
	{
		offers = views == null ? Collections.emptyList() : views;
	}

	void updateIdle(long[] since)
	{
		emptySince = since == null ? new long[8] : since;
	}

	void clear()
	{
		offers = Collections.emptyList();
		emptySince = new long[8];
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		List<OfferView> list = offers;
		boolean timers = config.geSlotTimers();
		boolean profits = config.geSlotProfit();
		long[] idle = emptySince;
		boolean idleOn = config.idleReminders();
		if ((list.isEmpty() || !(timers || profits)) && !idleOn)
		{
			return null;
		}
		Font font = FontManager.getRunescapeSmallFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		long now = System.currentTimeMillis();
		if (idleOn)
		{
			for (int i = 0; i < idle.length; i++)
			{
				if (idle[i] <= 0 || now - idle[i] < IDLE_SHOW_AFTER_MS)
				{
					continue;
				}
				Widget w = client.getWidget(InterfaceID.GeOffers.INDEX_0 + i);
				if (w == null || w.isHidden() || w.getBounds() == null || w.getBounds().width <= 0)
				{
					continue;
				}
				long idleFor = now - idle[i];
				draw(g, fm, "Idle " + Gp.age(idleFor), w.getBounds(), w.getBounds().y + fm.getAscent() + 3,
					idleFor >= IDLE_URGENT_MS ? STALLED : WAITING);
			}
		}

		Font osrs = FontManager.getRunescapeFont();
		for (OfferView v : list)
		{
			if (v.status != OfferView.Status.ACTIVE)
			{
				continue;
			}
			Widget w = client.getWidget(InterfaceID.GeOffers.INDEX_0 + v.slot);
			if (w == null || w.isHidden())
			{
				continue; // GE closed, or an offer's detail screen is open
			}
			Rectangle b = w.getBounds();
			if (b == null || b.width <= 0)
			{
				continue;
			}
			if (timers && v.placedAt > 0)
			{
				int y = b.y + fm.getAscent() + 3;
				draw(g, fm, Gp.age(now - v.placedAt), b, y, v.stalled ? STALLED : WAITING);
				if (v.advice != null)
				{
					String s = v.advice.stop ? (v.buy ? "Cancel?" : "Hold") : (v.buy ? "Raise " : "Lower ") + Gp.shortGp(v.advice.price);
					draw(g, fm, s, b, y + fm.getHeight(), v.advice.loss ? LOSS : STALLED);
				}
			}
			if (profits && !v.buy && v.avgCost >= 0)
			{
				drawProfit(g, osrs, b, v.estProfit());
			}
		}
		return null;
	}

	/** Expected profit of a sell: green +N or red -N, centred on the offer's progress bar. */
	private static void drawProfit(Graphics2D g, Font osrs, Rectangle b, long profit)
	{
		g.setFont(osrs);
		FontMetrics fm = g.getFontMetrics();
		String s = (profit >= 0 ? "+" : "") + Gp.shortGp(profit);
		int x = b.x + (b.width - fm.stringWidth(s)) / 2;
		// The progress bar sits about three quarters of the way down the slot
		int y = b.y + Math.round(b.height * 0.74f) + fm.getAscent() / 2 - 1;
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(profit >= 0 ? Gp.PROFIT : Gp.LOSS);
		g.drawString(s, x, y);
		g.setFont(FontManager.getRunescapeSmallFont());
	}

	private static void draw(Graphics2D g, FontMetrics fm, String s, Rectangle b, int y, Color c)
	{
		int x = b.x + b.width - fm.stringWidth(s) - 6;
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}
}
