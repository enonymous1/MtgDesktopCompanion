package org.magic.api.decksniffer.impl;

import static org.magic.tools.MTG.getEnabledPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.magic.api.beans.MagicCard;
import org.magic.api.beans.MagicDeck;
import org.magic.api.beans.MagicEdition;
import org.magic.api.beans.RetrievableDeck;
import org.magic.api.beans.enums.MTGColor;
import org.magic.api.interfaces.MTGCardsProvider;
import org.magic.api.interfaces.abstracts.AbstractDeckSniffer;
import org.magic.services.MTGControler;
import org.magic.services.network.URLTools;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstNode;

public class MTGSalvationDeckSniffer extends AbstractDeckSniffer {

	private Map<String,Integer> mapCodes;
	
	@Override
	public STATUT getStatut() {
		return STATUT.DEPRECATED;
	}
	
	public MTGSalvationDeckSniffer() {
		super();
		
		mapCodes = new HashMap<>();
		mapCodes.put("Standard", 32);
		mapCodes.put("Casual", 16);
		mapCodes.put("Classic", 64);
		mapCodes.put("Commander", 2);
		mapCodes.put("Legacy", 4);
		mapCodes.put("Vintage", 8);
		mapCodes.put("Modern", 1);
		
	}
	

	@Override
	public String[] listFilter() {
		return mapCodes.keySet().toArray(new String[mapCodes.size()]);
	}

	@Override
	public MagicDeck getDeck(RetrievableDeck info) throws IOException {

		String url = info.getUrl() + "#Details:deck-export";
		var deck = info.toBaseDeck();

		var d = URLTools.extractAsHtml(url);

		deck.setDescription(info.getUrl().toString() + "\n" + d.select("section.guide div").text());

		for (Element a : d.select("span.deck-type"))
			deck.getTags().add(a.text());

		String plainDeck = d.select("section.deck-export-section pre").get(1).text();

		var sideboard = false;

		List<String> elements = new ArrayList<>(Arrays.asList(plainDeck.split("\n")));
		elements.remove(0);
		String cardName = null;
		for (String s : elements) {
			if (s.toLowerCase().startsWith("sideboard")) {
				sideboard = true;
			} else if (s.length() > 1) {
				try {
					var qte = Integer.parseInt(s.substring(0, s.indexOf(' ')));
					cardName = s.substring(s.indexOf(' '), s.length()).trim();
					MagicEdition ed = null;
					if (MagicCard.isBasicLand(cardName)) {
						ed = new MagicEdition(MTGControler.getInstance().get("default-land-deck"));
					}
					MagicCard mc = getEnabledPlugin(MTGCardsProvider.class)
							.searchCardByName( cardName, ed, true).get(0);
					if (!sideboard) {
						deck.getMain().put(mc, qte);
					} else {
						deck.getSideBoard().put(mc, qte);
					}
					notify(mc);

				} catch (Exception e) {
					logger.error("error getting" + cardName +" : " + e);
				}
			}
		}

		return deck;
	}

	public List<RetrievableDeck> getDeckList() throws IOException {

		String url = getString("URL") + "/decks?filter-format=" + getFormatCode(getString("FORMAT"))+ "&filter-deck-time-frame=" + getString("FILTER");

		List<RetrievableDeck> list = new ArrayList<>();

		var nbPage = 1;
		

		for (var i = 1; i <= getInt("MAX_PAGE"); i++) {
			String link = url + "&page=" + nbPage;
			logger.debug("sniff url : " + link);

			var d = URLTools.extractAsHtml(link);

			Elements e = null;

			e = d.select("tr.deck-row");

			for (Element cont : e) {
				var deck = new RetrievableDeck();
				deck.setName(cont.select("a.deck-name").html());
				deck.setAuthor(cont.select("small.deck-credit a").text());
				try {
					deck.setUrl(new URL(getString("URL") + "/" + cont.select("a.deck-name").attr("href")).toURI());
				} catch (URISyntaxException e1) {
					deck.setUrl(null);
				}
				deck.setDescription(cont.select("span.deck-type").html());
				deck.setColor(parseColor(cont.select("script").html()));
				list.add(deck);
			}
			nbPage++;
		}
		return list;
	}

	private String manajson;

	private String parseColor(String string) {
		AstNode node = new Parser().parse(string, "", 1);
		node.visit(n -> {
			manajson = n.toSource();
			return false;
		});

		manajson = manajson.substring(manajson.indexOf("series") + "series: ".length(), manajson.length() - 8);

		var arr = URLTools.toJson(manajson).getAsJsonArray();
		manajson = "";
		var hascolor = false;
		var build = new StringBuilder(manajson);
		for (var i = 0; i < arr.size(); i++) {
			var obj = arr.get(i).getAsJsonObject();
			var c = MTGColor.colorByName(obj.get("name").getAsString());
			var tab = obj.get("data").getAsJsonArray();
			hascolor = false;
			for (var j = 0; j < tab.size(); j++) {
				if (tab.get(j).getAsInt() > 0)
					hascolor = true;
			}
			if (hascolor && c!=null) {
				build.append(c.toManaCode());
			}
		}

		return build.toString();
	}

	private Integer getFormatCode(String property) {
		return mapCodes.get(property);
	}


	@Override
	public String getName() {
		return "MTGSalvation";
	}
	
	@Override
	public Map<String, String> getDefaultAttributes() {
		return Map.of("URL", "https://www.mtgsalvation.com/",
								"MAX_PAGE", "2",
								"FORMAT", "Modern",
								"FILTER", "1");// HOT=1, NEW=2, TOPWEEK=3,TOPMONTH=4,TOPALLTIME=5
	}

	@Override
	public String getVersion() {
		return "2.0";
	}

}
