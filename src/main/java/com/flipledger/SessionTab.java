package com.flipledger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Session profit, current GE offers, suggested flips and one live entry per flip.
 */
class SessionTab extends JPanel
{
	private static final int SHOWN_FLIPS = 40;
	private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm");
	private static final Color STEADY = new Color(0x2ECC71);
	private static final Color SETTLED = new Color(0xF1C40F);
	private static final Color RISKY = new Color(0xE74C3C);

	private final JLabel sessionProfit = new JLabel("+0 gp");
	private final JLabel gpHour = value();
	private final JLabel sessionFlips = value();
	private final JLabel sessionTime = value();
	private final JLabel allTime = value();
	private final JPanel offers = section();
	private final JPanel limitsList = section();
	private final JPanel suggestions = section();
	private final JLabel suggestionStatus = new JLabel(" ");
	private final JPanel flips = section();

	private static final long SKIP_MS = 30 * 60_000L;

	private List<OfferView> lastOffers = Collections.emptyList();
	private List<Suggester.Suggestion> lastSuggestions = Collections.emptyList();
	private final Map<Integer, Long> skipped = new HashMap<>();
	private final IntConsumer onBlock;
	private LedgerData lastData;

	SessionTab(Runnable onReset, Runnable onClear, Runnable onRefreshSuggestions, IntConsumer onBlock)
	{
		this.onBlock = onBlock;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Session profit", JLabel.CENTER);
		title.setForeground(Color.LIGHT_GRAY);
		sessionProfit.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
		sessionProfit.setHorizontalAlignment(JLabel.CENTER);
		JPanel head = new JPanel(new GridLayout(2, 1));
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		head.add(title);
		head.add(sessionProfit);
		add(head);
		add(Box.createVerticalStrut(8));

		JPanel stats = new JPanel(new GridLayout(0, 2, 4, 4));
		stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stats.setBorder(new EmptyBorder(8, 8, 8, 8));
		stats.add(key("GP/hr"));
		stats.add(gpHour);
		stats.add(key("Flips done"));
		stats.add(sessionFlips);
		stats.add(key("Session time"));
		stats.add(sessionTime);
		stats.add(key("All-time profit"));
		stats.add(allTime);
		stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(stats);
		add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton reset = new JButton("Reset");
		reset.setToolTipText("Start a new session (keeps all-time profit and history)");
		reset.addActionListener(e -> onReset.run());
		JButton clear = new JButton("Clear all");
		clear.setToolTipText("Clear all-time profit and flip history for this account");
		clear.addActionListener(e ->
		{
			if (JOptionPane.showConfirmDialog(this, "Clear all-time profit and flip history for this account?",
				"Flip Ledger", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				onClear.run();
			}
		});
		buttons.add(reset);
		buttons.add(clear);
		add(buttons);

		JPanel sugHead = new JPanel(new BorderLayout());
		sugHead.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sugHead.setBorder(new EmptyBorder(12, 0, 4, 0));
		sugHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		sugHead.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel sl = new JLabel("Suggested flip");
		sl.setForeground(Color.WHITE);
		sl.setFont(FontManager.getRunescapeBoldFont());
		JButton refresh = new JButton("Refresh");
		refresh.setToolTipText("Re-check prices (limited to once a minute to go easy on the Wiki)");
		refresh.addActionListener(e -> onRefreshSuggestions.run());
		sugHead.add(sl, BorderLayout.WEST);
		sugHead.add(refresh, BorderLayout.EAST);
		add(sugHead);
		suggestionStatus.setForeground(Color.GRAY);
		suggestionStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(suggestionStatus);
		add(Box.createVerticalStrut(3));
		add(suggestions);



		heading("Flips");
		add(flips);
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

	private static JPanel section()
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

	private static JLabel line(String text, Color c)
	{
		JLabel l = new JLabel(text);
		l.setForeground(c);
		return l;
	}

	private static JPanel card(Color edge)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		r.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, edge),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		r.setAlignmentX(Component.LEFT_ALIGNMENT);
		return r;
	}

