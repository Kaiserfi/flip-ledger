package com.flipledger;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** Today's profit against the daily goal: a progress bar with the numbers on it. Hidden when there is no goal. */
class GoalBar extends JPanel
{
	private static final Color TRACK = new Color(0x3A3A3A);
	private static final Color REACHED = new Color(0xF1C40F);

	private long goal;
	private long today;

	GoalBar()
	{
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		setPreferredSize(new Dimension(10, 20));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		setVisible(false);
	}

	void set(long goal, long today)
	{
		this.goal = goal;
		this.today = today;
		setVisible(goal > 0);
		setToolTipText(goal > 0 ? String.format("<html>Daily goal %,d gp<br>Today %,d gp (flips and alching)</html>", goal, today) : null);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		super.paintComponent(g0);
		if (goal <= 0)
		{
			return;
		}
		Graphics2D g = (Graphics2D) g0;
		int w = getWidth();
		int h = getHeight();
		boolean reached = today >= goal;
		g.setColor(TRACK);
		g.fillRect(0, 0, w, h);
		double frac = Math.max(0, Math.min(1, today / (double) goal));
		g.setColor(reached ? REACHED : Gp.PROFIT.darker());
		g.fillRect(0, 0, (int) Math.round(w * frac), h);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		String s = reached
			? String.format("Goal reached: %s / %s", Gp.shortGp(today), Gp.shortGp(goal))
			: String.format("Today %s / %s  (%d%%)", Gp.shortGp(today), Gp.shortGp(goal), Math.max(0, Math.round(frac * 100)));
		int x = (w - fm.stringWidth(s)) / 2;
		int y = (h + fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(today < 0 ? Gp.LOSS : Color.WHITE);
		g.drawString(s, x, y);
	}
}
