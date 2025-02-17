package org.magic.api.providers.impl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.apache.commons.beanutils.BeanUtils;
import org.magic.api.beans.MagicCard;
import org.magic.api.beans.MagicCardNames;
import org.magic.api.beans.MagicEdition;
import org.magic.api.beans.MagicFormat;
import org.magic.api.beans.MagicFormat.AUTHORIZATION;
import org.magic.api.beans.MagicRuling;
import org.magic.api.beans.enums.MTGBorder;
import org.magic.api.beans.enums.MTGColor;
import org.magic.api.beans.enums.MTGFinishes;
import org.magic.api.beans.enums.MTGFrameEffects;
import org.magic.api.beans.enums.MTGLayout;
import org.magic.api.beans.enums.MTGRarity;
import org.magic.api.criterias.MTGCrit;
import org.magic.api.criterias.MTGQueryBuilder;
import org.magic.api.criterias.QueryAttribute;
import org.magic.api.criterias.builders.ScryfallCriteriaBuilder;
import org.magic.api.interfaces.abstracts.AbstractCardsProvider;
import org.magic.services.MTGConstants;
import org.magic.services.network.URLTools;
import org.magic.services.threads.MTGRunnable;
import org.magic.services.threads.ThreadManager;
import org.magic.tools.InstallCert;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kitfox.svg.app.beans.SVGIcon;

public class ScryFallProvider extends AbstractCardsProvider {

	private static final String RELEASED_AT = "released_at";
	private static final String CMC = "cmc";
	private static final String GAMES = "games";
	private static final String LAYOUT = "layout";
	private static final String OVERSIZED = "oversized";
	private static final String REPRINTED = "reprinted";
	private static final String CARDS = "/cards/";
	private static final String ILLUSTRATION_ID = "illustration_id";
	private static final String FRAME = "frame";
	private static final String SEARCH_Q = "search?q=";
	private static final String COLOR = "color";
	private static final String WATERMARK = "watermark";
	private static final String TYPE_LINE = "type_line";
	private static final String POWER = "power";
	private static final String LOYALTY = "loyalty";
	private static final String TOUGHNESS = "toughness";
	private static final String RARITY = "rarity";
	private static final String MULTIVERSE_ID = "multiverse_id";
	private static final String COLOR_IDENTITY="color_identity";
	private static final String DIGITAL = "digital";
	private static final String COLORS = "colors";
	private static final String COLLECTOR_NUMBER = "collector_number";
	private static final String ARTIST = "artist";
	private static final String CARD_FACES = "card_faces";
	private static final String BORDER = "border_color";
	private static final String NAME = "name";
	private static final String FINISHES ="finishes";
	private static final String LOAD_CERTIFICATE = "LOAD_CERTIFICATE";
	private static final String BULK_FILE_URL="https://archive.scryfall.com/json/scryfall-all-cards.json";
	private String baseURI = "";
	
	public ScryFallProvider() {
		super();
		if(getBoolean(LOAD_CERTIFICATE))
		{
			try {
				InstallCert.installCert("scryfall.com");
				setProperty(LOAD_CERTIFICATE, FALSE);
			} catch (Exception e1) {
				logger.error(e1);
			}
		}
		baseURI=getString("URL");
	}
	
	
	public SVGIcon getSvgFileFor(String idSet)
	{
		var ic = new SVGIcon();
		ic.setSvgURI(URI.create("https://c2.scryfall.com/file/scryfall-symbols/sets/"+idSet.toLowerCase()+".svg"));
		ic.setAntiAlias(true);
		ic.setAutosize(1);
		return ic;
	}
	
	public void downloadBulkFile(File f) throws IOException
	{
		URLTools.download(BULK_FILE_URL, f);
	}

	
	
	@Override
	public Map<String, String> getDefaultAttributes() {
		return Map.of(LOAD_CERTIFICATE, "true",
								"URL", "https://api.scryfall.com",
								"MULTILANG",FALSE,
								"LOAD_RULING",FALSE);
	}
	
	@Override
	public void init() {
		baseURI=getString("URL");
	}
	
	
	
	
	@Override
	public MagicCard getCardById(String id,MagicEdition ed) throws IOException {
		try {
			return searchCardByCriteria("id", id, ed, true).get(0);
		}catch(IndexOutOfBoundsException e)
		{
			return null;
		}
	}
	
	
	@Override
	public MagicCard getCardByScryfallId(String crit) throws IOException {
		return getCardById(crit);
	}
	

