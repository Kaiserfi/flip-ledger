package com.flipledger;

import java.awt.BorderLayout;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The flipping strategy and the mistakes to avoid, in plain words.
 */
class InfoTab extends JPanel
{
	private static final String CSS =
		"body{color:#d0d0d0;margin:0}"
			+ "h3{color:#ffffff;margin:10px 0 3px 0;font-size:11px}"
			+ "ul{margin:2px 0 2px 14px;padding:0}"
			+ "li{margin:0 0 3px 0}"
			+ "b{color:#ffffff}"
			+ ".g{color:#2ecc71}.r{color:#e74c3c}.y{color:#f1c40f}";

	private static final String HTML = "<html><head><style>" + CSS + "</style></head><body>"
		+ "<h3>The core idea</h3>"
		+ "Buy where sellers are filling, sell where buyers are paying, and keep the gap after the "
		+ "<b>2% GE tax</b> (none under 50 gp, capped at 5M per item)."

		+ "<h3>Before every buy</h3><ul>"
		+ "<li><b>Gap beats the tax.</b> At least about 0.5% left after tax.</li>"
		+ "<li><b>Steady prices.</b> Buyers and sellers stay in a tight band.</li>"
		+ "<li><b>Buyers not falling.</b> The buyer price is your exit.</li>"
		+ "<li><b>Lowest recent buyer above your break-even.</b></li>"
		+ "<li><b>Sold vs bought balanced.</b> Far more sold means people are dumping.</li></ul>"

		+ "<h3>Week chart labels</h3><ul>"
		+ "<li><span class='g'>Steady</span>: safe to flip.</li>"
		+ "<li><span class='y'>Settled</span>: fell earlier, flat now. Flip it, but sell the moment it fills.</li>"
		+ "<li><span class='r'>Sliding</span>: falling. Don't buy.</li>"
		+ "<li><span class='r'>Spiking</span>: just jumped. Don't buy; it usually falls back. "
		+ "(Fine to <i>sell</i> into a spike.)</li></ul>"

		+ "<h3>Pricing</h3><ul>"
		+ "<li>Use the <b>typical</b> price over the last 30 minutes, not one odd trade.</li>"
		+ "<li>Never chase. If it won't fill at your price, move on.</li>"
		+ "<li>On cheap items, bid 1 gp above the crowd to get to the front of the queue.</li>"
		+ "<li><b>Check the digits</b> before Confirm. Three extra zeros cost real money.</li></ul>"

		+ "<h3>Selling</h3><ul>"
		+ "<li>List as soon as it fills, even partial fills.</li>"
		+ "<li>If buyers start falling, sell at the <b>latest buyer price</b> right away. "
		+ "Break-even is the floor, not a target to wait for.</li>"
		+ "<li>An offer that fills instantly usually means you could have asked a bit more.</li></ul>"

		+ "<h3>Slots and size</h3><ul>"
		+ "<li>Keep <b>1 to 2 slots free</b> for selling, or bought items pile up in your bag.</li>"
		+ "<li>Size so a 1% dip can't cost you much. Bigger stakes need stricter rules.</li>"
		+ "<li>Keep 30 to 40% of your gp as cash, especially overnight.</li>"
		+ "<li>Steady, busy, mid-priced items (10k to 1M) usually beat one slow expensive item.</li></ul>"

		+ "<h3>Overnight</h3><ul>"
		+ "<li>Steady items only, bid at or a bit under the typical seller price.</li>"
		+ "<li>Lowball bids on steady items can catch overnight dumps.</li>"
		+ "<li>In the morning, check prices again before you sell.</li></ul>"

		+ "<h3>Where losses come from</h3><ul>"
		+ "<li>Buying a sliding or spiking item.</li>"
		+ "<li>Holding through a drop instead of getting out.</li>"
		+ "<li>Too much size for a tiny margin.</li></ul>"

		+ "<h3>Stay safe</h3><ul>"
		+ "<li>Nobody gives away free gp. \"Trade me for a video\" and doubling are scams.</li>"
		+ "<li>Flip Ledger only shows information. Every click is yours.</li></ul>"
		+ "</body></html>";

	InfoTab()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JEditorPane text = new JEditorPane("text/html", HTML);
		text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setEditable(false);
		text.setFocusable(false);
		text.setOpaque(false);
		text.setBorder(null);
		add(text, BorderLayout.CENTER);
	}
}
