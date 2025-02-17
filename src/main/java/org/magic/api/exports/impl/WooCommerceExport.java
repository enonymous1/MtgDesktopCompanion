package org.magic.api.exports.impl;

import static org.magic.tools.MTG.getEnabledPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.collections4.ListUtils;
import org.magic.api.beans.MagicCard;
import org.magic.api.beans.MagicCardStock;
import org.magic.api.beans.MagicDeck;
import org.magic.api.beans.MagicEdition;
import org.magic.api.interfaces.MTGCardsProvider;
import org.magic.api.interfaces.MTGDao;
import org.magic.api.interfaces.abstracts.AbstractCardExport;
import org.magic.api.providers.impl.ScryFallProvider;
import org.magic.services.MTGControler;
import org.magic.tools.BeanTools;
import org.magic.tools.MTG;
import org.magic.tools.WooCommerceTools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.icoderman.woocommerce.EndpointBaseType;
import com.icoderman.woocommerce.WooCommerce;

public class WooCommerceExport extends AbstractCardExport {

	private static final String ARTICLE_NAME = "ARTICLE_NAME";
	private static final String UPDATE = "update";
	private static final String CREATE = "create";
	private static final String CARD_LANG_DESCRIPTION = "CARD_LANG_DESCRIPTION";
	private static final String ATTRIBUTES_KEYS = "ATTRIBUTES_KEYS";
	private static final String STOCK_MANAGEMENT = "STOCK_MANAGEMENT";
	private static final String CATEGORY_ID = "CATEGORY_ID";
	public static final String DEFAULT_STATUT = "DEFAULT_STATUT";
	private  static final String BATCH_THRESHOLD = "BATCH_THRESHOLD";
	
	
	private WooCommerce wooCommerce;
	
	@Override
	public CATEGORIES getCategory() {
		return CATEGORIES.ONLINE;
	}
	
	@Override
	public boolean needFile() {
		return false;
	}
	
	@Override
	public boolean needDialogForDeck(MODS mod) {
		return false;
	}
	
	 @Override
	public boolean needDialogForStock(MODS mod) {
		return false;
	}
	
	@Override
	public String getFileExtension() {
		return "";
	}
	
	private void init()
	{
		 wooCommerce = WooCommerceTools.newClient(getAuthenticator());
	}
	
	
	public WooCommerce getWooCommerce() {
		if(wooCommerce==null)
			init();
		
		return wooCommerce;
	}
	
	
	
	@Override
	public List<MagicCardStock> importStock(String content) throws IOException {
		init();
		
		List<MagicCardStock> stocks= new ArrayList<>();
		Map<String, String> productInfo = new HashMap<>();
		        productInfo.put("category", getString(CATEGORY_ID));
		
		List<JsonElement> ret = wooCommerce.getAll(EndpointBaseType.PRODUCTS.getValue(), productInfo);
		
		for(JsonElement e : ret)
		{
			try {
				var id = String.valueOf(e.getAsJsonObject().get("id").getAsInt());
				MagicCardStock st =null;
				st = MTG.getEnabledPlugin(MTGDao.class).getStockWithTiersID(getName(), id);
				
				if(st==null)
				{
					logger.debug("stock not found with id="+id+". Create new one");
					st = MTGControler.getInstance().getDefaultStock();
					st.getTiersAppIds().put(getName(), id);
					
					MagicCard mc = getEnabledPlugin(MTGCardsProvider.class).searchCardByName(e.getAsJsonObject().get("name").getAsString(), null, true).stream().findFirst().orElse(null);
					if(mc!=null)
					{
						st.setProduct(mc);
					}
					else
					{
						logger.debug(e.getAsJsonObject().get("name").getAsString() + " is not found");
						continue;
					}
					
				}
				else
				{
					logger.debug("Found idMTGStock=" + st.getId() + "with " + getName()+" id = "+id);
					st.setUpdated(true);
				}
				
				st.setPrice(e.getAsJsonObject().get("price").getAsDouble());
				st.setQte(e.getAsJsonObject().get("stock_quantity").getAsInt());
			
				stocks.add(st);
				
				notify(st.getProduct());
				
			} catch (Exception e1) {
				logger.error("error importStock",e1);
			}
			
			
			
		}
		return stocks;
	}
	
	