	public JsonObject getJsonFor(MagicCard mc)
	{
		String url = baseURI + CARDS + mc.getCurrentSet().getId().toLowerCase() + "/" + mc.getCurrentSet().getNumber();
		try {
			return URLTools.extractAsJson(url).getAsJsonObject();
		} catch (IOException e) {
			return null;
		}
	}
	
	

	@Override
	public List<MagicCard> listAllCards() throws IOException {
		throw new IOException("Not Implemented");
	}
	
	
	@Override
	public MagicCard getCardByArenaId(String id) throws IOException {
		return searchCardByCriteria("arenaId",id, null, true).get(0);
	}
	
	@Override
	public List<MagicCard> searchCardByCriteria(String att, String crit, MagicEdition me, boolean exact) throws IOException {
		List<MagicCard> list = new ArrayList<>();

		String comparator = crit;

		if (exact)
			comparator = "!\"" + crit + "\"";

		var url = new StringBuilder(baseURI);
				url.append(CARDS);
				
		if (att.equals(NAME))
			url.append(SEARCH_Q).append(URLTools.encode("++" + comparator + " include:extras"));
		else if (att.equals("custom"))
			url.append(SEARCH_Q).append(URLTools.encode(crit));
		else if (att.equals(SET_FIELD))
			url.append(SEARCH_Q).append(URLTools.encode("++e:" + crit));
		else if (att.equals("id"))
			url.append(URLTools.encode(crit));
		else
			url.append(SEARCH_Q).append(URLTools.encode(att + ":" + comparator + " include:extras"));

		if (me != null)
			url.append("%20").append(URLTools.encode("e:" + me.getId()));

		var hasMore = true;
		while (hasMore) {

			logger.debug(URLDecoder.decode(url.toString(), MTGConstants.DEFAULT_ENCODING.displayName()));
			try {
				JsonElement el = URLTools.extractAsJson(url.toString()) ;

				if (att.equals("id")) {
					list.add(loadCard(el.getAsJsonObject(), exact, crit));
					hasMore = false;
				} else {
					var jsonList = el.getAsJsonObject().getAsJsonArray("data");
					for (var i = 0; i < jsonList.size(); i++) {
						MagicCard mc = loadCard(jsonList.get(i).getAsJsonObject(), exact, crit);
						list.add(mc);
					}
					hasMore = el.getAsJsonObject().get("has_more").getAsBoolean();

					if (hasMore)
						url = new StringBuilder(el.getAsJsonObject().get("next_page").getAsString());

					Thread.sleep(50);
				}
			} catch (IOException e) {
				logger.error("erreur", e);
				hasMore = false;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error("erreur", e);
				hasMore = false;
			}
		}
		return list;
	}

	@Override
	public MagicCard getCardByNumber(String id, MagicEdition me) throws IOException {
		String url = baseURI + CARDS + me.getId().toLowerCase() + "/" + id;
		var root =  URLTools.extractAsJson(url).getAsJsonObject();
		return loadCard(root, true, null);
	}

	@Override
	public List<MagicEdition> loadEditions() throws IOException {
			String url = baseURI + "/sets";
			var root = URLTools.extractAsJson(url).getAsJsonObject(); 
			List<MagicEdition> eds = new ArrayList<>();
			for (var i = 0; i < root.get("data").getAsJsonArray().size(); i++) {
				var e = root.get("data").getAsJsonArray().get(i).getAsJsonObject();
				MagicEdition ed = generateEdition(e.getAsJsonObject());
				eds.add(ed);
			}
		
		return eds;
	}

	@Override
	public String[] getLanguages() {
		return new String[] { "en","es","fr","de","it","pt","ja","ru","zhs","he","ar"};
	}

	@Override
	public List<QueryAttribute> loadQueryableAttributs() {
		List<QueryAttribute> arr = new ArrayList<>();
		
		for(String s :Lists.newArrayList(NAME, "custom", "type", "oracle", "mana",RARITY, "cube", ARTIST, "flavor", WATERMARK, BORDER, FRAME))
		{
			arr.add(new QueryAttribute(s,String.class));
		}
		
		for(String s :Lists.newArrayList(CMC, POWER, TOUGHNESS,LOYALTY))
		{
			arr.add(new QueryAttribute(s,Integer.class));
		}
		
		for(String s :Lists.newArrayList("is","foil","nonfoil",OVERSIZED,"promo","reprint","story_spotlight","variation"))
		{
			arr.add(new QueryAttribute(s,Boolean.class));
		}
		
		arr.add(new QueryAttribute("set",MagicEdition.class));
		arr.add(new QueryAttribute(COLOR, MTGColor.class));
		arr.add(new QueryAttribute(COLOR_IDENTITY, MTGColor.class));
		arr.add(new QueryAttribute(LAYOUT,MTGLayout.class));
		arr.add(new QueryAttribute(FINISHES,MTGFinishes.class));
		return arr;
	}
	
