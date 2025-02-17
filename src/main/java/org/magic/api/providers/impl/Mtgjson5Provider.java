package org.magic.api.providers.impl;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
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
import org.magic.api.criterias.builders.JsonCriteriaBuilder;
import org.magic.api.interfaces.abstracts.extra.AbstractMTGJsonProvider;
import org.magic.services.MTGConstants;
import org.magic.services.network.URLTools;

import com.google.gson.JsonObject;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.cache.CacheProvider;
import com.jayway.jsonpath.spi.cache.LRUCache;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;


public class Mtgjson5Provider extends AbstractMTGJsonProvider{

	
	private static final String ROOT_DATA = "$.data";
	private static final String CARDS_ROOT_SEARCH = ".cards[?(@.";
	private ReadContext ctx;

	
	@Override
	public STATUT getStatut() {
		return STATUT.DEV;
	}
	

	@Override
	public MTGQueryBuilder<?> getMTGQueryManager() {
		MTGQueryBuilder<?> b= new JsonCriteriaBuilder();
		initBuilder(b);
		return b; 
	}

	@Override
	public File getDataFile() {
		return new File(MTGConstants.DATA_DIR, "AllSets-x5.json");
	}
	

	@Override
	public String getOnlineDataFileZip() {
		return MTGJSON_API_URL+"/AllPrintings.json.zip";
	}
	
	
	public Mtgjson5Provider() {
		super();
		if(CacheProvider.getCache()==null)
			CacheProvider.setCache(new LRUCache(getInt("LRU_CACHE")));
		
	}


	public void init() {
		logger.info("init " + this);

		Configuration.setDefaults(new Configuration.Defaults() {

			private final JsonProvider jsonProvider = new GsonJsonProvider();
			private final MappingProvider mappingProvider = new GsonMappingProvider();

			@Override
			public JsonProvider jsonProvider() {
				return jsonProvider;
			}

			@Override
			public MappingProvider mappingProvider() {
				return mappingProvider;
			}

			@Override
			public Set<Option> options() {
				return EnumSet.noneOf(Option.class);
			}

		});
		Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

		download();

		try {
	
			chrono.start();
			logger.debug(this + " : parsing db file");
			ctx = JsonPath.parse(getDataFile());
			logger.debug(this + " : parsing OK in " + chrono.stop()+"s");
		}
		catch(Exception e)
		{
			logger.error(e);
		}
	}

	@Override
	public List<MagicCard> searchByCriteria(MTGCrit<?>... crits) throws IOException
	{
		return search("$.data..cards"+ getMTGQueryManager().build(crits).toString());
	}
	
	@Override
	public List<MagicCard> listAllCards() throws IOException {
		return searchCardByName("", null, false);
	}
	
	@Override
	public List<MagicCard> searchCardByCriteria(String att, String crit, MagicEdition ed, boolean exact) throws IOException {
		
		var filterEdition = ".";

		if (ed != null)
		{
			if(ed.getId().equals("NMS"))
				ed.setId("NEM");
				
			filterEdition = filterEdition + ed.getId().toUpperCase();
		}

		String jsquery = ROOT_DATA + filterEdition + CARDS_ROOT_SEARCH + att + " =~ /^.*" + crit.replace("\\+", " ")+ ".*$/i)]";

		if (exact)
			jsquery = ROOT_DATA + filterEdition + CARDS_ROOT_SEARCH + att + " == \"" + crit.replace("\\+", " ") + "\")]";

		if (att.equalsIgnoreCase(SET_FIELD)) 
		{
				jsquery = ROOT_DATA+"." + crit.toUpperCase() + ".cards";
		}
		else if(StringUtils.isNumeric(crit)) {
			jsquery = ROOT_DATA + filterEdition + CARDS_ROOT_SEARCH + att + " == " + crit + ")]";
		}
		
		return search(jsquery);
		
	}
	
