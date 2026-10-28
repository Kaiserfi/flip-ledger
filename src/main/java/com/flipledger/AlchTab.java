package com.flipledger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Alch mode: progress toward a goal (a bond by default), this session's alching, and the best items to alch.
 */
class AlchTab extends JPanel
{
	private static final Color GOLD = new Color(0xF1C40F);
	private static final Color RED = new Color(0xE74C3C);

	/** Everything the tab shows, built by the plugin. */
	static class View
	{
		long goal;          // gp target
		boolean goalIsBond;
		long bondPrice;     // 0 if unknown
		long invCoins;
		long bankCoins = -1; // -1: bank not seen yet
		long bankSeenAt;
		long casts;
		long profit;
		long sessionMs;
		long allTimeProfit;
		long allTimeCasts;
		long natureRune;
		boolean members;
		int magicLevel;
		List<Alch.Pick> picks = Collections.emptyList();
		String lastName;    // null until the first cast
		long lastCoins, lastItemCost, lastRuneCost, lastProfit;
	}

	private final Consumer<String> onGoal;
	private final Consumer<Boolean> onFireStaff;
	private final Runnable onReset;

	private final JLabel goalText = new JLabel("-");
	private final JLabel coinsText = new JLabel();
	private final JLabel bondText = new JLabel();
	private double goalFrac;
	private final JLabel casts = value(), profit = value(), perHour = value(), allTime = value();
	private final JLabel runeText = new JLabel();
	private final JLabel lastTitle = new JLabel("Last cast");
	private final JPanel last = column();
	private final JLabel magicWarn = new JLabel();
	private final JLabel picksTitle = new JLabel("Best items to alch");
	private final JPanel picks = column();
	private final JTextField goalField = new JTextField();
	private final JCheckBox fireStaff = new JCheckBox("Using a fire staff");