	private static void finish(JPanel section, JPanel card, int lines)
	{
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10 + lines * 18));
		section.add(card);
		section.add(Box.createVerticalStrut(3));
	}

	private static void empty(JPanel section, String text)
	{
		JLabel l = new JLabel("<html><body style='width:150px'>" + text + "</body></html>");
		l.setForeground(Color.GRAY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(l);
	}

	// ---------- stats + flips ----------

	void update(long profit, int flipsDone, long sessionMs, LedgerData data)
	{
		sessionProfit.setText(Gp.signed(profit));
		sessionProfit.setForeground(Gp.color(profit));
		double hours = sessionMs / 3_600_000.0;
		long perHour = hours > 0.02 ? Math.round(profit / hours) : 0;
		gpHour.setText(Gp.shortGp(perHour));
		gpHour.setForeground(Gp.color(perHour));
		sessionFlips.setText(String.valueOf(flipsDone));
		long mins = sessionMs / 60_000;
		sessionTime.setText(String.format("%d:%02d", mins / 60, mins % 60));
		if (data == null)
		{
			allTime.setText("log in");
			allTime.setForeground(Color.GRAY);
		}
		else
		{
			allTime.setText(Gp.shortGp(data.allTimeProfit));
			allTime.setForeground(Gp.color(data.allTimeProfit));
		}
		lastData = data;
		renderFlips();
		renderLimits();
	}

	/** Items bought in the last 4 hours: how much of the GE buy limit is used and when it resets. */
	private void renderLimits()
	{
		limitsList.removeAll();
		List<LedgerData.LimitWindow> list = lastData == null ? Collections.emptyList()
			: lastData.activeLimits(System.currentTimeMillis());
		if (list.isEmpty())
		{
			empty(limitsList, "Nothing bought in the last 4 hours");
		}
		for (LedgerData.LimitWindow w : list)
		{
			long reset = w.start + LedgerData.LIMIT_WINDOW_MS;
			boolean full = w.limit > 0 && w.bought >= w.limit;
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(3, 6, 3, 6));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			JLabel name = new JLabel(w.name == null ? "Item" : w.name);
			name.setForeground(Color.WHITE);
			JLabel amt = new JLabel(w.limit > 0
				? Gp.shortGp(w.bought) + "/" + Gp.shortGp(w.limit) : Gp.shortGp(w.bought));
			amt.setForeground(full ? RISKY : Color.LIGHT_GRAY);
			row.add(name, BorderLayout.CENTER);
			row.add(amt, BorderLayout.EAST);
			row.setToolTipText(String.format("<html>%s<br>Bought %,d%s<br>Resets in %s</html>",
				(w.name == null ? "Item" : w.name).replace("<", "&lt;"), w.bought,
				w.limit > 0 ? String.format(" of %,d (%,d left)", w.limit, Math.max(0, w.limit - w.bought)) : "",
				Gp.clock(reset)));
			limitsList.add(row);
			limitsList.add(Box.createVerticalStrut(2));
		}
		limitsList.revalidate();
		limitsList.repaint();
	}

	private void renderFlips()
	{
		flips.removeAll();
		LedgerData data = lastData;
		if (data == null || data.history.isEmpty())
		{
			empty(flips, data == null ? "Log in to see your flips." : "No flips yet. Buy something on the GE and it shows up here.");
		}
		else
		{
			int shown = 0;
			for (LedgerData.Flip f : new ArrayList<>(data.history))
			{
				if (f.qty <= 0)
				{
					continue; // nothing sold yet: it's on the Offers tab
				}
				flipRow(f);
				if (++shown >= SHOWN_FLIPS)
				{
					break;
				}
			}
			if (shown == 0)
			{
				empty(flips, "No sales yet. Completed flips show up here.");
			}
		}
		flips.revalidate();
		flips.repaint();
	}

	/** One line per flip: "1,000 x Item" and a green + or red - profit. Details on hover. */
	private void flipRow(LedgerData.Flip f)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel name = new JLabel(String.format("%s x %s", Gp.shortGp(f.qty), f.name));
		name.setForeground(f.open ? Color.LIGHT_GRAY : Color.WHITE);
		JLabel p = new JLabel(Gp.signed(f.profit));
		p.setForeground(Gp.color(f.profit));
		row.add(name, BorderLayout.CENTER);
		row.add(p, BorderLayout.EAST);
		StringBuilder tip = new StringBuilder("<html>").append(f.name.replace("<", "&lt;"));
		if (f.boughtQty > 0)
		{
			tip.append(String.format("<br>Bought %,d @ %,d", f.boughtQty, f.avgBuy()));
		}
		tip.append(String.format("<br>Sold %,d &middot; %s", f.qty, TIME.format(new Date(f.time))));
		if (f.open)
		{
			tip.append("<br><i>Still selling</i>");
		}
		row.setToolTipText(tip.append("</html>").toString());
		flips.add(row);
		flips.add(Box.createVerticalStrut(2));
	}

	/** The Offers tab: current GE offers with progress bars. */
	JPanel offersView()
	{
		JPanel p = section();
		JLabel h = new JLabel("Current offers");
		h.setForeground(Color.WHITE);
		h.setFont(FontManager.getRunescapeBoldFont());
		h.setBorder(new EmptyBorder(4, 0, 6, 0));
		h.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(h);
		p.add(offers);
		JLabel lh = new JLabel("Buy limits (4 hours)");
		lh.setForeground(Color.WHITE);
		lh.setFont(FontManager.getRunescapeBoldFont());
		lh.setBorder(new EmptyBorder(12, 0, 6, 0));
		lh.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(lh);
		p.add(limitsList);
		renderLimits();
		JLabel tip = new JLabel("<html><body style='width:150px'>Hover an offer for price, spent, estimated profit and break-even. Buy limits count only buys made while the plugin is running.</body></html>");
		tip.setForeground(Color.GRAY);
		tip.setBorder(new EmptyBorder(8, 0, 0, 0));
		tip.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(tip);
		return p;
	}

	private void flipCard(LedgerData.Flip f)
	{
		OfferView buying = null, selling = null;
		for (OfferView o : lastOffers)
		{
			if (o.itemId == f.itemId && o.status == OfferView.Status.ACTIVE)
			{
				if (o.buy)
				{
					buying = o;
				}
				else
				{
					selling = o;
				}
			}
		}

		String status;
		Color edge;
		if (!f.open)
		{
			status = "Done";
			edge = Gp.color(f.profit);
		}
		else if (selling != null)
		{
			status = "Selling";
			edge = Gp.PROFIT;
		}
		else if (buying != null)
		{
			status = "Buying";
			edge = GeSlotTextOverlay.BOUGHT;
		}
		else
		{
			status = f.qty > 0 ? "Part sold" : "Holding";
			edge = Color.GRAY;
		}

		JPanel c = card(edge);
		JPanel lines = new JPanel(new GridLayout(0, 1, 0, 1));
		lines.setOpaque(false);
		lines.add(line(f.name, Color.WHITE));

		String bought = f.boughtQty > 0
			? String.format("%,d @ %s", f.boughtQty, Gp.shortGp(f.avgBuy()))
			: "pre-tracking";
		lines.add(line(status + "  ·  " + bought, Color.LIGHT_GRAY));

		int n = 2;
		if (f.qty > 0)
		{
			lines.add(line(String.format("Sold %,d  ·  %s", f.qty, TIME.format(new Date(f.time))), Color.GRAY));
			n++;
		}
		if (f.open && selling != null && f.boughtQty > 0)
		{
			long holding = Math.max(0, f.boughtQty - f.qty);
			long netEach = selling.price - Gp.taxPerItem(selling.price);
			long est = f.profit + (netEach - f.avgBuy()) * holding;
			JLabel l = new JLabel(String.format("<html>At %s: <font color='%s'>%s</font></html>",
				Gp.shortGp(selling.price), est >= 0 ? "#2ecc71" : "#e74c3c", Gp.signed(est)));
			l.setForeground(Color.GRAY);
			lines.add(l);
			n++;
		}
		c.add(lines, BorderLayout.CENTER);

		JLabel p = new JLabel(f.qty > 0 ? Gp.signed(f.profit) : "");
		p.setForeground(Gp.color(f.profit));
		p.setVerticalAlignment(JLabel.TOP);
		c.add(p, BorderLayout.EAST);
		finish(flips, c, n);
	}

	// ---------- current offers ----------

	void updateOffers(List<OfferView> views)
	{
		lastOffers = views == null ? Collections.emptyList() : views;
		offers.removeAll();
		if (lastOffers.isEmpty())
		{
			empty(offers, "No active offers");
		}
		for (OfferView v : lastOffers)
		{
			offers.add(offerRow(v));
			offers.add(Box.createVerticalStrut(2));
		}
		offers.revalidate();
		offers.repaint();
		renderFlips();
		renderLimits();
		renderSuggestion();
	}

	/** One compact row: "Buy  Item" with the filled amount, and a thin progress bar. Details on hover. */
	private JPanel offerRow(OfferView v)
	{
		Color c = v.status == OfferView.Status.CANCELLED ? RISKY : v.buy ? GeSlotTextOverlay.BOUGHT : Gp.PROFIT;
		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JLabel type = new JLabel(v.buy ? "Buy " : "Sell ");
		type.setForeground(c);
		JLabel name = new JLabel(v.name); // plain label: long names end in "..." instead of wrapping
		name.setForeground(Color.WHITE);
		JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);
		left.add(type, BorderLayout.WEST);
		left.add(name, BorderLayout.CENTER);
		String right = v.status == OfferView.Status.COMPLETE ? "collect"
			: v.status == OfferView.Status.CANCELLED ? "cancelled"
			: String.format("%s/%s", Gp.shortGp(v.filled), Gp.shortGp(v.total));
		JLabel amt = new JLabel(right);
		amt.setForeground(v.status == OfferView.Status.COMPLETE ? c : Color.LIGHT_GRAY);
		row.add(left, BorderLayout.CENTER);
		row.add(amt, BorderLayout.EAST);

		double frac = v.total > 0 ? Math.min(1.0, v.filled / (double) v.total) : 0;
		JPanel bar = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				super.paintComponent(g);
				g.setColor(new Color(0x3A3A3A));
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(c);
				g.fillRect(0, 0, (int) Math.round(getWidth() * frac), getHeight());
			}
		};
		bar.setPreferredSize(new Dimension(10, 3));
		row.add(bar, BorderLayout.SOUTH);

		StringBuilder tip = new StringBuilder("<html>").append(v.name.replace("<", "&lt;")).append("<br>")
			.append(String.format("%,d / %,d @ %,d", v.filled, v.total, v.price));
		if (v.buy)
		{
			tip.append("<br>Spent ").append(Gp.shortGp(v.spent)).append(" of ").append(Gp.shortGp(v.price * v.total));
		}
		else if (v.avgCost >= 0)
		{
			tip.append("<br>Est. ").append(Gp.signed(v.estProfit())).append(String.format(" &middot; break-even %,d", v.breakEven()));
		}
		row.setToolTipText(tip.append("</html>").toString());
		return row;
	}

	private static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	// ---------- suggestions ----------

	void updateSuggestions(List<Suggester.Suggestion> list, String status)
	{
		suggestionStatus.setText(status);
		lastSuggestions = list == null ? Collections.emptyList() : list;
		renderSuggestion();
	}

	/** Shows one suggestion. Skipped, blocked, already-bought and already-held items are passed over. */
	private void renderSuggestion()
	{
		long now = System.currentTimeMillis();
		skipped.values().removeIf(t -> now - t > SKIP_MS);

		Set<Integer> busy = new HashSet<>();
		for (OfferView o : lastOffers)
		{
			busy.add(o.itemId);
		}
		if (lastData != null)
		{
			for (LedgerData.Flip f : lastData.history)
			{
				if (f.open)
				{
					busy.add(f.itemId);
				}
			}
		}

		List<Suggester.Suggestion> left = new ArrayList<>();
		for (Suggester.Suggestion s : lastSuggestions)
		{
			if (!skipped.containsKey(s.id) && !busy.contains(s.id))
			{
				left.add(s);
			}
		}

		suggestions.removeAll();
		if (left.isEmpty())
		{
			empty(suggestions, lastSuggestions.isEmpty()
				? "Nothing passes the checklist right now with your settings. Try a longer fill window, a lower min profit, or more item types."
				: "You've skipped or are already flipping every current suggestion. Skips clear after 30 minutes, or press Refresh.");
		}
		else
		{
			Suggester.Suggestion s = left.get(0);
			Color tag = "steady".equals(s.status) ? STEADY : "settled".equals(s.status) ? SETTLED
				: ("checking".equals(s.status) || "unchecked".equals(s.status)) ? Color.GRAY : RISKY;
			JPanel c = card(tag);
			c.setToolTipText("<html>" + s.status + ": " + s.why + "<br>About " + String.format("%,d", s.perHour)
				+ " trade each way per hour</html>");
			JPanel lines = new JPanel();
			lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
			lines.setOpaque(false);
			// Full item name, wrapping if needed: "(e)" and similar matter
			JLabel name = new JLabel("<html><body style='width:140px'>" + s.name.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
			name.setForeground(Color.WHITE);
			name.setFont(FontManager.getRunescapeBoldFont());
			lines.add(name);
			lines.add(line("Profit " + Gp.signed(s.profit), Gp.PROFIT));
			lines.add(line(String.format("Buy at %,d", s.buy), GeSlotTextOverlay.BOUGHT));
			lines.add(line(String.format("Sell at %,d", s.sell), Gp.PROFIT));
			lines.add(line(String.format("Qty %,d  ·  %s gp", s.qty, Gp.shortGp(s.cost)), Color.LIGHT_GRAY));
			lines.add(line(String.format("Break-even %,d", s.breakEven), Color.GRAY));
			lines.add(line(String.format("%s  ·  ~%d min/side", s.status, s.fillMinutes), tag));
			c.add(lines, BorderLayout.CENTER);
			suggestions.add(c);
			suggestions.add(Box.createVerticalStrut(3));

			JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
			actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
			actions.setAlignmentX(Component.LEFT_ALIGNMENT);
			actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			JButton skip = new JButton("Skip");
			skip.setToolTipText("Show the next suggestion (this one comes back in 30 minutes)");
			skip.addActionListener(e ->
			{
				skipped.put(s.id, System.currentTimeMillis());
				renderSuggestion();
			});
			JButton block = new JButton("Block");
			block.setToolTipText("Never suggest this item again (undo in Settings)");
			block.addActionListener(e ->
			{
				skipped.put(s.id, System.currentTimeMillis()); // hide straight away
				onBlock.accept(s.id);
				renderSuggestion();
			});
			actions.add(skip);
			actions.add(block);
			suggestions.add(actions);

			JLabel hint = new JLabel(String.format("<html><body style='width:150px'>%d of %d. Place the buy yourself; this moves on once it's in the GE.</body></html>",
				1, left.size()));
			hint.setForeground(Color.GRAY);
			hint.setBorder(new EmptyBorder(4, 0, 0, 0));
			hint.setAlignmentX(Component.LEFT_ALIGNMENT);
			suggestions.add(hint);
		}
		suggestions.revalidate();
		suggestions.repaint();
	}
}