	@SuppressWarnings("unchecked")
	private List<MagicCard> search(String jsquery) {
		
		List<String> currentSet = new ArrayList<>();
		ArrayList<MagicCard> ret = new ArrayList<>();
		
		logger.debug("parsing " + jsquery);

		List<Map<String, Object>> cardsElement = ctx.withListeners(fr -> {
			if (fr.path().startsWith("$['data']")) {
					String path=fr.path();
					path=path.substring("$['data'][".length()+1);
					path=path.substring(0, path.indexOf("']"));
					currentSet.add(path);
			}
			return null;
		}).read(jsquery, List.class);
		
		var indexSet = 0;
		for (Map<String, Object> map : cardsElement) 
		{
				var mc = new MagicCard();
				  mc.setId(String.valueOf(String.valueOf(map.get(UUID))));
				  mc.setText(String.valueOf(map.get(TEXT)));
				  
				if (map.get(NAME) != null)
				{
					int split = map.get(NAME).toString().indexOf("/");
					
					if(split>0)
					{
						
						var side = map.get(SIDE).toString();
						
						if(side.equals("a"))
							mc.setName(String.valueOf(map.get(NAME)).substring(0, split).trim());
						else
							mc.setName(String.valueOf(map.get(NAME)).substring(split+2).trim());
						
						
						mc.setFlavorName(String.valueOf(map.get(NAME)).trim());
					}
					else
					{
						mc.setName(String.valueOf(map.get(NAME)));	
					}
					
				}
						  
				if (map.get(MANA_COST) != null)
					mc.setCost(String.valueOf(map.get(MANA_COST)));
				else
					mc.setCost("");
				
				if (map.get(RARITY) != null)
				{
					mc.setRarity(MTGRarity.rarityByName(String.valueOf(map.get(RARITY))));
				}
			
				if (map.get(TEXT) != null)
					mc.setText(String.valueOf(map.get(TEXT)));
				
				if (map.get(CONVERTED_MANA_COST) != null)
					mc.setCmc((int)Double.parseDouble(map.get(CONVERTED_MANA_COST).toString()));
				
				if (map.get(FRAME_VERSION) != null)
					mc.setFrameVersion(String.valueOf(map.get(FRAME_VERSION)));
				
				if (map.get(FLAVOR_NAME) != null)
					mc.setFlavorName(String.valueOf(map.get(FLAVOR_NAME)));
				
				if (map.get(ARTIST) != null)
					mc.setArtist(String.valueOf(map.get(ARTIST)));
			
				if (map.get(IS_RESERVED) != null)
					mc.setReserved(Boolean.valueOf(String.valueOf(map.get(IS_RESERVED))));
				
				if (map.get(HAS_CONTENT_WARNING) != null)
					mc.setHasContentWarning(Boolean.valueOf(String.valueOf(map.get(HAS_CONTENT_WARNING))));
				
				if (map.get(IS_OVERSIZED) != null)
					mc.setOversized(Boolean.valueOf(String.valueOf(map.get(IS_OVERSIZED))));
			
				if (map.get(LAYOUT) != null)
					mc.setLayout(MTGLayout.parseByLabel(String.valueOf(map.get(LAYOUT))));
				
				if (map.get(FLAVOR_TEXT) != null)
					mc.setFlavor(String.valueOf(map.get(FLAVOR_TEXT)));
				
				if (map.get(TCGPLAYER_PRODUCT_ID) != null) {
					mc.setTcgPlayerId((int)Double.parseDouble(map.get(TCGPLAYER_PRODUCT_ID).toString()));
				}
				
				if (map.get(EDHREC_RANK) != null) {
					mc.setEdhrecRank(Double.valueOf(map.get(EDHREC_RANK).toString()).intValue());
				}
				
				if (map.get(ORIGINAL_TEXT) != null)
					mc.setOriginalText(String.valueOf(map.get(ORIGINAL_TEXT)));
				
				if (map.get(ORIGINAL_TYPE) != null)
					mc.setOriginalType(String.valueOf(map.get(ORIGINAL_TYPE)));
				
				if (map.get(SIGNATURE) != null)
					mc.setSignature(String.valueOf(map.get(SIGNATURE)));
				
				if (map.get(SUPERTYPES) != null)
					mc.getSupertypes().addAll((List<String>) map.get(SUPERTYPES));
			
				if (map.get(TYPES) != null)
					mc.getTypes().addAll((List<String>) map.get(TYPES));
			
				if (map.get(SUBTYPES) != null)
					mc.getSubtypes().addAll((List<String>) map.get(SUBTYPES));
			
				if (map.get(POWER) != null)
					mc.setPower(String.valueOf(map.get(POWER)));
				
				if (map.get(TOUGHNESS) != null)
					mc.setToughness(String.valueOf(map.get(TOUGHNESS)));
				
				if (map.get(COLORS) != null)
					mc.getColors().addAll(MTGColor.parseByCode(((List<String>) map.get(COLORS))));
			
				if (map.get(COLOR_INDICATOR) != null)
					mc.getColorIndicator().addAll(MTGColor.parseByCode(((List<String>) map.get(COLOR_INDICATOR))));
				
				if (map.get(COLOR_IDENTITY) != null)
					mc.getColorIdentity().addAll(MTGColor.parseByCode(((List<String>) map.get(COLOR_IDENTITY))));
				
				if (map.get(FRAME_EFFECTS) != null)
					mc.getFrameEffects().addAll(MTGFrameEffects.parseByLabel(((List<String>) map.get(FRAME_EFFECTS))));
				
				if (map.get(AVAILABILITY) != null)
				{
					mc.setArenaCard(map.get(AVAILABILITY).toString().contains("arena"));
					mc.setMtgoCard(map.get(AVAILABILITY).toString().contains("mtgo"));
				}
			
				if (map.get(SIDE) != null)
					mc.setSide(String.valueOf(map.get(SIDE)));
				
				if(map.get(IS_STORY_SPOTLIGHT)!=null)
					mc.setStorySpotlight(Boolean.valueOf(map.get(IS_STORY_SPOTLIGHT).toString()));
				
				if(map.get(HAS_ALTERNATIVE_DECK_LIMIT)!=null)
					mc.setHasAlternativeDeckLimit(Boolean.valueOf(map.get(HAS_ALTERNATIVE_DECK_LIMIT).toString()));
				
				if (map.get(WATERMARK) != null)
					mc.setWatermarks(String.valueOf(map.get(WATERMARK)));
				
				if (map.get(IS_PROMO) != null)
					mc.setPromoCard(Boolean.valueOf(map.get(IS_PROMO).toString()));
				
				if (map.get(IS_REPRINT) != null)
					mc.setReprintedCard(Boolean.valueOf(map.get(IS_REPRINT).toString()));
			
				if (map.get(TIMESHIFTED) != null)
					mc.setTimeshifted(Boolean.valueOf(map.get(TIMESHIFTED).toString()));
				
				if (map.get(IS_FULLART) != null)
					mc.setFullArt(Boolean.valueOf(map.get(IS_FULLART).toString()));

				if (map.get(RARITY) != null)
					mc.setRarity(MTGRarity.rarityByName(map.get(RARITY).toString()));
				
				if (map.get(IS_FUNNY) != null)
					mc.setFunny(Boolean.valueOf(map.get(IS_FUNNY).toString()));
				
				if (map.get(IS_REBALANCED) != null)
					mc.setRebalanced(Boolean.valueOf(map.get(IS_REBALANCED).toString()));
				
				if (map.get(SECURITYSTAMP) != null)
					mc.setSecurityStamp(map.get(SECURITYSTAMP).toString());
				
				if (map.get(FINISHES) != null)
					mc.getFinishes().addAll(MTGFinishes.parseByLabel(((List<String>) map.get(FINISHES))));
				
				
				
				if(map.get(BORDER_COLOR)!=null)
					mc.setBorder(MTGBorder.parseByLabel(map.get(BORDER_COLOR).toString()));

				if (map.get(LOYALTY) != null) {
					try {
						mc.setLoyalty((int) Double.parseDouble(map.get(LOYALTY).toString()));
					} catch (Exception e) {
						mc.setLoyalty(0);
					}
				}
				
				if (map.get(LEGALITIES) != null) {
					
					for (Map.Entry<String,String> mapFormats : ((Map<String,String>) map.get(LEGALITIES)).entrySet()) {
						var mf = new MagicFormat(String.valueOf(mapFormats.getKey()),AUTHORIZATION.valueOf(String.valueOf(mapFormats.getValue()).toUpperCase()));
						mc.getLegalities().add(mf);
					}
				}
			

				Map<String,String> identifiers = (Map<String, String>) map.get("identifiers");
				
				if(identifiers!=null) {
				
					if(identifiers.get(MTG_ARENA_ID)!=null)
						mc.setMtgArenaId(Double.valueOf(identifiers.get(MTG_ARENA_ID)).intValue());
						
					if (identifiers.get(SCRYFALL_ILLUSTRATION_ID) != null)
						mc.setScryfallIllustrationId(String.valueOf(identifiers.get(SCRYFALL_ILLUSTRATION_ID)));
					
					if (identifiers.get(SCRYFALL_ID) != null)
						mc.setScryfallId(String.valueOf(identifiers.get(SCRYFALL_ID)));
					
					
					if (identifiers.get(MCM_ID) != null) {
						mc.setMkmId((int)Double.parseDouble(identifiers.get(MCM_ID)));
					}
					
					if (identifiers.get(MTGSTOCKS_ID) != null) {
						mc.setMtgstocksId(Double.valueOf(identifiers.get(MTGSTOCKS_ID)).intValue());
					}
					
					
				}
				
				if (map.get(RULINGS) != null) {
					for (Map<String, Object> mapRules : (List<Map<String,Object>>) map.get(RULINGS)) {
						var mr = new MagicRuling();
						mr.setDate(String.valueOf(mapRules.get("date")));
						mr.setText(String.valueOf(mapRules.get(TEXT)));
						mc.getRulings().add(mr);
					}
				}
				
				var defnames = new MagicCardNames();
				
						if(identifiers!=null && identifiers.get(MULTIVERSE_ID)!=null)
							   defnames.setGathererId((int)Double.parseDouble(identifiers.get(MULTIVERSE_ID)));
						
							   defnames.setLanguage("English");
							   defnames.setName(mc.getName());
							   defnames.setText(mc.getText());
							   defnames.setType(mc.getFullType());
			
				mc.getForeignNames().add(defnames);
				
				if (map.get(FOREIGN_DATA) != null) {
					for (Map<String, Object> mapNames : (List<Map<String, Object>>) map.get(FOREIGN_DATA)) {
						var fnames = new MagicCardNames();
									   fnames.setLanguage(String.valueOf(mapNames.get(LANGUAGE)));
									   fnames.setName(String.valueOf(mapNames.get(NAME)));
									   
									   if (mapNames.get(TEXT) != null)
										   fnames.setText(String.valueOf(mapNames.get(TEXT)));
									   
									   if (mapNames.get(TYPE) != null)
										   fnames.setType(String.valueOf(mapNames.get(TYPE)));
									   
									   if (mapNames.get(MULTIVERSE_ID) != null)
										   fnames.setGathererId((int) (double) mapNames.get(MULTIVERSE_ID));
									   
									   if (mapNames.get(FLAVOR_TEXT) != null)
										   fnames.setFlavor(String.valueOf(mapNames.get(FLAVOR_TEXT)));
									   
			
						mc.getForeignNames().add(fnames);
					}
				}
				
				String codeEd;
				if (currentSet.size() <= 1)
					codeEd = currentSet.get(0);
				else
					codeEd = currentSet.get(indexSet++);
			
				MagicEdition me = getSetById(codeEd);
							 if (map.get(NUMBER) != null)
							 {
								 me.setNumber(String.valueOf(map.get(NUMBER)));
							 }
							 
							 if(identifiers!=null && identifiers.get(MULTIVERSE_ID)!=null)
							 {
								 defnames.setGathererId((int)Double.parseDouble(identifiers.get(MULTIVERSE_ID)));
								 me.setMultiverseid(String.valueOf((int)Double.parseDouble(identifiers.get(MULTIVERSE_ID))));
							 }
							
				mc.getEditions().add(me);
				
				if (!mc.isBasicLand() && map.get(PRINTINGS) != null)
				{
					for (String print : (List<String>) map.get(PRINTINGS)) {
						if (!print.equalsIgnoreCase(codeEd)) {
							MagicEdition meO = getSetById(print);
							initOtherEditionCardsVar(mc, meO);
							mc.getEditions().add(meO);
						}
					}
				}
				
				//TODO fixing rotatedCardName
				
		postTreatmentCard(mc);		
	
		notify(mc);
		ret.add(mc);
	}
		return ret;
	}
	