	@Override
	public MTGQueryBuilder<?> getMTGQueryManager() {
		MTGQueryBuilder<String> b= new ScryfallCriteriaBuilder();
		
		initBuilder(b);
		
		return b;
	}
	
	

	@Override
	public List<MagicCard> searchByCriteria(MTGCrit<?>[] crits) throws IOException {
		throw new IOException("Not Yet Implemented");
	}
	
	
	
	

	@Override
	public String getVersion() {
		return "2.0";
	}

	@Override
	public STATUT getStatut() {
		return STATUT.BETA;
	}

	@Override
	public String getName() {
		return "ScryFall";
	}

	private MagicCard loadCard(JsonObject obj, boolean exact, String search){
		
		try {
			return cacheCards.get(obj.get("id").getAsString(), new Callable<MagicCard>() {
				
				@Override
				public MagicCard call() throws Exception {
					return generateCard(obj, exact, search);
				}
			});
		} catch (ExecutionException e) {
			logger.error("error loading cards", e);
			return null;
		}
		
		
	}
	

	private MagicCard generateCard(JsonObject obj, boolean exact, String search) throws IOException {
		var mc = new MagicCard();

		mc.setId(obj.get("id").getAsString());
		mc.setScryfallId(mc.getId());
		mc.setName(obj.get(NAME).getAsString());
		mc.setCmc(obj.get(CMC).getAsInt());
		mc.setLayout(MTGLayout.parseByLabel(obj.get(LAYOUT).getAsString()));
		mc.setOversized(obj.get(OVERSIZED).getAsBoolean());
		
		try {
			mc.setReprintedCard(obj.get(REPRINTED).getAsBoolean());
		} catch (NullPointerException e) {
			mc.setReprintedCard(false);
		}

		try {
			mc.setStorySpotlight(obj.get("story_spotlight").getAsBoolean());
		} catch (NullPointerException e) {
			mc.setStorySpotlight(false);
		}

		try {
			mc.setRarity(MTGRarity.valueOf(obj.get(RARITY).getAsString().toUpperCase()));
		} catch (NullPointerException e) {
			mc.setStorySpotlight(false);
		}
		
		try {
			mc.setText(obj.get("oracle_text").getAsString());
		} catch (NullPointerException e) {
			mc.setText("");
		}
		try {
			mc.setCost(obj.get("mana_cost").getAsString());
		} catch (NullPointerException e) {
			mc.setCost("");
		}
		try {
			mc.setFlavor(obj.get("flavor_text").getAsString());
		} catch (NullPointerException e) {
			mc.setFlavor("");
		}

		if (obj.get(TYPE_LINE) != null)
			generateTypes(mc, String.valueOf(obj.get(TYPE_LINE)));

		
		mc.setJapanese(String.valueOf(obj.get("LANG")).equals("en"));
		
		var n = new MagicCardNames();
		n.setLanguage("English");
		n.setName(mc.getName());
		try {
			n.setGathererId(obj.get(MULTIVERSE_ID).getAsInt());
		} catch (NullPointerException e) {
			n.setGathererId(0);
		}

		mc.getForeignNames().add(n);

		try {
			mc.setArtist(obj.get(ARTIST).getAsString());
		} catch (NullPointerException e) {
			logger.trace("artist not found");
		}
		try {
			mc.setReserved(obj.get("reserved").getAsBoolean());
		} catch (NullPointerException e) {
			logger.trace("reserved not found");
		}
		try {
			mc.setPower(obj.get(POWER).getAsString());
		} catch (NullPointerException e) {
			logger.trace("power not found");
		}
		try {
			mc.setToughness(obj.get(TOUGHNESS).getAsString());
		} catch (NullPointerException e) {
			logger.trace("toughness not found");
		}
		try {
			mc.setLoyalty(obj.get(LOYALTY).getAsInt());
		} catch (Exception e) {
			logger.trace("loyalty not found");
		}
		try {
			mc.setWatermarks(obj.get(WATERMARK).getAsString());
		} catch (NullPointerException e) {
			logger.trace("watermark not found");
		}
		try {
			mc.setFrameVersion(obj.get(FRAME).getAsString());
		} catch (NullPointerException e) {
			logger.trace("frame not found");
		}
		
		try {
			mc.setTcgPlayerId(obj.get("tcgplayer_id").getAsInt());
		} catch (NullPointerException e) {
			logger.trace("tcgplayerid not found");
		}
		
		try {
			mc.setFlavorName(obj.get("flavor_name").getAsString());
		} catch (NullPointerException e) {
			//do nothing
		}
		
		
		
		
		try {
			mc.setImageName(obj.get(ILLUSTRATION_ID).getAsString());
		} catch (NullPointerException e) {
			logger.trace("illustration_id not found");
		}
		
		if(obj.get("frame_effects")!=null) {
			Iterator<JsonElement> it = obj.get("frame_effects").getAsJsonArray().iterator();
				while (it.hasNext())
					mc.getFrameEffects().add(MTGFrameEffects.parseByLabel(it.next().getAsString()));
		}
		
		
		if (obj.get(COLORS) != null) {
			Iterator<JsonElement> it = obj.get(COLORS).getAsJsonArray().iterator();
			while (it.hasNext())
				mc.getColors().add(MTGColor.colorByCode(it.next().getAsString()));

		}
		
		if (obj.get(FINISHES) != null) {
			Iterator<JsonElement> it = obj.get(FINISHES).getAsJsonArray().iterator();
			while (it.hasNext())
				mc.getFinishes().add(MTGFinishes.parseByLabel(it.next().getAsString()));

		}
		

		if (obj.get(COLOR_IDENTITY) != null) {
			Iterator<JsonElement> it = obj.get(COLOR_IDENTITY).getAsJsonArray().iterator();
			while (it.hasNext())
				mc.getColorIdentity().add(MTGColor.colorByCode(it.next().getAsString()));
		}

		if (obj.get("legalities") != null) {
			var legs = obj.get("legalities").getAsJsonObject();
			Iterator<Entry<String, JsonElement>> it = legs.entrySet().iterator();
			while (it.hasNext()) {
				Entry<String, JsonElement> ent = it.next();
				var format = new MagicFormat(ent.getKey(),AUTHORIZATION.valueOf(ent.getValue().getAsString().toUpperCase()));
				mc.getLegalities().add(format);
			}
		}
		
		if (obj.get(BORDER) != null)
			mc.setBorder(MTGBorder.parseByLabel(obj.get(BORDER).getAsString()));
		
		
		if (obj.get(GAMES) != null) {
			var g = obj.get(GAMES).getAsJsonArray();
			
			g.forEach(el->{
				
				if(el.getAsString().equals("arena"))
					mc.setArenaCard(true);
				
				if(el.getAsString().equals("mtgo"))
					mc.setMtgoCard(true);
			});
		}
		
		
	
		var idface = 0;

		if (mc.getName().contains("//")) {
			String[] names = mc.getName().split(" // ");
			if (exact)
			{
				if (names[0].equals(search)) {
					idface = 0;
				} else {
					idface = 1;
				}
			}
		}
		if (obj.get(CARD_FACES) != null) 
		{
			mc.setText(obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get("oracle_text")
					.getAsString());
			mc.setCost(obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get("mana_cost")
					.getAsString());
			
			if(obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get(ILLUSTRATION_ID)!=null)
				mc.setImageName(obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get(ILLUSTRATION_ID).getAsString());

			generateTypes(mc, obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get(TYPE_LINE)
					.getAsString());

			try {
				mc.setLoyalty(
						obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get(LOYALTY).getAsInt());
			} catch (Exception e) {
				logger.error(mc.getName() + " has no loyalty: " + e);
			}

			try {
				Iterator<JsonElement> it = obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject()
						.get(COLORS).getAsJsonArray().iterator();
				while (it.hasNext())
					mc.getColors().add(MTGColor.colorByCode(it.next().getAsString()));
			} catch (Exception e) {
				logger.error(mc.getName() + " has no colors: " + e);
			}
			try {
				mc.setPower(obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get(POWER)
						.getAsString());
				mc.setToughness(obj.get(CARD_FACES).getAsJsonArray().get(idface).getAsJsonObject().get(TOUGHNESS)
						.getAsString());
			} catch (Exception e) {
				logger.error(mc.getName() + " has no power/toughness: " + e);

			}
		}

		MagicEdition ed = null;
		
			try {
				ed = (MagicEdition) BeanUtils.cloneBean(getSetById(obj.get("set").getAsString()));
				ed.setOnlineOnly(obj.get(DIGITAL).getAsBoolean());
				ed.setNumber(obj.get(COLLECTOR_NUMBER).getAsString());
				mc.getEditions().add(ed);
			} catch (Exception e2) {
				throw new IOException(e2);
			} 

			
			if (obj.get("multiverse_ids") != null) {
				try {
					ed.setMultiverseid(String.valueOf(obj.get("multiverse_ids").getAsJsonArray().get(idface).getAsInt()));
				} catch (Exception e1) {
					logger.error("No multiverseID found for " + mc.getName() + " face : " + idface);
				}

			}
		
		
		ThreadManager.getInstance().executeThread(new MTGRunnable() {
			
			@Override
			protected void auditedRun() {
				try {
					if (!mc.isBasicLand())
						initOtherEdition(mc);
						
					generateRules(mc);
				} catch (Exception e) {
					logger.error("error in initOtherEdition :" + e.getMessage());
				}
				
			}
		}, "other editions");
		
		postTreatmentCard(mc);
		notify(mc);
		return mc;

	}

