package com.flipledger;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar with four tabs: Settings, Info, Session and Offers.
 */
class FlipLedgerPanel extends PluginPanel
{
	private final SettingsTab settings;
	private final SessionTab session;

	FlipLedgerPanel(ConfigManager configManager, FlipLedgerConfig config,
		Runnable onSettingsChanged, Runnable onReset, Runnable onClear, Runnable onRefreshSuggestions,
		java.util.function.IntConsumer onBlock)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		settings = new SettingsTab(configManager, config, onSettingsChanged);
		InfoTab info = new InfoTab();
		session = new SessionTab(onReset, onClear, onRefreshSuggestions, onBlock);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setBorder(new EmptyBorder(8, 0, 4, 0));

		MaterialTab settingsTab = new MaterialTab("Settings", tabs, scroll(settings));
		MaterialTab infoTab = new MaterialTab("Info", tabs, scroll(info));
		MaterialTab sessionTab = new MaterialTab("Session", tabs, scroll(session));
		MaterialTab offersTab = new MaterialTab("Offers", tabs, scroll(session.offersView()));
		tabs.addTab(settingsTab);
		tabs.addTab(infoTab);
		tabs.addTab(sessionTab);
		tabs.addTab(offersTab);
		// Four tabs don't fit the sidebar in a flow layout; use four equal columns instead
		tabs.removeAll();
		tabs.setLayout(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
		gc.weightx = 1;
		gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		for (MaterialTab t : new MaterialTab[]{settingsTab, infoTab, sessionTab, offersTab})
		{
			t.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
			t.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			tabs.add(t, gc);
		}
		tabs.select(sessionTab);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	private static JScrollPane scroll(JPanel content)
	{
		WidthPanel wrap = new WidthPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(6, 10, 10, 10));
		wrap.add(content, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane(wrap);
		sp.setBorder(null);
		sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sp.getVerticalScrollBar().setUnitIncrement(16);
		sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return sp;
	}

	void update(long profit, int flips, long sessionMs, LedgerData data)
	{
		session.update(profit, flips, sessionMs, data);
	}

	void updateOffers(List<OfferView> views)
	{
		session.updateOffers(views);
	}

	void updateSuggestions(List<Suggester.Suggestion> list, String status)
	{
		session.updateSuggestions(list, status);
	}

	void reloadSettings()
	{
		settings.reload();
	}
}