	@SuppressWarnings("unchecked")
	@Override
	public void exportStock(List<MagicCardStock> stocks, File f) throws IOException {
		
		init();
		
		if(stocks.size()>getInt(BATCH_THRESHOLD))
		{
			batchExport(ListUtils.partition(stocks, 100));
			return;
		}
		
		
		for(MagicCardStock st : stocks) 
		{
			
			Map<String, Object> productInfo=build(st);
			
	        Map<String,JsonElement> ret;
	        
				if(st.getTiersAppIds(getName())!=null)
				{
					logger.debug(st.getProduct() + "is already present in "+getName() + ". Update it");
					ret = wooCommerce.update(EndpointBaseType.PRODUCTS.getValue(),(int)Double.parseDouble(st.getTiersAppIds().get(getName())),productInfo);
					if(ret.isEmpty())
					{
						logger.info("No update for " + st + "-" + st.getProduct() +". Create it");
						ret = wooCommerce.create(EndpointBaseType.PRODUCTS.getValue(), productInfo);
					   
					}
				}
				else
				{
					logger.debug(st.getProduct() + "is not present in "+getName() + ". create it");
					ret = wooCommerce.create(EndpointBaseType.PRODUCTS.getValue(), productInfo);
				}
				
				if(ret.isEmpty() || ret.get("id")==null)
				{
					logger.error("No export for " + st + "-" + st.getProduct() +":"+ret);
				}
				else
				{
					st.getTiersAppIds().put(getName(), ret.get("id").getAsString());
					st.setUpdated(true);
				}
				
				notify(st.getProduct());
		}
	}
	
	
	
	private Map<String, Object> build(MagicCardStock st) {
		Map<String, Object> productInfo = new HashMap<>();
		
		
		if(st.getProduct()==null)
			return productInfo;
		
		if(st.getTiersAppIds().get(getName())!=null)
			productInfo.put("id", st.getTiersAppIds(getName()));
		
		
		if(getString(ARTICLE_NAME).isEmpty())
			productInfo.put("name", toForeign(st.getProduct()).getName());
		else
			productInfo.put("name", toName( toForeign(st.getProduct())));
		
        productInfo.put("type", "simple");
        productInfo.put("regular_price", String.valueOf(st.getPrice()));
        productInfo.put("categories", WooCommerceTools.entryToJsonArray("id",getString(CATEGORY_ID)));
        productInfo.put("description",desc(st.getProduct()));
        productInfo.put("short_description", toForeign(st.getProduct()).getName()+"-"+st.getCondition());
        productInfo.put("enable_html_description", "true");
        productInfo.put("status", getString(DEFAULT_STATUT));
        
        if(getBoolean(STOCK_MANAGEMENT)) {
        	productInfo.put("manage_stock", getString(STOCK_MANAGEMENT));
        	productInfo.put("stock_quantity", String.valueOf(st.getQte()));
        }
        
        
        	try {
        	productInfo.put("images", WooCommerceTools.entryToJsonArray("src",new ScryFallProvider().getJsonFor(st.getProduct()).get("image_uris").getAsJsonObject().get("normal").getAsString()));
        	//productInfo.put("images", toJson("src",PluginRegistry.inst().getPlugin(getString(PIC_PROVIDER_NAME), MTGPictureProvider.class).generateUrl(st.getMagicCard(), null)))
        	}catch(Exception e)
        	{
        		logger.error("error getting image for " + st.getProduct(),e);	
        	}
        	
      	
      	if(!getString(ATTRIBUTES_KEYS).isEmpty()) {
      		var arr = new JsonArray();
					  arr.add(createAttributes("foil", String.valueOf(st.isFoil()),true));
					  arr.add(createAttributes("altered", String.valueOf(st.isAltered()),true));
					  arr.add(createAttributes("Mkm-Condition", String.valueOf(st.getCondition().name()),true));
					  arr.add(createAttributes("Mkm-Rarete", st.getProduct().getRarity().toPrettyString(),true));
					 
					  if(st.getComment()!=null)
						  arr.add(createAttributes("Mkm-Commentaires", st.getComment(),true));
					  
					  arr.add(createAttributes("Language", st.getLanguage(),true));
					  arr.add(createAttributes("Mkm-Extension", st.getProduct().getEditions().stream().map(MagicEdition::getSet).toArray(String[]::new),true));
			productInfo.put("attributes", arr);
			
			productInfo.entrySet().forEach(e->logger.trace(e.getKey() +" " + e.getValue()));
			
      	}
      	
      	return productInfo;
	}