	private void generateRules(MagicCard mc) throws IOException {
		
		if(getBoolean("LOAD_RULING"))
		{
			String url = getString("URL")+CARDS + mc.getId() + "/rulings";
		
			JsonElement el = URLTools.extractAsJson(url);
			var arr = el.getAsJsonObject().get("data").getAsJsonArray();
	
			for (var i = 0; i < arr.size(); i++) {
				var obr = arr.get(i).getAsJsonObject();
				var rul = new MagicRuling();
				rul.setDate(obr.get("published_at").getAsString());
				rul.setText(obr.get("comment").getAsString());
	
				mc.getRulings().add(rul);
			}
		}
	}

	private void generateTypes(MagicCard mc, String line) {

		line = line.replace("\"", "");

		for (String k : new String[] { "Legendary", "Basic", "Ongoing", "Snow", "World" }) {
			if (line.contains(k)) {
				mc.getSupertypes().add(k);
				line = line.replaceAll(k, "").trim();
			}
		}

		var sep = "\u2014";

		if (line.contains(sep)) {

			for (String s : line.substring(0, line.indexOf(sep)).trim().split(" "))
				mc.getTypes().add(s.replace("\"", ""));

			for (String s : line.substring(line.indexOf(sep) + 1).trim().split(" "))
				mc.getSubtypes().add(s);
		} else {
			for (String s : line.split(" "))
				mc.getTypes().add(s.replace("\"", ""));
		}

	}

