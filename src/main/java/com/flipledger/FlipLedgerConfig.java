package com.flipledger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(FlipLedgerConfig.GROUP)
public interface FlipLedgerConfig extends Config
{
	String GROUP = "flipledger";

	@ConfigSection(name = "Flipping session", description = "How suggestions are picked and sized", position = 0)
	String session = "session";

	@ConfigSection(name = "Item types", description = "Which kinds of items to suggest", position = 1)
	String types = "types";

	@ConfigSection(name = "Display", description = "Floating text and chat", position = 2)
	String display = "display";

	@ConfigSection(name = "Crew leaderboard", description = "Optional: share your own profit with your group's leaderboard", position = 3)
	String crew = "crew";

	@ConfigItem(keyName = "crewEnabled", name = "Share with crew leaderboard",
		description = "Off by default. Sends only your name, profit and flip count for the current 1h / 12h / 24h windows to the URL below",
		warning = "This sends your RuneScape name, your flipping profit and flip counts to the third-party server "
			+ "you enter below (your crew's leaderboard), which is not controlled or verified by RuneLite developers. "
			+ "It also reveals your IP address to that server. Only turn this on for a server you trust.",
		section = crew, position = 1)
	default boolean crewEnabled()
	{
		return false;
	}

	@ConfigItem(keyName = "crewUrl", name = "Submit URL", description = "Your crew's leaderboard address, e.g. https://flips.yourname.workers.dev",
		section = crew, position = 3)
	default String crewUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "crewKey", name = "Crew key", description = "The crew key from your crew leader. Your RuneScape name is claimed for you on your first submit", secret = true,
		section = crew, position = 4)
	default String crewKey()
	{
		return "";
	}

	@ConfigItem(keyName = "mode", name = "Mode", description = "Flip or Alch", hidden = true)
	default String mode()
	{
		return "flip";
	}

	@ConfigItem(keyName = "alchGoal", name = "Alch goal", description = "\"bond\" for the live bond price, or an amount like 10m", hidden = true)
	default String alchGoal()
	{
		return "bond";
	}

	@ConfigItem(keyName = "alchFireStaff", name = "Fire staff", description = "Alching with a fire staff (no fire runes)", hidden = true)
	default boolean alchFireStaff()
	{
		return true;
	}

	// ---------- session ----------

	@ConfigItem(keyName = "budget", name = "GP to use", description = "Total gp for suggested flips, e.g. 50m or 750k",
		section = session, position = 1)
	default String budget()
	{
		return "50m";
	}

	@ConfigItem(keyName = "maxPerSlot", name = "Max per slot", description = "Most gp in any single suggestion, e.g. 10m",
		section = session, position = 2)
	default String maxPerSlot()
	{
		return "10m";
	}

	@ConfigItem(keyName = "minProfit", name = "Min profit", description = "Hide suggestions that make less than this, e.g. 25k",
		section = session, position = 3)
	default String minProfit()
	{
		return "25k";
	}

	@ConfigItem(keyName = "fillWindow", name = "Fill window", description = "How long you're willing to wait for an offer to fill",
		section = session, position = 4)
	default FillWindow fillWindow()
	{
		return FillWindow.MIN_30;
	}

	@ConfigItem(keyName = "showRisky", name = "Show risky items", description = "Also suggest items that are sliding or spiking (not recommended)",
		section = session, position = 5)
	default boolean showRisky()
	{
		return false;
	}

	@ConfigItem(keyName = "blockedItems", name = "Blocked items", description = "Item IDs you blocked from suggestions",
		hidden = true, section = session, position = 7)
	default String blockedItems()
	{
		return "";
	}

	@ConfigItem(keyName = "suggestions", name = "Suggestions", description = "Download public OSRS Wiki prices to suggest flips (about 10 small requests an hour, only while logged in)",
		section = session, position = 6)
	default boolean suggestions()
	{
		return true;
	}

	// ---------- item types ----------

	@ConfigItem(keyName = "catPotions", name = "Potions", description = "", section = types, position = 1)
	default boolean catPotions()
	{
		return true;
	}

	@ConfigItem(keyName = "catFood", name = "Food", description = "", section = types, position = 2)
	default boolean catFood()
	{
		return true;
	}

	@ConfigItem(keyName = "catRunes", name = "Runes", description = "", section = types, position = 3)
	default boolean catRunes()
	{
		return true;
	}

	@ConfigItem(keyName = "catAmmo", name = "Ammo", description = "Arrows, bolts, darts, javelins, cannonballs, chinchompas", section = types, position = 4)
	default boolean catAmmo()
	{
		return true;
	}

	@ConfigItem(keyName = "catHerbs", name = "Herbs & secondaries", description = "", section = types, position = 5)
	default boolean catHerbs()
	{
		return true;
	}

	@ConfigItem(keyName = "catSeeds", name = "Seeds & saplings", description = "", section = types, position = 6)
	default boolean catSeeds()
	{
		return true;
	}

	@ConfigItem(keyName = "catBones", name = "Bones & ashes", description = "", section = types, position = 7)
	default boolean catBones()
	{
		return true;
	}

	@ConfigItem(keyName = "catTeleports", name = "Teleports", description = "Tablets and scrolls", section = types, position = 8)
	default boolean catTeleports()
	{
		return true;
	}

	@ConfigItem(keyName = "catSupplies", name = "Other supplies", description = "Essence, ether, splinters, ice sacks, coconuts and similar", section = types, position = 9)
	default boolean catSupplies()
	{
		return true;
	}

	@ConfigItem(keyName = "catOther", name = "Everything else", description = "Gear, weapons, resources, rares and any other item that trades steadily",
		section = types, position = 10)
	default boolean catOther()
	{
		return true;
	}

	// ---------- display ----------

	@ConfigItem(keyName = "showFloatingText", name = "Floating text on player",
		description = "Green +gp / red -gp above your character when a sale completes", section = display, position = 1)
	default boolean showFloatingText()
	{
		return true;
	}

	@ConfigItem(keyName = "showAlchText", name = "Floating text on alch",
		description = "Green +gp / red -gp above your character for each High Alch (profit after the item and runes)",
		section = display, position = 2)
	default boolean showAlchText()
	{
		return true;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(keyName = "floatingSeconds", name = "Floating text seconds", description = "How long the text stays on screen",
		section = display, position = 2)
	default int floatingSeconds()
	{
		return 3;
	}

	@Range(min = 10, max = 32)
	@ConfigItem(keyName = "floatingFontSize", name = "Floating text size", description = "Font size of the text above your character",
		section = display, position = 3)
	default int floatingFontSize()
	{
		return 18;
	}

	@ConfigItem(keyName = "showOnGeSlots", name = "Floating text on GE offers",
		description = "Float profit (sales) and quantity (buys) over the offer slot each time it fills", section = display, position = 4)
	default boolean showOnGeSlots()
	{
		return true;
	}

	@ConfigItem(keyName = "showPriceCheck", name = "Price check while making offers",
		description = "Show recommended prices for the item you pick when setting up a GE offer", section = display, position = 0)
	default boolean showPriceCheck()
	{
		return true;
	}

	@ConfigItem(keyName = "crashAlerts", name = "Price drop alerts",
		description = "Warn in chat and with a notification when something you hold or are selling drops below break-even or falls 3%+ fast",
		section = display, position = 0)
	default boolean crashAlerts()
	{
		return true;
	}

	@ConfigItem(keyName = "showChatMessage", name = "Chat message", description = "Post a line in your chat box (only you see it) for each sale",
		section = display, position = 5)
	default boolean showChatMessage()
	{
		return true;
	}

	@ConfigItem(keyName = "applyTax", name = "Subtract 2% GE tax",
		description = "Subtract the GE tax from sale proceeds (2%, nothing under 50 gp, capped at 5M per item)", section = display, position = 6)
	default boolean applyTax()
	{
		return true;
	}

	enum FillWindow
	{
		MIN_5("5 min", 5),
		MIN_15("15 min", 15),
		MIN_30("30 min", 30),
		HOUR_1("1 hour", 60),
		HOUR_4("4 hours", 240);

		final String label;
		final int minutes;

		FillWindow(String label, int minutes)
		{
			this.label = label;
			this.minutes = minutes;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
