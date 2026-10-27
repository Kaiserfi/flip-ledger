package com.flipledger;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * A panel that always matches the sidebar's width inside a scroll pane, so text wraps
 * or shortens instead of running off the right edge.
 */
class WidthPanel extends JPanel implements Scrollable
{
	WidthPanel(LayoutManager layout)
	{
		super(layout);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
	{
		return visible.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}
}