	private void initOtherEdition(MagicCard mc) throws IOException {

		String url = baseURI + "/cards/search?q=+" + URLTools.encode("++!\"" + mc.getName() + "\"")+ "%20include:extras" + "%20-s:" + mc.getCurrentSet().getId();

		logger.trace("initOtherEdition " + URLDecoder.decode(url, MTGConstants.DEFAULT_ENCODING.displayName()));

		var hasMore = true;
		while (hasMore) {
	
			try {
				JsonElement el = URLTools.extractAsJson(url);

				var jsonList = el.getAsJsonObject().getAsJsonArray("data");
				for (var i = 0; i < jsonList.size(); i++) {
					var obj = jsonList.get(i).getAsJsonObject();
					MagicEdition ed = getSetById(obj.get("set").getAsString());

					if (obj.get(MULTIVERSE_ID) != null)
						ed.setMultiverseid(obj.get(MULTIVERSE_ID).getAsString());

					if (obj.get(COLLECTOR_NUMBER) != null)
						ed.setNumber(obj.get(COLLECTOR_NUMBER).getAsString());

					mc.getEditions().add(ed);
				}
				hasMore = el.getAsJsonObject().get("has_more").getAsBoolean();

				if (hasMore)
					url = el.getAsJsonObject().get("next_page").getAsString();

				Thread.sleep(50);
			} catch (IOException e) {
				logger.trace(e);
				hasMore = false;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.trace(e);
				hasMore = false;
			}
		}
	}

	private MagicEdition generateEdition(JsonObject obj) {
		var ed = new MagicEdition();
		ed.setId(obj.get("code").getAsString());
		ed.setSet(obj.get(NAME).getAsString());
		ed.setType(obj.get("set_type").getAsString());

		
		
		if (obj.get(DIGITAL) != null)
			ed.setOnlineOnly(obj.get(DIGITAL).getAsBoolean());

		if(obj.get("foil_only") !=null)
			ed.setFoilOnly(obj.get("foil_only").getAsBoolean());
		
		ed.setCardCount(obj.get("card_count").getAsInt());
		ed.setCardCountOfficial(ed.getCardCount());
		
		
		if (obj.get("block") != null)
			ed.setBlock(obj.get("block").getAsString());

		if (obj.get(RELEASED_AT) != null)
		{
			ed.setReleaseDate(obj.get(RELEASED_AT).getAsString());
			ed.setPreview(LocalDate.parse(obj.get(RELEASED_AT).getAsString(), DateTimeFormatter.ofPattern("yyyy-MM-dd")).isAfter(LocalDate.now()));
		}

		notify(ed);
		
		return ed;
	}


}
