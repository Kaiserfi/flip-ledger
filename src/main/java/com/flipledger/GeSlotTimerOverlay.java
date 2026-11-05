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

	void clear()
	{
		offers = Collections.emptyList();
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		List<OfferView> list = offers;
		if (list.isEmpty() || !config.geSlotTimers())
		{
			return null;
		}
		Font font = FontManager.getRunescapeSmallFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		long now = System.currentTimeMillis();

		for (OfferView v : list)
		{
			if (v.status != OfferView.Status.ACTIVE || v.placedAt <= 0)
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
			int y = b.y + fm.getAscent() + 3;
			draw(g, fm, Gp.age(now - v.placedAt), b, y, v.stalled ? STALLED : WAITING);
			if (v.advice != null)
			{
				String s = (v.buy ? "Raise " : "Lower ") + Gp.shortGp(v.advice.price);
				draw(g, fm, s, b, y + fm.getHeight(), v.advice.loss ? LOSS : STALLED);
			}
		}
		return null;
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