	private void initOtherEditionCardsVar(MagicCard mc, MagicEdition me) {
		String edCode = me.getId();

		if (!edCode.startsWith("p"))
			edCode = edCode.toUpperCase();

		String jsquery = "$.data." + edCode + ".cards[?(@.name==\""+ mc.getName().replaceAll("\\+", " ").replaceAll("\"", "\\\\\"") + "\")]";

		List<Map<String, Object>> cardsElement = null;
		try {
			cardsElement = ctx.read(jsquery, List.class);
		} catch (Exception e) {
			logger.error("error in " + jsquery +" " +  e);
			return ;
		}

		if (cardsElement != null)
			for (Map<String, Object> map : cardsElement) {
				
				try {

					me.setNumber(String.valueOf(map.get(NUMBER)));
				} catch (Exception e) {
					logger.trace("initOtherEditionCardsVar number not found");
				}
				
								
				Map<String,String> identifiers = (Map<String, String>) map.get("identifiers");
				
				try {
					me.setMultiverseid(String.valueOf((int)Double.parseDouble(identifiers.get(MULTIVERSE_ID))));
				} catch (Exception e) {
					//do nothing
				}
				
				
			}
	}

	@Override
	public List<MagicEdition> loadEditions() throws IOException {
		var jsquery = "$.*";
		chrono.start();
		
		List<MagicEdition> eds = new ArrayList<>();
		try {		
		
		URLTools.extractAsJson(MTG_JSON_SETS_LIST).getAsJsonObject().get("data").getAsJsonArray().forEach(e->{
				String codeedition = e.getAsJsonObject().get("code").getAsString().toUpperCase();
				eds.add(generateEdition(codeedition));
		});
		
		}catch(Exception ex)
		{
			
			logger.error("Error loading set List from " + MTG_JSON_SETS_LIST +". Loading manually");
			final List<String> codeEd = new ArrayList<>();
			ctx.withListeners(fr -> {
				if (fr.path().startsWith("$"))
					codeEd.add(fr.path().substring(fr.path().indexOf("$[") + 3, fr.path().indexOf(']') - 1));
				return null;

			}).read(jsquery, List.class);
			codeEd.stream().map(this::generateEdition).forEach(eds::add);
		}

		logger.debug("Loading editions OK in " + chrono.stop() + " sec.");
		
		return eds;
	}

