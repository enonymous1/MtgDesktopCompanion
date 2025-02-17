package org.magic.api.pricers.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.magic.api.beans.MagicCard;
import org.magic.api.beans.MagicPrice;
import org.magic.api.beans.enums.EnumMarketType;
import org.magic.api.interfaces.abstracts.AbstractPricesProvider;
import org.magic.services.MTGControler;
import org.magic.services.network.RequestBuilder;
import org.magic.services.network.RequestBuilder.METHOD;
import org.magic.services.network.URLTools;
import org.magic.tools.UITools;

public class BigOrBitCardsPricer extends AbstractPricesProvider {

	@Override
	public String getName() {
		return "BigOrBitCards";
	}
	
	
	 @Override
	public EnumMarketType getMarket() {
		 return EnumMarketType.EU_MARKET;
	}

	@Override
	protected List<MagicPrice> getLocalePrice(MagicCard card) throws IOException {
		
		
		var extra="";
		
		if(card.isExtendedArt())
			extra=" (Extended Art)";
		else if(card.isShowCase())
			extra=" (Showcase)";
		else if(card.isBorderLess())
			extra=" (Borderless Art)";
		
		logger.info(getName() + " looking for " + card+extra);
		
		var doc = RequestBuilder.build().method(METHOD.GET).url("https://www.bigorbitcards.co.uk/").setClient(URLTools.newClient())
				.addContent("search_performed", "Y")
				.addContent("product_variants","N")
				.addContent("match","All")
				.addContent("q",card.getName() + extra)
				.addContent("pname","Y")
				.addContent("category_name","Magic the Gathering")
				.addContent("subcats","Y")
				.addContent("tx_features[]","All")
				.addContent("cid","1000062")
				.addContent("dispatch[products.search]","")
				.addContent("features_hash","7-Y")
				.addContent("items_per_page","96")
				.toHtml().select("div.ty-compact-list__content");
		
		var ret = new ArrayList<MagicPrice>();
		
		doc.forEach(e->{
			
			var mp = new MagicPrice();
				mp.setSeller(getName());
				mp.setSite(getName());
				mp.setCurrency(Currency.getInstance(Locale.UK));
				mp.setCountry(Locale.UK.getDisplayCountry(MTGControler.getInstance().getLocale()));
				mp.setLanguage("English");
				mp.setMagicCard(card);
				mp.setFoil(e.select("bdi.compact_bdi_title").first().text().contains("(Foil)"));
				mp.setUrl(e.select("bdi.compact_bdi_title a").first().attr("href"));
				mp.setSellerUrl(mp.getUrl());
				
				var spanPrices=e.select("span.ty-qty-in-stock span");
				mp.setQuality(spanPrices.get(0).text().trim());
				var ind = spanPrices.get(1).text().indexOf('£')+1;
				mp.setValue(UITools.parseDouble(spanPrices.get(1).text().substring(ind)));
				
				notify(mp);
				
			ret.add(mp);
		});
		
		logger.info(getName() + " found" + ret.size() + " items");
		return ret;
	}

}
