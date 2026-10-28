package com.flipledger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Session settings people can change any time. Values are saved in RuneLite's config,
 * so they also appear (and stay in sync) in the plugin's normal settings page.
 */
class SettingsTab extends JPanel
{
	private final ConfigManager configManager;
	private final FlipLedgerConfig config;
	private final Runnable onChange;

	SettingsTab(ConfigManager configManager, FlipLedgerConfig config, Runnable onChange)
	{
		this.configManager = configManager;
		this.config = config;
		this.onChange = onChange;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		build();
	}

	/** Re-read values, e.g. after they were changed on the RuneLite settings page. */
	void reload()
	{
		removeAll();
		build();
		revalidate();
		repaint();
	}

	private void build()
	{
		header("Flipping session");
		gpField("GP to use", "budget", config::budget, "Total gp suggestions may use");
		gpField("Max per slot", "maxPerSlot", config::maxPerSlot, "Most gp in one item");
		gpField("Min profit", "minProfit", config::minProfit, "Smallest profit worth a slot");

		JComboBox<FlipLedgerConfig.FillWindow> fill = new JComboBox<>(FlipLedgerConfig.FillWindow.values());
		fill.setSelectedItem(config.fillWindow());
		fill.addActionListener(e -> set("fillWindow", ((FlipLedgerConfig.FillWindow) fill.getSelectedItem()).name()));
		row("Fill window", fill);

		check("Show risky items", "showRisky", config.showRisky());
		check("Suggestions (Wiki prices)", "suggestions", config.suggestions());
		int blockedCount = config.blockedItems().trim().isEmpty() ? 0 : config.blockedItems().split(",").length;
		javax.swing.JButton unblock = new javax.swing.JButton("Unblock all (" + blockedCount + ")");
		unblock.setAlignmentX(Component.LEFT_ALIGNMENT);
		unblock.addActionListener(e ->
		{
			set("blockedItems", "");
			unblock.setText("Unblock all (0)");
		});
		add(unblock);

		header("Item types");
		check("Potions", "catPotions", config.catPotions());
		check("Food", "catFood", config.catFood());
		check("Runes", "catRunes", config.catRunes());
		check("Ammo", "catAmmo", config.catAmmo());
		check("Herbs & secondaries", "catHerbs", config.catHerbs());
		check("Seeds & saplings", "catSeeds", config.catSeeds());
		check("Bones & ashes", "catBones", config.catBones());
		check("Teleports", "catTeleports", config.catTeleports());
		check("Other supplies", "catSupplies", config.catSupplies());
		check("Everything else (gear, resources...)", "catOther", config.catOther());

		header("Display");
		check("Price check while making offers", "showPriceCheck", config.showPriceCheck());
		check("Price drop alerts", "crashAlerts", config.crashAlerts());
		check("Floating text on player", "showFloatingText", config.showFloatingText());
		check("Floating text on alch", "showAlchText", config.showAlchText());
		check("Floating text on GE offers", "showOnGeSlots", config.showOnGeSlots());
		check("Chat message per sale", "showChatMessage", config.showChatMessage());
		check("Subtract 2% GE tax", "applyTax", config.applyTax());

		header("Crew leaderboard");
		check("Share with crew leaderboard", "crewEnabled", config.crewEnabled());
		textField("Submit URL", "crewUrl", config::crewUrl, false, "From your crew leader, starts with https://");
		textField("Crew key", "crewKey", config::crewKey, true, "The crew key from your crew leader");
		JLabel crewNote = new JLabel("<html><body style='width:150px'>Off by default. When on, sends only your name, "
			+ "profit and flip count for the current 1h / 12h / 24h windows (Eastern time), at most once a minute.</body></html>");
		crewNote.setForeground(Color.GRAY);
		crewNote.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(crewNote);

		JLabel note = new JLabel("<html><body style='width:150px'>Members items are hidden automatically on free-to-play worlds.</body></html>");
		note.setForeground(Color.GRAY);
		note.setBorder(BorderFactory.createEmptyBorder(10, 2, 0, 0));
		note.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(note);
	}

	private void header(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(l);
	}

	private void row(String label, Component field)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setBackground(ColorScheme.DARK_GRAY_COLOR);
		r.setAlignmentX(Component.LEFT_ALIGNMENT);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		JLabel l = new JLabel(label);
		l.setForeground(Color.LIGHT_GRAY);
		l.setPreferredSize(new Dimension(80, 24));
		r.add(l, BorderLayout.WEST);
		r.add(field, BorderLayout.CENTER);
		add(r);
		add(Box.createVerticalStrut(4));
	}

	private void gpField(String label, String key, Supplier<String> current, String tip)
	{
		JTextField f = new JTextField(current.get());
		f.setToolTipText(tip + ". Use k / m, e.g. 750k or 10m");
		Runnable commit = () ->
		{
			String v = f.getText().trim();
			if (Suggester.parse(v, -1) < 0)
			{
				f.setText(current.get()); // invalid: put the old value back
				return;
			}
			if (!v.equalsIgnoreCase(current.get()))
			{
				set(key, v);
			}
		};
		f.addActionListener(e -> commit.run());
		f.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});
		row(label, f);
	}

	private void textField(String label, String key, Supplier<String> current, boolean secret, String tip)
	{
		JTextField f = secret ? new javax.swing.JPasswordField(current.get()) : new JTextField(current.get());
		f.setToolTipText(tip);
		Runnable commit = () ->
		{
			String v = f.getText().trim();
			if (!v.equals(current.get()))
			{
				set(key, v);
			}
		};
		f.addActionListener(e -> commit.run());
		f.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});
		row(label, f);
	}

	private void check(String label, String key, boolean value)
	{
		JCheckBox c = new JCheckBox(label, value);
		c.setBackground(ColorScheme.DARK_GRAY_COLOR);
		c.setForeground(Color.LIGHT_GRAY);
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.addActionListener(e -> set(key, String.valueOf(c.isSelected())));
		add(c);
	}

	private void set(String key, String value)
	{
		configManager.setConfiguration(FlipLedgerConfig.GROUP, key, value);
		onChange.run();
	}
}