	private MagicEdition generateEdition(String id) {
		
		if(id.startsWith("p"))
			id=id.toUpperCase();
		
		var ed = new MagicEdition(id);
		var base = "$.data." + id.toUpperCase();
		try{
		ed.setSet(ctx.read(base + "."+NAME, String.class));
		}
		catch(PathNotFoundException pnfe)
		{	
			ed.setSet(id);
		}
		
		try{
			ed.setOnlineOnly(ctx.read(base + "."+IS_ONLINE_ONLY, Boolean.class));
		}
		catch(PathNotFoundException pnfe)
		{ 
			//do nothing
		}
		
		try{
			ed.setFoilOnly(ctx.read(base + "."+IS_FOIL_ONLY, Boolean.class));
		}
		catch(PathNotFoundException pnfe)
		{ 
			//do nothing
		}
			
		
		
		try{
		ed.setReleaseDate(ctx.read(base + ".releaseDate", String.class));
		}
		catch(PathNotFoundException pnfe)
		{ 
			//do nothing
		}
		
		try{
		ed.setType(ctx.read(base + ".type", String.class));
		}catch(PathNotFoundException pnfe)
		{ 
			//do nothing
		}
		
		try{
			ed.setBlock(ctx.read(base + ".block", String.class));
		}catch(PathNotFoundException pnfe)
		{ 
			//do nothing
		}
		
	
		try {
			ed.setMkmName(ctx.read(base + ".mcmName", String.class));
		} catch (PathNotFoundException pnfe) {
			// do nothing
		}
		
		try {
			ed.setMkmid(ctx.read(base + ".mcmId", Integer.class));
		} catch (PathNotFoundException pnfe) {
			// do nothing
		}
		
		
		try {
			ed.setCardCountOfficial(ctx.read(base + ".baseSetSize", Integer.class));
		} catch (PathNotFoundException pnfe) {
			logger.warn("baseSetSize not found in " + ed.getId());
			
		}
		
		try {
			ed.setTcgplayerGroupId(ctx.read(base + "."+TCGPLAYER_GROUP_ID, Integer.class));
		} catch (PathNotFoundException pnfe) {
			// do nothing
		}
		
		
		try {
			ed.setKeyRuneCode(ctx.read(base+"."+KEYRUNE_CODE,String.class));
		}catch(PathNotFoundException pnfe)
		{
			//do nothing
		}
		
		try {
			JsonObject o = ctx.read(base+".translations",JsonObject.class);
			
			o.keySet().forEach(key->ed.getTranslations().put(key, o.get(key).getAsString()));
		}catch(Exception pnfe)
		{
			//do nothing
		}
		
		try {
			Boolean o = ctx.read(base+"."+ISPREVIEW,Boolean.class);
			ed.setPreview(o);
		}catch(Exception pnfe)
		{
			 ed.setPreview(LocalDate.parse(ed.getReleaseDate(),DateTimeFormatter.ofPattern("yyyy-MM-dd")).isAfter(LocalDate.now()));
				
		}
		
		
		try {
			ed.setCardCount(ctx.read(base + ".totalSetSize", Integer.class));
		} catch (PathNotFoundException pnfe) {
			logger.warn("totalSetSize not found in " + ed.getId() + ", manual calculation");
			if (ed.getCardCount() == 0)
				try {
					ed.setCardCount(ctx.read(base + ".cards.length()"));
				} catch (Exception e) {
					ed.setCardCount(0);
				}
		}
		return ed;
	}

	@Override
	public String getName() {
		return "MTGJson5";
	}
	
	@Override
	public Map<String, String> getDefaultAttributes() {
		var m = super.getDefaultAttributes();
			  m.put("LRU_CACHE", "400");
		return m;
	}

	
	@Override
	public MagicCard getTokenFor(MagicCard mc, MTGLayout layout) throws IOException {
		// TODO get tokens / emblem for cards
		return null;
	}

	@Override
	public List<MagicCard> listToken(MagicEdition ed) throws IOException {
		//TODO load tokens
		
		return new ArrayList<>();
	}

}
