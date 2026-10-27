package com.flipledger;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shown while you're setting up a GE offer: recommended prices for the item you picked.
 * Display only: you type the price yourself. Drag it anywhere (Alt + drag).
 */
class PriceCheckOverlay extends OverlayPanel
{
	static class Line
	{
		final String left;
		final String right;
		final Color color;

		Line(String left, String right, Color color)
		{
			this.left = left;
			this.right = right;
			this.color = color;
		}
	}

	private volatile String title;
	private volatile List<Line> lines = Collections.emptyList();

	@Inject
	PriceCheckOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(net.runelite.client.ui.overlay.OverlayLayer.ABOVE_WIDGETS); // stay on top of the GE window
	}

	void show(String title, List<Line> lines)
	{
		this.title = title;
		this.lines = lines;
	}

	void hide()
	{
		this.title = null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String t = title;
		if (t == null)
		{
			return null;
		}
		panelComponent.setPreferredSize(new Dimension(235, 0));
		panelComponent.getChildren().add(TitleComponent.builder().text(t).color(Color.WHITE).build());
		for (Line l : lines)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(l.left).right(l.right).rightColor(l.color == null ? Color.WHITE : l.color).build());
		}
		return super.render(g);
	}
}