	private String toName(MagicCard card) {
		var s = BeanTools.createString(card, getString(ARTICLE_NAME));
		
		logger.debug("generate name " + s);
		
		return s;
	}

	private void batchExport(List<List<MagicCardStock>> partition) {
		
		
		
		for(List<MagicCardStock> stocks : partition) {
			Map<String,Object> params = new HashMap<>();
			
			
			
			List<MagicCardStock> creates = stocks.stream().filter(st->st.getTiersAppIds().get(getName())==null).toList();
			
			
			params.put(CREATE, creates.stream().map(this::build).toList());
			params.put(UPDATE, stocks.stream().filter(st->st.getTiersAppIds().get(getName())!=null).map(this::build).toList());
			
			
			Map<String,JsonElement> ret = wooCommerce.batch(EndpointBaseType.PRODUCTS.getValue(), params);
			 
			logger.debug(ret);
		
			if(ret.get(CREATE)!=null) {
				var arrRet = ret.get(CREATE).getAsJsonArray();
					
				for(var i=0;i<arrRet.size();i++)
				{
					var obj = arrRet.get(i).getAsJsonObject();
					
					try {
							if(obj.get("id").getAsInt()==0)
							{
								logger.error("Error for " + creates.get(i) +" : " + obj );
							}
							else
							{
								creates.get(i).getTiersAppIds().put(getName(), String.valueOf(obj.get("id").getAsInt()));
								creates.get(i).setUpdated(true);
							}
					}
					catch(Exception e)
					{
						logger.error("error updating at " + i +" : "+obj+". Error : "+ e);
					}
					
					
				}
			}
			
			if(ret.get(UPDATE)!=null)
			{
				logger.debug("Update done");
			}
			
			
			for(MagicCardStock st : stocks)
				notify(st.getProduct());
			
		}
		
	}

	private JsonObject createAttributes (String key ,String val,boolean visible)
	{
		return createAttributes(key ,new String[] {val},visible);
	}
	
	private JsonObject createAttributes(String key ,String[] val,boolean visible)
	{
					var obj = new JsonObject();
					   obj.addProperty("name", key);
					   obj.addProperty("visible", String.valueOf(visible));
					   
					   var arr  =new JsonArray();
					   for(String s : val)
						   arr.add(s);
					   
					   obj.add("options", arr);
		   return obj;
	}
	
	private String desc(MagicCard mc) {
		MagicCard mc2 = toForeign(mc);
		var build =new StringBuilder();
		build.append("<html>").append(mc2).append("<br/>").append(mc2.getFullType()).append("<br/>").append(mc2.getText())
		.append("</html>");
		
		return build.toString();
	}

	private MagicCard toForeign(@Nonnull MagicCard mc) {
		MagicCard mc2 ;
		
		if(!getString(CARD_LANG_DESCRIPTION).isEmpty())
		{
			mc2 = mc.toForeign(mc.getForeignNames().stream().filter(fn->fn.getLanguage().equalsIgnoreCase(getString(CARD_LANG_DESCRIPTION))).findFirst().orElse(null));
		}
		else
		{
			mc2=mc;
		}
		
		if(mc2==null )
			return mc;
		
		return mc2;
	}

	
	@Override
	public MagicDeck importDeck(String f, String name) throws IOException {
		var d = new MagicDeck();
		d.setName(name);
		
		for(MagicCardStock st : importStock(f))
		{
			d.getMain().put(st.getProduct(), st.getQte());
		}
		return d;
	}
	

	@Override
	public String getName() {
		return WooCommerceTools.WOO_COMMERCE_NAME;
	}

	@Override
	public void exportDeck(MagicDeck deck, File dest) throws IOException {
		exportStock(importFromDeck(deck), dest);
	}

	
	@Override
	public Map<String, String> getDefaultAttributes() {
		var m = new HashMap<String, String>();
				m.put(CATEGORY_ID, "");
				m.put(DEFAULT_STATUT, "private");
				m.put(STOCK_MANAGEMENT,"true");
				m.put(ATTRIBUTES_KEYS,"");
				m.put(CARD_LANG_DESCRIPTION,"English");
				m.put(ARTICLE_NAME,"");
				m.put(BATCH_THRESHOLD,"50");
		
		
		return m;
	}

	@Override
	public List<String> listAuthenticationAttributes() {
		return WooCommerceTools.generateKeysForWooCommerce();
	}
	

}