	AlchTab(String goal, boolean staff, Consumer<String> onGoal, Consumer<Boolean> onFireStaff, Runnable onReset)
	{
		this.onGoal = onGoal;
		this.onFireStaff = onFireStaff;
		this.onReset = onReset;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		heading("Goal");
		JPanel bar = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				g.setColor(new Color(0x3A3A3A));
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(goalFrac >= 1 ? Gp.PROFIT : GOLD);
				g.fillRect(0, 0, (int) Math.round(getWidth() * Math.min(1, goalFrac)), getHeight());
			}
		};
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
		bar.setPreferredSize(new Dimension(10, 8));
		goalText.setForeground(Color.WHITE);
		goalText.setAlignmentX(Component.LEFT_ALIGNMENT);
		goalText.setBorder(new EmptyBorder(0, 0, 4, 0));
		add(goalText);
		add(bar);
		for (JLabel l : new JLabel[]{coinsText, bondText})
		{
			l.setForeground(Color.GRAY);
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(l);
		}

		JPanel goalRow = new JPanel(new BorderLayout(6, 0));
		goalRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		goalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		goalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		goalRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		JLabel gl = new JLabel("Target");
		gl.setForeground(Color.LIGHT_GRAY);
		goalField.setText(goal);
		goalField.setToolTipText("\"bond\" follows the live bond price, or type an amount like 10m");
		goalField.addActionListener(e -> onGoal.accept(goalField.getText().trim()));
		goalField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				onGoal.accept(goalField.getText().trim());
			}
		});
		goalRow.add(gl, BorderLayout.WEST);
		goalRow.add(goalField, BorderLayout.CENTER);
		add(goalRow);

		heading("This session");
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 2));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(key("Casts"));
		grid.add(casts);
		grid.add(key("Profit"));
		grid.add(profit);
		grid.add(key("Profit / hr"));
		grid.add(perHour);
		grid.add(key("All-time alch"));
		grid.add(allTime);
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		add(grid);
		JButton reset = new JButton("Reset session");
		reset.setAlignmentX(Component.LEFT_ALIGNMENT);
		reset.addActionListener(e -> onReset.run());
		add(Box.createVerticalStrut(4));
		add(reset);

		lastTitle.setForeground(Color.WHITE);
		lastTitle.setFont(FontManager.getRunescapeBoldFont());
		lastTitle.setBorder(new EmptyBorder(12, 0, 4, 0));
		lastTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(lastTitle);
		add(last);

		heading("Runes");
		runeText.setForeground(Color.LIGHT_GRAY);
		runeText.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(runeText);
		fireStaff.setSelected(staff);
		fireStaff.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fireStaff.setForeground(Color.LIGHT_GRAY);
		fireStaff.setAlignmentX(Component.LEFT_ALIGNMENT);
		fireStaff.setToolTipText("Without one, each cast also costs 5 fire runes");
		fireStaff.addActionListener(e -> onFireStaff.accept(fireStaff.isSelected()));
		add(fireStaff);
		magicWarn.setForeground(RED);
		magicWarn.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(magicWarn);

		picksTitle.setForeground(Color.WHITE);
		picksTitle.setFont(FontManager.getRunescapeBoldFont());
		picksTitle.setBorder(new EmptyBorder(12, 0, 4, 0));
		picksTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(picksTitle);
		add(picks);

		JLabel note = new JLabel("<html><body style='width:150px'>Profit is after the nature rune. Best per cast first: "
			+ "use up the top items' buy limits, then fill the rest of your casts further down. Casts are counted "
			+ "when an item turns into coins with a nature rune used.</body></html>");
		note.setForeground(Color.GRAY);
		note.setBorder(new EmptyBorder(8, 0, 0, 0));
		note.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(note);
	}

	void update(View v)
	{
		long have = v.invCoins + Math.max(0, v.bankCoins);
		goalFrac = v.goal > 0 ? have / (double) v.goal : 0;
		goalText.setText(String.format("%s / %s  (%d%%)", Gp.shortGp(have), Gp.shortGp(v.goal),
			Math.min(999, Math.round(goalFrac * 100))));
		coinsText.setText(v.bankCoins < 0
			? "<html>" + Gp.shortGp(v.invCoins) + " in inventory. Open your bank once to count it too.</html>"
			: Gp.shortGp(v.invCoins) + " inventory + " + Gp.shortGp(v.bankCoins) + " bank");
		bondText.setText(v.bondPrice > 0 ? "Bond price: " + Gp.shortGp(v.bondPrice) + (v.goalIsBond ? " (your target)" : "") : "");

		casts.setText(String.format("%,d", v.casts));
		profit.setText(Gp.signed(v.profit));
		profit.setForeground(Gp.color(v.profit));
		double hours = v.sessionMs / 3_600_000.0;
		long ph = hours > 0.02 ? Math.round(v.profit / hours) : 0;
		perHour.setText(Gp.shortGp(ph));
		perHour.setForeground(Gp.color(ph));
		allTime.setText(Gp.shortGp(v.allTimeProfit));
		allTime.setForeground(Gp.color(v.allTimeProfit));

		last.removeAll();
		if (v.lastName == null)
		{
			JLabel l = new JLabel("No casts yet this session");
			l.setForeground(Color.GRAY);
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			last.add(l);
			lastTitle.setText("Last cast");
		}
		else
		{
			lastTitle.setText("Last cast  " + Gp.signed(v.lastProfit));
			last.add(lastRow("Coins", v.lastCoins));
			last.add(lastRow(v.lastName, -v.lastItemCost));
			last.add(lastRow("Runes", -v.lastRuneCost));
		}
		last.revalidate();

		runeText.setText(v.natureRune > 0 ? "Nature rune: " + String.format("%,d", v.natureRune) + " gp" : "Nature rune: loading...");
		magicWarn.setText(v.magicLevel > 0 && v.magicLevel < Alch.MAGIC_LEVEL
			? "High Alchemy needs Magic 55 (you have " + v.magicLevel + ")" : "");

		picksTitle.setText("Best items to alch (" + (v.members ? "members" : "F2P") + ")");
		picks.removeAll();
		if (v.picks.isEmpty())
		{
			JLabel l = new JLabel("<html><body style='width:150px'>" + (v.natureRune > 0
				? "Nothing profitable right now. Prices update every 10 minutes."
				: "Waiting for prices...") + "</body></html>");
			l.setForeground(Color.GRAY);
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			picks.add(l);
		}
		for (Alch.Pick p : v.picks)
		{
			picks.add(pickRow(p));
			picks.add(Box.createVerticalStrut(2));
		}
		picks.revalidate();
		picks.repaint();
		repaint();
	}

	private JPanel pickRow(Alch.Pick p)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		JPanel left = column();
		left.setOpaque(false);
		JLabel name = new JLabel(p.name);
		name.setForeground(Color.WHITE);
		JLabel sub = new JLabel(String.format("buy ≤ %,d · %s/4h", p.buy, p.limit > 0 ? Gp.shortGp(p.limitLeft) : "no limit"));
		sub.setForeground(Color.GRAY);
		left.add(name);
		left.add(sub);
		JPanel right = column();
		right.setOpaque(false);
		JLabel each = new JLabel(Gp.signed(p.profit));
		each.setForeground(Gp.PROFIT);
		each.setHorizontalAlignment(JLabel.RIGHT);
		JLabel per = new JLabel(Gp.shortGp(p.perLimit));
		per.setForeground(Color.LIGHT_GRAY);
		per.setHorizontalAlignment(JLabel.RIGHT);
		each.setAlignmentX(Component.RIGHT_ALIGNMENT);
		per.setAlignmentX(Component.RIGHT_ALIGNMENT);
		right.add(each);
		right.add(per);
		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		row.setToolTipText(String.format("<html>%s<br>Buy at or below %,d<br>High Alch gives %,d<br>Profit %s each, %s per 4-hour limit"
				+ "<br>%,d trade an hour</html>", p.name.replace("<", "&lt;"), p.buy, p.highAlch, Gp.signed(p.profit),
			Gp.shortGp(p.perLimit), p.volume));
		return row;
	}

	private static JPanel lastRow(String label, long amount)
	{
		JPanel r = new JPanel(new BorderLayout(4, 0));
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		r.setBorder(new EmptyBorder(2, 6, 2, 6));
		r.setAlignmentX(Component.LEFT_ALIGNMENT);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		JLabel l = new JLabel(label); // plain label: long names end in "..."
		l.setForeground(Color.LIGHT_GRAY);
		JLabel a = new JLabel(Gp.signed(amount));
		a.setForeground(Gp.color(amount));
		r.add(l, BorderLayout.CENTER);
		r.add(a, BorderLayout.EAST);
		return r;
	}

	private void heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(Color.WHITE);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setBorder(new EmptyBorder(12, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(l);
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel key(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(Color.GRAY);
		return l;
	}

	private static JLabel value()
	{
		JLabel l = new JLabel("-");
		l.setForeground(Color.WHITE);
		l.setHorizontalAlignment(JLabel.RIGHT);
		return l;
	}
}
