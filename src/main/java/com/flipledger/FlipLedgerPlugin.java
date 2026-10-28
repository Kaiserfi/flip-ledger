package com.flipledger;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Flip Ledger",
	description = "Tracks Grand Exchange flips with profit after tax, floating +/- gp text and gp/hr",
	tags = {"flip", "flipping", "grand exchange", "ge", "profit", "merch"}
)
public class FlipLedgerPlugin extends Plugin
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlipLedgerPlugin.class);
	private static final String DATA_KEY = "data";
	/** Right after login the GE briefly reports every slot as empty; ignore that. */
	private static final long LOGIN_GRACE_MS = 10_000;
	private static final long MANUAL_REFRESH_GAP_MS = 60_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private FlipLedgerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private FloatingTextOverlay floatingText;

	@Inject
	private GeSlotTextOverlay geSlotText;

	@Inject
	private PriceCheckOverlay priceCheck;

	@Inject
	private Notifier notifier;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	private PriceService prices;
	private CrewLeaderboard crew;
	private volatile String playerName;
	private ScheduledFuture<?> priceTask;
	private volatile boolean loggedIn;
	private volatile boolean membersWorld = true;
	private long lastManualRefresh;

	private FlipLedgerPanel panel;
	private NavigationButton navButton;
	private Timer clock;

	private LedgerData data;
	private String loadedProfile;
	private long loginAt;

	private long sessionStart;
	private long sessionProfit;
	private int sessionFlips;

	@Provides
	FlipLedgerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlipLedgerConfig.class);
	}

	@Override
	protected void startUp()
	{
		resetSession();
		prices = new PriceService(okHttpClient, gson);
		crew = new CrewLeaderboard(okHttpClient, gson);
		recipes = new Recipes(gson);
		alchTab = new AlchTab(config.alchGoal(), config.alchFireStaff(),
			goal ->
			{
				configManager.setConfiguration(FlipLedgerConfig.GROUP, "alchGoal", goal.isEmpty() ? "bond" : goal);
				refreshAlch();
			},
			staff ->
			{
				configManager.setConfiguration(FlipLedgerConfig.GROUP, "alchFireStaff", staff);
				refreshAlch();
			},
			() ->
			{
				alchSessionStart = System.currentTimeMillis();
				alchSessionCasts = 0;
				alchSessionProfit = 0;
				refreshAlch();
			});
		alchSessionStart = System.currentTimeMillis();
		panel = new FlipLedgerPanel(configManager, config, this::recomputeSuggestions,
			this::resetSessionFromPanel, this::clearHistoryFromPanel, this::manualRefresh, this::blockItem,
			this::limitReached, alchTab);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Flip Ledger")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(floatingText);
		overlayManager.add(geSlotText);
		overlayManager.add(priceCheck);

		// Keep session time and gp/hr ticking
		clock = new Timer(10_000, e -> refreshPanel());
		clock.start();

		// Checks once a minute whether a price file is due; the files themselves refresh every 10-30 min
		priceTask = executor.scheduleAtFixedRate(this::tickPrices, 5, 30, TimeUnit.SECONDS);
		recomputeSuggestions();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loginAt = 0;
			clientThread.invokeLater(() ->
			{
				onLogin();
				ensureLoaded();
				refreshPanel();
				refreshOffers();
			});
		}
		refreshPanel();
	}

	@Override
	protected void shutDown()
	{
		if (clock != null)
		{
			clock.stop();
		}
		if (priceTask != null)
		{
			priceTask.cancel(false);
		}
		loggedIn = false;
		overlayManager.remove(floatingText);
		floatingText.clear();
		overlayManager.remove(geSlotText);
		overlayManager.remove(priceCheck);
		priceCheck.hide();
		geSlotText.clear();
		clientToolbar.removeNavigation(navButton);
		data = null;
		loadedProfile = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			loginAt = System.currentTimeMillis();
			onLogin();
			ensureLoaded();
			refreshPanel();
			refreshOffers();
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			loggedIn = false;
			data = null;
			loadedProfile = null;
			refreshPanel();
			refreshOffers();
		}
	}

	// ---------- price check while setting up an offer ----------

	private String lastCheckKey;
	private int checkTicks;

	@Subscribe
	public void onGameTick(GameTick t)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden() || !config.showPriceCheck())
		{
			if (lastCheckKey != null)
			{
				priceCheck.hide();
				lastCheckKey = null;
			}
			return;
		}
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		boolean sell = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 1;
		int qty = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
		String key = itemId + ":" + sell + ":" + qty;
		// Recompute when the item, side or quantity changes, and every ~30 s for fresh prices
		if (itemId > 0)
		{
			prices.refreshLatest(java.util.Collections.singletonList(itemId), 30_000, () -> lastCheckKey = null);
		}
		if (key.equals(lastCheckKey) && ++checkTicks < 10)
		{
			return;
		}
		lastCheckKey = key;
		checkTicks = 0;
		if (itemId <= 0)
		{
			priceCheck.show("Price check", java.util.Collections.singletonList(
				new PriceCheckOverlay.Line("Pick an item", "", java.awt.Color.GRAY)));
			return;
		}
		String name = itemManager.getItemComposition(itemId).getName();
		long avgCost = -1;
		if (data != null)
		{
			LedgerData.Position p = data.positions.get(itemId);
			if (p != null && p.qty > 0)
			{
				avgCost = p.totalCost / p.qty;
			}
		}
		LedgerData.LimitWindow lw = data == null ? null : data.limitWindow(itemId, System.currentTimeMillis());
		priceCheck.show(name, PriceCheck.lines(prices, itemId, sell, qty, avgCost, config.applyTax(), lw,
			processing(), () -> lastCheckKey = null));
	}

	private void onLogin()
	{
		lastInv = null; // don't compare against another account's inventory
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			playerName = client.getLocalPlayer().getName();
		}
		boolean wasLoggedIn = loggedIn;
		loggedIn = true;
		boolean members = client.getWorldType().contains(WorldType.MEMBERS);
		boolean changed = members != membersWorld;
		membersWorld = members;
		if (!wasLoggedIn)
		{
			executor.execute(this::tickPrices);
		}
		else if (changed)
		{
			recomputeSuggestions();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (FlipLedgerConfig.GROUP.equals(e.getGroup()) && !"data".equals(e.getKey()))
		{
			recomputeSuggestions();
		}
	}

	// ---------- suggestions ----------

	private volatile java.util.List<OfferView> lastOffers = java.util.Collections.emptyList();
	private final java.util.Map<Integer, Long> alerted = new java.util.concurrent.ConcurrentHashMap<>();
	private long lastWatch;
	private static final long WATCH_EVERY_MS = 120_000;
	private static final long ALERT_COOLDOWN_MS = 15 * 60_000L;

	/** Every 2 minutes: check the latest trades on items you hold or are selling, and warn on a drop. */
	private void watchHoldings()
	{
		LedgerData d = data;
		if (!loggedIn || d == null || !config.crashAlerts() || System.currentTimeMillis() - lastWatch < WATCH_EVERY_MS)
		{
			return;
		}
		java.util.Map<Integer, String> watch = new java.util.HashMap<>();
		for (OfferView o : lastOffers)
		{
			if (!o.buy && o.status == OfferView.Status.ACTIVE)
			{
				watch.put(o.itemId, o.name);
			}
		}
		for (LedgerData.Flip f : new java.util.ArrayList<>(d.history))
		{
			if (f.open && f.boughtQty > f.qty)
			{
				watch.put(f.itemId, f.name);
			}
		}
		if (watch.isEmpty())
		{
			return;
		}
		lastWatch = System.currentTimeMillis();
		prices.refreshLatest(watch.keySet(), WATCH_EVERY_MS - 5_000, () -> checkDrops(watch));
		for (int id : watch.keySet())
		{
			prices.recent(id, () -> { });
		}
	}

	private void checkDrops(java.util.Map<Integer, String> watch)
	{
		LedgerData d = data;
		long now = System.currentTimeMillis();
		for (java.util.Map.Entry<Integer, String> e : watch.entrySet())
		{
			int id = e.getKey();
			PriceService.Latest l = prices.latestFor(id);
			if (l == null || l.high <= 0 || now / 1000 - l.highTime > 600)
			{
				continue;
			}
			Long last = alerted.get(id);
			if (last != null && now - last < ALERT_COOLDOWN_MS)
			{
				continue;
			}
			String why = null;
			LedgerData.Position p = d == null ? null : d.positions.get(id);
			if (p != null && p.qty > 0)
			{
				long be = Suggester.breakEven(p.totalCost / p.qty);
				if (l.high < be)
				{
					why = String.format("buyers at %,d, below your break-even %,d", l.high, be);
				}
			}
			PriceService.Recent r = prices.recent(id, () -> { });
			if (why == null && r != null && r.buyer > 0 && l.high < r.buyer * 0.97)
			{
				why = String.format("buyers at %,d, down %.1f%% from the usual %,d", l.high, (1 - l.high / (double) r.buyer) * 100, r.buyer);
			}
			if (why != null)
			{
				alerted.put(id, now);
				String msg = "Flip Ledger: " + e.getValue() + " is dropping (" + why + ")" + processTip(id, l.high);
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage("<col=e74c3c>" + msg + "</col>")
					.build());
				notifier.notify(msg);
			}
		}
	}

	/** " Tip: making X gets about Y each instead." when processing beats selling at today's buyer price. */
	private String processTip(int id, long buyerNow)
	{
		if (recipes == null)
		{
			return "";
		}
		Processing proc = processing();
		Processing.Path p = proc.best(id);
		double asIs = buyerNow - (config.applyTax() ? Gp.taxPerItem(buyerNow) : 0);
		if (p == null || p.steps.isEmpty() || p.valueEach <= asIs * 1.01)
		{
			return "";
		}
		String product = itemManager.getItemComposition(p.steps.get(p.steps.size() - 1).o).getName();
		return String.format(". Tip: making %s is worth about %,d each vs %,d selling now", product,
			Math.round(p.valueEach), Math.round(asIs));
	}

	private void tickPrices()
	{
		watchHoldings();
		LedgerData d = data;
		if (loggedIn && d != null)
		{
			crew.maybeSend(config, playerName, d.eventsCopy(), crewToken(), this::saveCrewToken);
		}
		if (loggedIn && config.suggestions())
		{
			prices.refreshDue(this::recomputeSuggestions, false);
		}
	}

	private void manualRefresh()
	{
		long now = System.currentTimeMillis();
		if (now - lastManualRefresh < MANUAL_REFRESH_GAP_MS || !config.suggestions())
		{
			recomputeSuggestions();
			return;
		}
		lastManualRefresh = now;
		prices.refreshDue(this::recomputeSuggestions, true);
	}

	/** Per-account token the leaderboard handed back when this RuneScape name was first claimed. */
	private String crewToken()
	{
		return configManager.getRSProfileConfiguration(FlipLedgerConfig.GROUP, "crewToken");
	}

	private void saveCrewToken(String token)
	{
		configManager.setRSProfileConfiguration(FlipLedgerConfig.GROUP, "crewToken", token);
	}

	private void blockItem(int itemId)
	{
		String cur = config.blockedItems().trim();
		String next = cur.isEmpty() ? String.valueOf(itemId) : cur + "," + itemId;
		configManager.setConfiguration(FlipLedgerConfig.GROUP, "blockedItems", next);
	}

	/** "At buy limit" on a suggestion: count the limit as used up for this 4-hour window. */
	private void limitReached(int itemId)
	{
		// Change saved data on the game thread, like every other update
		clientThread.invokeLater(() ->
		{
			LedgerData d = data;
			if (d == null)
			{
				return;
			}
			PriceService.Item it = prices.mapping.get(itemId);
			d.markLimitReached(itemId, System.currentTimeMillis(), it == null ? "Item" : it.name, it == null ? 0 : it.limit);
			save();
			refreshPanel();
			recomputeSuggestions();
		});
	}

	private synchronized void recomputeSuggestions()
	{
		refreshAlch();
		if (panel == null)
		{
			return;
		}
		final List<Suggester.Suggestion> list;
		final String status;
		if (!config.suggestions())
		{
			list = java.util.Collections.emptyList();
			status = "Suggestions are off (Settings tab).";
		}
		else if (prices.hourAt == 0)
		{
			list = java.util.Collections.emptyList();
			status = loggedIn ? "Loading prices from the OSRS Wiki..." : "Log in to load suggestions.";
		}
		else
		{
			list = Suggester.compute(prices, config, membersWorld, this::boughtRecently, busyItems(),
				this::recomputeSuggestions);
			status = "OSRS Wiki prices, updated " + new SimpleDateFormat("HH:mm").format(new Date(prices.hourAt))
				+ (membersWorld ? "" : " (F2P items)");
		}
		SwingUtilities.invokeLater(() -> panel.updateSuggestions(list, status));
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged e)
	{
		ensureLoaded();
		if (data == null)
		{
			return;
		}

		int slot = e.getSlot();
		if (slot < 0 || slot >= data.slots.length)
		{
			return;
		}

		GrandExchangeOffer offer = e.getOffer();
		GrandExchangeOfferState state = offer.getState();

		if (state == GrandExchangeOfferState.EMPTY)
		{
			if (System.currentTimeMillis() - loginAt < LOGIN_GRACE_MS)
			{
				return;
			}
			data.slots[slot] = null;
			save();
			refreshOffers();
			return;
		}

		boolean buy = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;

		int itemId = offer.getItemId();
		int qty = offer.getQuantitySold();
		long spent = offer.getSpent();

		LedgerData.SlotSnapshot prev = data.slots[slot];
		int deltaQty;
		long deltaSpent;
		if (prev != null && prev.itemId == itemId && prev.buy == buy && qty >= prev.qty)
		{
			deltaQty = qty - prev.qty;
			deltaSpent = spent - prev.spent;
		}
		else
		{
			deltaQty = qty;
			deltaSpent = spent;
		}
		data.slots[slot] = new LedgerData.SlotSnapshot(itemId, buy, qty, spent);

		if (deltaQty > 0)
		{
			if (buy)
			{
				recordBuy(itemId, deltaQty, deltaSpent);
				geSlotText.show(slot, GeSlotTextOverlay.Kind.BOUGHT, deltaQty);
			}
			else
			{
				Long profit = recordSell(itemId, deltaQty, deltaSpent);
				if (profit != null)
				{
					geSlotText.show(slot, GeSlotTextOverlay.Kind.PROFIT, profit);
				}
				else
				{
					geSlotText.show(slot, GeSlotTextOverlay.Kind.SOLD_UNTRACKED, deltaQty);
				}
			}
		}
		save();
		refreshOffers();
	}

	/** Last inventory counts (noted and unnoted together), to spot grimy herbs turning into clean ones. */
	private java.util.Map<Integer, Integer> lastInv;
	private Recipes recipes;
	private AlchTab alchTab;
	private volatile long invCoins;
	private volatile long alchSessionStart;
	private volatile long alchSessionCasts;
	private volatile long alchSessionProfit;

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() == InventoryID.BANK && data != null && e.getItemContainer() != null)
		{
			// Remember bank coins for the Alch goal (only while the bank is open)
			long coins = 0;
			for (Item it : e.getItemContainer().getItems())
			{
				if (it.getId() == Alch.COINS)
				{
					coins += it.getQuantity();
				}
			}
			if (coins != data.bankCoins)
			{
				data.bankCoins = coins;
				data.bankSeenAt = System.currentTimeMillis();
				save();
			}
			refreshAlch();
			return;
		}
		if (e.getContainerId() != InventoryID.INV)
		{
			return;
		}
		java.util.Map<Integer, Integer> now = new java.util.HashMap<>();
		ItemContainer c = e.getItemContainer();
		if (c != null)
		{
			for (Item it : c.getItems())
			{
				if (it.getId() > 0 && it.getQuantity() > 0)
				{
					now.merge(itemManager.canonicalize(it.getId()), it.getQuantity(), Integer::sum);
				}
			}
		}
		java.util.Map<Integer, Integer> before = lastInv;
		lastInv = now;
		invCoins = now.getOrDefault(Alch.COINS, 0);
		// Banking moves items in and out at once; cleaning never happens with the bank open
		if (before == null || data == null || recipes == null || client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null)
		{
			return;
		}
		// What went down and what went up this tick
		java.util.Map<Integer, Integer> down = new java.util.HashMap<>();
		java.util.Map<Integer, Integer> up = new java.util.HashMap<>();
		for (java.util.Map.Entry<Integer, Integer> b : before.entrySet())
		{
			int d = b.getValue() - now.getOrDefault(b.getKey(), 0);
			if (d > 0)
			{
				down.put(b.getKey(), d);
			}
		}
		for (java.util.Map.Entry<Integer, Integer> n : now.entrySet())
		{
			int u = n.getValue() - before.getOrDefault(n.getKey(), 0);
			if (u > 0)
			{
				up.put(n.getKey(), u);
			}
		}
		if (detectAlch(down, up))
		{
			save();
			refreshPanel();
			return;
		}
		refreshAlch();
		if (down.isEmpty() || up.isEmpty())
		{
			return;
		}
		boolean changed = false;
		for (java.util.Map.Entry<Integer, Integer> made : up.entrySet())
		{
			Recipes.Recipe r = matchRecipe(made.getKey(), made.getValue(), down);
			if (r == null)
			{
				continue;
			}
			double actions = made.getValue() / r.q;
			int[] ids = new int[r.m.length];
			long[] used = new long[r.m.length];
			long[] market = new long[r.m.length];
			for (int i = 0; i < r.m.length; i++)
			{
				ids[i] = (int) r.m[i][0];
				used[i] = Math.round(actions * r.m[i][1]);
				market[i] = marketValue(ids[i]);
				down.merge(ids[i], (int) -used[i], Integer::sum); // can't be claimed twice
			}
			data.transferMade(made.getKey(), made.getValue(), itemManager.getItemComposition(made.getKey()).getName(),
				ids, used, market, System.currentTimeMillis());
			changed = true;
		}
		if (changed)
		{
			save();
			refreshPanel();
		}
	}

	/**
	 * The recipe that explains this tick: every input went down by exactly what making {@code made}
	 * of the output uses, and at least one input has a GE cost we're tracking.
	 */
	private Recipes.Recipe matchRecipe(int outId, int made, java.util.Map<Integer, Integer> down)
	{
		Recipes.Recipe best = null;
		for (Recipes.Recipe r : recipes.making(outId))
		{
			double actions = made / r.q;
			boolean ok = true, tracked = false;
			for (double[] x : r.m)
			{
				int id = (int) x[0];
				double need = actions * x[1];
				int went = down.getOrDefault(id, 0);
				if (id == 995 || Math.abs(went - need) > 0.5)
				{
					ok = id == 995 && ok; // coins (fees) leave the money pouch, not always the inventory
					if (!ok)
					{
						break;
					}
				}
				tracked |= data.positions.containsKey(id);
			}
			if (ok && tracked && (best == null || r.m.length > best.m.length))
			{
				best = r; // the most specific match wins (e.g. smelting with coal vs. without)
			}
		}
		return best;
	}

	/**
	 * High Alchemy: one item and a nature rune left the inventory and exactly the item's High Alch value in
	 * coins arrived. Counts the cast and its profit (item cost from the GE if we know it, otherwise today's price).
	 */
	private boolean detectAlch(java.util.Map<Integer, Integer> down, java.util.Map<Integer, Integer> up)
	{
		Integer coins = up.get(Alch.COINS);
		if (coins == null || down.getOrDefault(Alch.NATURE_RUNE, 0) != 1)
		{
			return false;
		}
		for (java.util.Map.Entry<Integer, Integer> d : down.entrySet())
		{
			int id = d.getKey();
			if (id == Alch.NATURE_RUNE || id == Alch.FIRE_RUNE || d.getValue() != 1)
			{
				continue;
			}
			PriceService.Item it = prices.mapping.get(id);
			if (it == null || it.highAlch != coins)
			{
				continue;
			}
			long[] untracked = new long[1];
			long cost = data.consume(id, 1, untracked) + untracked[0] * Alch.buyPrice(prices, id);
			cost += data.consume(Alch.NATURE_RUNE, 1, untracked) + untracked[0] * Alch.buyPrice(prices, Alch.NATURE_RUNE);
			int fires = down.getOrDefault(Alch.FIRE_RUNE, 0);
			if (fires > 0)
			{
				cost += data.consume(Alch.FIRE_RUNE, fires, untracked) + untracked[0] * Alch.buyPrice(prices, Alch.FIRE_RUNE);
			}
			long profit = coins - cost;
			data.alchProfit += profit;
			data.alchCasts++;
			alchSessionProfit += profit;
			alchSessionCasts++;
			return true;
		}
		return false;
	}

	/** Rebuild the Alch page: goal progress, this session, the best items for this world type. */
	private void refreshAlch()
	{
		AlchTab tab = alchTab;
		if (tab == null || prices == null)
		{
			return;
		}
		AlchTab.View v = new AlchTab.View();
		LedgerData d = data;
		v.bondPrice = Alch.buyPrice(prices, Alch.BOND);
		String g = config.alchGoal().trim();
		v.goalIsBond = g.isEmpty() || g.equalsIgnoreCase("bond");
		v.goal = v.goalIsBond ? (v.bondPrice > 0 ? v.bondPrice : 10_000_000L) : Math.max(1, Suggester.parse(g, 10_000_000L));
		v.invCoins = invCoins;
		v.bankCoins = d == null ? -1 : d.bankCoins;
		v.bankSeenAt = d == null ? 0 : d.bankSeenAt;
		v.casts = alchSessionCasts;
		v.profit = alchSessionProfit;
		v.sessionMs = System.currentTimeMillis() - alchSessionStart;
		v.allTimeProfit = d == null ? 0 : d.alchProfit;
		v.allTimeCasts = d == null ? 0 : d.alchCasts;
		v.natureRune = Alch.buyPrice(prices, Alch.NATURE_RUNE);
		v.members = membersWorld;
		v.magicLevel = loggedIn ? client.getRealSkillLevel(Skill.MAGIC) : 0;
		v.picks = Alch.best(prices, membersWorld, config.alchFireStaff(), this::boughtRecently, 10);
		SwingUtilities.invokeLater(() -> tab.update(v));
	}

	/** What an input you didn't buy through the GE is worth: what it sells for right now. */
	private long marketValue(int id)
	{
		if (id == 995)
		{
			return 1;
		}
		PriceService.Window h = prices.hour.get(id);
		return h == null ? 0 : Math.max(0, h.low);
	}

	private Processing processing()
	{
		return new Processing(prices, recipes, this::realLevel, membersWorld, config.applyTax());
	}

	/** Real (unboosted) level by skill name, e.g. "Herblore"; 0 if unknown. */
	private int realLevel(String skill)
	{
		try
		{
			return client.getRealSkillLevel(Skill.valueOf(skill.trim().toUpperCase().replace(' ', '_')));
		}
		catch (IllegalArgumentException e)
		{
			return 0;
		}
	}

	/** Items already in your GE slots or still being flipped: never suggested again until they're done. */
	private java.util.function.IntPredicate busyItems()
	{
		java.util.Set<Integer> busy = new java.util.HashSet<>();
		for (OfferView o : lastOffers)
		{
			busy.add(o.itemId);
		}
		LedgerData d = data;
		if (d != null)
		{
			try
			{
				for (LedgerData.Flip f : new java.util.ArrayList<>(d.history))
				{
					if (f.open)
					{
						busy.add(f.itemId);
					}
				}
			}
			catch (java.util.ConcurrentModificationException ignored)
			{
				// the game thread changed history mid-copy; the next refresh catches it
			}
		}
		return busy::contains;
	}

	private static java.util.Set<Integer> offerItems(java.util.List<OfferView> views)
	{
		java.util.Set<Integer> s = new java.util.HashSet<>();
		for (OfferView v : views)
		{
			s.add(v.itemId);
		}
		return s;
	}

	/** Units of an item bought in its current 4-hour buy-limit window. */
	private long boughtRecently(int itemId)
	{
		LedgerData d = data;
		LedgerData.LimitWindow w = d == null ? null : d.limitWindow(itemId, System.currentTimeMillis());
		return w == null ? 0 : w.bought;
	}

	private void recordBuy(int itemId, long qty, long cost)
	{
		LedgerData.Position p = data.positions.get(itemId);
		if (p == null)
		{
			data.positions.put(itemId, new LedgerData.Position(qty, cost));
		}
		else
		{
			p.qty += qty;
			p.totalCost += cost;
		}

		long now = System.currentTimeMillis();
		String itemName = itemManager.getItemComposition(itemId).getName();
		PriceService.Item mapped = prices.mapping.get(itemId);
		data.addBought(itemId, qty, now, itemName, mapped == null ? 0 : mapped.limit);
		LedgerData.Flip f = data.openFlip(itemId);
		if (f == null)
		{
			f = new LedgerData.Flip(now, itemId, itemName);
		}
		f.boughtQty += qty;
		f.boughtCost += cost;
		f.time = now;
		data.moveToTop(f);
		refreshPanel();
	}

	private Long recordSell(int itemId, long qty, long gross)
	{
		LedgerData.Position p = data.positions.get(itemId);
		if (p == null || p.qty <= 0)
		{
			// Bought before the plugin was installed, or from somewhere else: no cost basis, so no profit shown
			return null;
		}

		long matched = Math.min(qty, p.qty);
		long pricePer = gross / qty;
		long tax = config.applyTax() ? Gp.taxPerItem(pricePer) * qty : 0;
		long net = gross - tax;

		long proceeds = net * matched / qty;
		long cost = p.totalCost * matched / p.qty;
		long profit = proceeds - cost;

		p.qty -= matched;
		p.totalCost -= cost;
		if (p.qty <= 0)
		{
			data.positions.remove(itemId);
		}

		String name = itemManager.getItemComposition(itemId).getName();
		long now = System.currentTimeMillis();

		// One entry per flip: it stays open until everything bought has been sold
		LedgerData.Flip f = data.openFlip(itemId);
		if (f == null)
		{
			f = new LedgerData.Flip(now, itemId, name);
		}
		f.qty += matched;
		f.profit += profit;
		f.cost += cost;
		f.proceeds += proceeds;
		f.time = now;
		boolean closed = !data.positions.containsKey(itemId);
		if (closed)
		{
			f.open = false;
			data.allTimeFlips++;
			sessionFlips++;
		}
		data.addEvent(profit, closed ? 1 : 0, f.profit, f.name);
		if (client.getLocalPlayer() != null)
		{
			playerName = client.getLocalPlayer().getName();
		}
		crew.markDirty();
		final LedgerData snapshotForCrew = data;
		final String nameForCrew = playerName;
		executor.execute(() -> crew.maybeSend(config, nameForCrew, snapshotForCrew.eventsCopy(), crewToken(), this::saveCrewToken));
		data.moveToTop(f);

		data.allTimeProfit += profit;
		sessionProfit += profit;

		if (config.showFloatingText())
		{
			floatingText.show(itemId, profit);
		}
		if (config.showChatMessage())
		{
			String col = profit >= 0 ? "2ecc71" : "e74c3c";
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(String.format("Flip Ledger: %,d x %s <col=%s>%s</col>",
					matched, name, col, Gp.signed(profit)))
				.build());
		}
		refreshPanel();
		return profit;
	}

	private void ensureLoaded()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return;
		}
		if (profile.equals(loadedProfile) && data != null)
		{
			return;
		}

		LedgerData loaded = null;
		String json = configManager.getRSProfileConfiguration(FlipLedgerConfig.GROUP, DATA_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				loaded = gson.fromJson(json, LedgerData.class);
			}
			catch (Exception ex)
			{
				log.warn("Flip Ledger: couldn't read saved data, starting fresh", ex);
			}
		}
		if (loaded == null)
		{
			loaded = new LedgerData();
		}
		if (loaded.slots == null || loaded.slots.length != 8)
		{
			loaded.slots = new LedgerData.SlotSnapshot[8];
		}
		if (loaded.positions == null)
		{
			loaded.positions = new java.util.HashMap<>();
		}
		if (loaded.history == null)
		{
			loaded.history = new java.util.ArrayList<>();
		}
		if (loaded.events == null)
		{
			loaded.events = new java.util.ArrayList<>();
		}
		data = loaded;
		loadedProfile = profile;
	}

	private void save()
	{
		if (data != null && loadedProfile != null)
		{
			configManager.setRSProfileConfiguration(FlipLedgerConfig.GROUP, DATA_KEY, gson.toJson(data));
		}
	}

	private void resetSession()
	{
		sessionStart = System.currentTimeMillis();
		sessionProfit = 0;
		sessionFlips = 0;
	}

	private void resetSessionFromPanel()
	{
		// Panel buttons run on the Swing thread; numbers are simple fields so this is safe enough
		resetSession();
		refreshPanel();
	}

	private void clearHistoryFromPanel()
	{
		if (data != null)
		{
			data.history.clear();
			data.allTimeProfit = 0;
			data.allTimeFlips = 0;
			save();
		}
		resetSession();
		refreshPanel();
	}

	/** Client thread only: snapshot the 8 GE slots for the side panel. */
	private void refreshOffers()
	{
		if (panel == null)
		{
			return;
		}
		final java.util.List<OfferView> views = new java.util.ArrayList<>();
		GrandExchangeOffer[] all = client.getGameState() == GameState.LOGGED_IN ? client.getGrandExchangeOffers() : null;
		if (all != null)
		{
			for (int i = 0; i < all.length; i++)
			{
				GrandExchangeOffer o = all[i];
				if (o == null || o.getState() == GrandExchangeOfferState.EMPTY || o.getItemId() <= 0)
				{
					continue;
				}
				GrandExchangeOfferState st = o.getState();
				boolean buy = st == GrandExchangeOfferState.BUYING
					|| st == GrandExchangeOfferState.BOUGHT
					|| st == GrandExchangeOfferState.CANCELLED_BUY;
				OfferView.Status status = (st == GrandExchangeOfferState.BOUGHT || st == GrandExchangeOfferState.SOLD)
					? OfferView.Status.COMPLETE
					: (st == GrandExchangeOfferState.CANCELLED_BUY || st == GrandExchangeOfferState.CANCELLED_SELL)
					? OfferView.Status.CANCELLED : OfferView.Status.ACTIVE;
				long avg = -1;
				if (!buy && data != null)
				{
					LedgerData.Position p = data.positions.get(o.getItemId());
					if (p != null && p.qty > 0)
					{
						avg = p.totalCost / p.qty;
					}
				}
				String name = itemManager.getItemComposition(o.getItemId()).getName();
				views.add(new OfferView(i, o.getItemId(), name, buy, status, o.getQuantitySold(), o.getTotalQuantity(),
					o.getPrice(), o.getSpent(), avg));
			}
		}
		boolean itemsChanged = !offerItems(views).equals(offerItems(lastOffers));
		lastOffers = views;
		if (itemsChanged)
		{
			executor.execute(this::recomputeSuggestions); // refill suggestions when an item enters or leaves the GE
		}
		SwingUtilities.invokeLater(() -> panel.updateOffers(views));
	}

	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		final long profit = sessionProfit;
		final int flips = sessionFlips;
		final long elapsed = System.currentTimeMillis() - sessionStart;
		final LedgerData snapshot = data;
		SwingUtilities.invokeLater(() -> panel.update(profit, flips, elapsed, snapshot));
		refreshAlch();
	}
}
