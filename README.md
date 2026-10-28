# Flip Ledger

A free RuneLite plugin that tracks your Grand Exchange flips. It shows what you really made after tax, keeps an eye on your offers and buy limits, and checks prices while you set up an offer. Your flip history stays on your computer.

Flip Ledger is **display only**: it never clicks, types or fills in prices for you.

## Features

- **Profit after tax:** every sale is matched against what you paid, minus the 2% GE tax (nothing under 50 gp, capped at 5M per item).
- **Floating text:** green +gp / red -gp above your character, and over the GE offer slot, as offers fill.
- **Sidebar:**
  - **Session tab:** session profit, gp/hr, flip count, all-time profit, and one entry per flip (RS-Flipper style).
  - **Offers tab:** your current offers with progress bars, and your 4-hour buy limits per item.
  - **Settings and Info tabs.**
- **Price check:** while you set up an offer, a small box shows:
  - recommended buy and sell prices, and your break-even;
  - profit for the quantity you entered;
  - the latest trade, and the week's trend;
  - how much of the buy limit you have left;
  - whether processing the item would pay better.
- **Processing:** when you turn something you bought into something else (ore to bars, grimy to clean herbs to unfinished potions, logs to arrow shafts to headless arrows...), the cost carries over, so the final sale shows your real profit.
- **Price drop alerts:** a chat warning and notification when something you hold or are selling drops below your break-even or falls fast. It includes a processing tip when making something from it is worth more.
- **Suggestions:** steady flips across every tradeable item (or just the groups you pick: potions, food, runes, ammo, herbs, gear and more), one at a time. Skip or block any you don't want. They're sized to your budget, fill time and remaining buy limit, and avoid items that are sliding or spiking.
- **Per account:** each account keeps its own history (for example, a main and an F2P account).

## Data and privacy

- **Prices:** downloaded from the public [OSRS Wiki Real-time Prices API](https://prices.runescape.wiki/), only while you're logged in. It uses bulk files, refreshed every 10–30 minutes, about 10 small requests an hour. Turn **Suggestions** off to stop most of these.
- **Recipes:** bundled with the plugin; no requests.
- **Crew leaderboard (optional, off by default):** if you turn it on and enter a server address and crew key, the plugin sends your RuneScape name, profit and flip counts for the current 1-hour, 12-hour and 24-hour windows to that server, at most once a minute. That server is run by your group, not by RuneLite. Nothing is sent unless you turn it on.

## Crew leaderboard (for group leaders)

`leaderboard/` holds a small Cloudflare Worker and a web page you can host for your own group, both free. See `leaderboard/DEPLOY.md`.

## Credits

- Prices: [OSRS Wiki Real-time Prices API](https://prices.runescape.wiki/).
- Recipe data (`src/main/resources/com/flipledger/recipes.json`): taken from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/), licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

## Developing

1. Open the folder in IntelliJ (JDK 11+), and let Gradle load.
2. Run the Gradle task **runClient**. It opens RuneLite in developer mode with the plugin loaded.
