package com.flipledger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Sorts GE items into consumable groups by name. Anything else falls under "Everything else".
 */
final class Consumables
{
	enum Group
	{
		POTIONS, FOOD, RUNES, AMMO, HERBS, SEEDS, BONES, TELEPORTS, SUPPLIES
	}

	private static final Set<String> FOOD = set(
		"shark", "manta ray", "anglerfish", "dark crab", "sea turtle", "tuna potato", "lobster", "swordfish",
		"monkfish", "cooked karambwan", "pineapple pizza", "anchovy pizza", "meat pizza", "plain pizza", "cake",
		"chocolate cake", "trout", "salmon", "tuna", "bass", "pike", "shrimps", "anchovies", "sardine", "herring",
		"mackerel", "cod", "cooked chicken", "cooked meat", "bread", "potato with cheese", "egg potato",
		"mushroom potato", "chilli potato", "curry", "stew", "jug of wine", "beer", "banana", "pineapple",
		"watermelon", "strawberry", "papaya fruit", "coconut", "purple sweets", "saradomin brew(4)",
		"blighted manta ray", "blighted anglerfish", "blighted karambwan", "cooked sunlight antelope",
		"cooked dashing kebbit", "cooked moonlight antelope", "botanical pie", "summer pie", "wild pie",
		"admiral pie", "mushroom pie", "dragonfruit", "dragonfruit pie", "cooked bream", "cooked moss lizard",
		"cooked rainbow fish", "cave eel", "slimy eel", "lava eel", "giant carp");

	private static final Set<String> HERBS = set(
		"guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf", "avantoe",
		"kwuarm", "huasca", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol",
		"eye of newt", "limpwurt root", "red spiders' eggs", "white berries", "snape grass", "wine of zamorak",
		"unicorn horn dust", "chocolate dust", "crushed nest", "potato cactus", "zulrah's scales",
		"dragon scale dust", "crystal dust", "mort myre fungus", "jangerberries", "goat horn dust", "lava scale shard",
		"amylase crystal", "nail beast nails", "ground mud runes", "magic roots", "yew roots", "ancient brew",
		"ground desert goat horn", "araxyte venom sack", "vial of water", "vial", "grapes", "cactus spine",
		"ashes", "volcanic ash");

	private static final Set<String> SUPPLIES = set(
		"pure essence", "rune essence", "daeyalt essence", "ancient essence", "revenant ether", "sunfire splinters",
		"blighted ancient ice sack", "blighted entangle sack", "blighted teleport spell sack", "blighted bind sack",
		"blighted snare sack", "blighted vengeance sack", "coconut", "compost", "supercompost", "ultracompost",
		"bucket of sand", "soda ash", "seaweed", "giant seaweed", "molten glass", "feather", "steel nails",
		"mithril nails", "adamantite nails", "bow string", "flax", "battlestaff", "air orb", "water orb", "earth orb",
		"fire orb", "unpowered orb", "plank", "oak plank", "teak plank", "mahogany plank", "camphor plank",
		"ironwood plank", "rosewood plank", "gold bar", "silver bar", "bronze bar", "iron bar", "steel bar",
		"mithril bar", "adamantite bar", "runite bar", "coal", "iron ore", "gold ore", "silver ore", "mithril ore",
		"adamantite ore", "runite ore", "logs", "oak logs", "willow logs", "maple logs", "yew logs", "magic logs",
		"redwood logs", "teak logs", "mahogany logs", "dragon bones", "green dragonhide", "blue dragonhide",
		"red dragonhide", "black dragonhide", "green dragon leather", "blue dragon leather", "red dragon leather",
		"black dragon leather", "cannonball", "steel cannonball", "granite cannonball", "frozen tear",
		"dark essence block", "cosmic rune");

	private Consumables()
	{
	}

	private static Set<String> set(String... s)
	{
		return new HashSet<>(Arrays.asList(s));
	}

	static Group groupOf(String rawName)
	{
		String n = rawName.toLowerCase(Locale.ROOT).trim();

		if (n.endsWith(" rune") && !n.contains("pouch"))
		{
			return Group.RUNES;
		}
		if (n.matches(".*\\([1-4]\\)$") || n.endsWith("(unf)") || n.contains("potion") || n.contains(" brew")
			|| n.startsWith("anti") || n.contains("antifire") || n.contains("antivenom") || n.contains("antidote")
			|| n.contains("stamina") || n.contains("restore") || n.contains("divine ") || n.contains(" mix("))
		{
			return n.endsWith("(unf)") ? Group.HERBS : Group.POTIONS;
		}
		if (n.endsWith(" bones") || n.equals("bones") || n.endsWith(" ashes") || n.endsWith(" remains"))
		{
			return Group.BONES;
		}
		if (n.endsWith(" seed") || n.endsWith(" sapling") || n.endsWith(" spore"))
		{
			return Group.SEEDS;
		}
		if (n.endsWith(" teleport") || n.endsWith("(tablet)") || n.endsWith("teleport scroll") || n.startsWith("teleport to"))
		{
			return Group.TELEPORTS;
		}
		if (n.endsWith(" arrow") || n.endsWith(" arrows") || n.contains(" arrow(") || n.contains(" bolts")
			|| n.endsWith(" dart") || n.contains(" dart(") || n.endsWith(" javelin") || n.contains(" javelin(")
			|| n.endsWith("cannonball") || n.contains("chinchompa") || n.endsWith(" knife") && n.contains("throwing")
			|| n.endsWith(" thrownaxe") || n.endsWith(" dart tip") || n.endsWith(" javelin heads") || n.endsWith(" arrowtips"))
		{
			return Group.AMMO;
		}
		if (n.startsWith("grimy ") || HERBS.contains(n))
		{
			return Group.HERBS;
		}
		if (FOOD.contains(n) || n.startsWith("cooked "))
		{
			return Group.FOOD;
		}
		if (SUPPLIES.contains(n))
		{
			return Group.SUPPLIES;
		}
		return null;
	}

	static boolean enabled(Group g, FlipLedgerConfig c)
	{
		switch (g)
		{
			case POTIONS:
				return c.catPotions();
			case FOOD:
				return c.catFood();
			case RUNES:
				return c.catRunes();
			case AMMO:
				return c.catAmmo();
			case HERBS:
				return c.catHerbs();
			case SEEDS:
				return c.catSeeds();
			case BONES:
				return c.catBones();
			case TELEPORTS:
				return c.catTeleports();
			default:
				return c.catSupplies();
		}
	}
}
