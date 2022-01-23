package org.magic.api.externalshop.impl;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.groovy.util.Maps;
import org.magic.api.beans.enums.EnumItems;
import org.magic.api.beans.shop.Category;
import org.magic.api.beans.shop.Contact;
import org.magic.api.beans.shop.Transaction;
import org.magic.api.interfaces.MTGProduct;
import org.magic.api.interfaces.MTGStockItem;
import org.magic.api.interfaces.abstracts.AbstractExternalShop;
import org.magic.api.interfaces.abstracts.extra.AbstractProduct;
import org.magic.api.interfaces.abstracts.extra.AbstractStockItem;
import org.magic.services.MTGControler;
import org.magic.services.network.MTGHttpClient;
import org.magic.services.network.RequestBuilder;
import org.magic.services.network.RequestBuilder.METHOD;
import org.magic.services.network.URLTools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ShopifyExternalShop extends AbstractExternalShop {
	
	private static final String CUSTOMERS = "customers";
	private static final String PRODUCTS = "products";
	private MTGHttpClient client = URLTools.newClient();
	
	
	
	public static void main(String[] args) throws IOException {
		MTGControler.getInstance().loadAccountsConfiguration();
		new ShopifyExternalShop().listContacts().forEach(p->{
			System.out.println(p);
		});
		System.exit(0);
	}
	
	
	
	private JsonArray read(String entityName, Map<String,String> attributs) throws IOException 
	{
		JsonArray arr = new JsonArray();
		
		var build=build("https://"+getAuthenticator().get("SUBDOMAIN")+".myshopify.com/admin/api/2022-01/"+entityName.toLowerCase()+".json");
		
		if(attributs!=null && !attributs.isEmpty())
			attributs.entrySet().forEach(e->build.addContent(e.getKey(), e.getValue()));
					 
		var resp = build.execute();
		
		arr.addAll(URLTools.toJson(resp.getEntity().getContent()).getAsJsonObject().get(entityName).getAsJsonArray());
		var next = URLTools.parseLinksHeader(resp.getFirstHeader("Link")).get("next");
		
		
		while(next!=null)
		{
			resp = build(next).execute();
			next = URLTools.parseLinksHeader(resp.getFirstHeader("Link")).get("next");
			arr.addAll(URLTools.toJson(resp.getEntity().getContent()).getAsJsonObject().get(entityName).getAsJsonArray());
		}
		return arr;
	}

	private RequestBuilder build(String url) {
		return RequestBuilder.build()
				  .url(url)
				  .method(METHOD.GET)
				  .setClient(client)
				  .addHeader("X-Shopify-Access-Token", getAuthenticator().get("ACCESS_TOKEN"))
				  .addHeader(URLTools.ACCEPT, URLTools.HEADER_JSON);
	}
	
	private List<MTGStockItem> parseVariants(JsonObject obj) {
		
		var ret = new ArrayList<MTGStockItem>();
		
		for(JsonElement el : obj.get("variants").getAsJsonArray())
		{
			AbstractStockItem<MTGProduct> it = AbstractStockItem.generateDefault();
									  it.setProduct(parseProduct(obj));
									  it.setId(el.getAsJsonObject().get("id").getAsLong());
									  it.setPrice(el.getAsJsonObject().get("price").getAsDouble());
									  it.setQte(el.getAsJsonObject().get("inventory_quantity").getAsInt());
									  it.setFoil(el.getAsJsonObject().get("option1").getAsString().toLowerCase().contains("foil"));
									  ret.add(it);
		}
		return ret;
	}
	
	private MTGProduct parseProduct(JsonObject sp) {
		
		MTGProduct p = AbstractProduct.createDefaultProduct();
				   p.setProductId(sp.get("id").getAsLong());
				   p.setName(sp.get("title").getAsString());
				   p.setUrl(sp.get("image").getAsJsonObject().get("src").getAsString());
				   notify(p);
		return p;
	}
	
	private Contact parseContact(JsonObject obj) {
		var c = new Contact();
			c.setEmail(obj.get("email").getAsString());
			c.setName(!obj.get("first_name").isJsonNull()?obj.get("first_name").getAsString():"");
			c.setLastName(!obj.get("last_name").isJsonNull()?obj.get("last_name").getAsString():"");
			c.setActive(obj.get("state").getAsString().equalsIgnoreCase("enabled"));
			c.setId(obj.get("id").getAsInt());
			if(obj.get("default_address")!=null) {
				var addrObj = obj.get("default_address").getAsJsonObject();	
				c.setAddress(addrObj.get("address1").getAsString());
				c.setCity(addrObj.get("city").getAsString());
				c.setZipCode(addrObj.get("zip").getAsString());
				c.setCountry(addrObj.get("country").getAsString());
				c.setTelephone(addrObj.get("phone").getAsString());
			}
			
		return c;
	
	}

	
	

	@Override
	public List<MTGProduct> listProducts(String name) throws IOException {
		var ret = new ArrayList<MTGProduct>();
		var arr = read(PRODUCTS,name!=null?Maps.of("title", name):null);
		for(JsonElement a: arr )
			ret.add(parseProduct(a.getAsJsonObject()));
		
		return ret;
	}
	

	@Override
	protected List<MTGStockItem> loadStock(String name) throws IOException {
		var ret = new ArrayList<MTGStockItem>();
		var arr = read(PRODUCTS,name!=null?Maps.of("title", name):null);
		for(JsonElement a: arr )
		{
			ret.addAll(parseVariants(a.getAsJsonObject()));
		}
		return ret;
	}
	
	@Override
	public List<Contact> listContacts() throws IOException {
		var arr = read(CUSTOMERS,null);
		var ret = new ArrayList<Contact>();
		for(JsonElement je : arr)
		{
			ret.add(parseContact(je.getAsJsonObject()));
		}
		return ret;
		
	}
	
	



	@Override
	public Map<String, String> getDefaultAttributes() {
		var m = super.getDefaultAttributes();
			m.put("FOIL_OPTION_NUMBER", "1");
			m.put("SET_OPTION_NUMBER", "2");
			
			return m;
	}
	

	
	
	@Override
	protected void saveOrUpdateStock(List<MTGStockItem> it) throws IOException {
		// TODO Auto-generated method stub

	}
	
	
	@Override
	public Long createProduct(MTGProduct t, Category c) throws IOException {
		// TODO Auto-generated method stub
		return 0L;
	}

	@Override
	public List<String> listAuthenticationAttributes() {
		return List.of("SUBDOMAIN", "ACCESS_TOKEN") ;
	}
	
	
	@Override
	public MTGStockItem getStockById(EnumItems typeStock, Long id) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Category> listCategories() throws IOException {
		return new ArrayList<>();
	}

	@Override
	public Integer saveOrUpdateContact(Contact c) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Contact getContactByEmail(String email) throws IOException {
		return null;
	}

	
	


	@Override
	public void deleteContact(Contact contact) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public Contact getContactByLogin(String login, String passw) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int saveOrUpdateTransaction(Transaction t) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void deleteTransaction(Transaction t) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public Transaction getTransactionById(int parseInt) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Transaction> listTransactions(Contact c) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean enableContact(String token) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getName() {
		return "Shopify";
	}

	@Override
	protected List<Transaction> loadTransaction() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}


}
