package org.magic.api.externalshop.impl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.api.mkm.exceptions.MkmException;
import org.api.mkm.modele.Article;
import org.api.mkm.modele.Game;
import org.api.mkm.modele.LightArticle;
import org.api.mkm.modele.LightProduct;
import org.api.mkm.modele.Localization;
import org.api.mkm.modele.Order;
import org.api.mkm.modele.Product;
import org.api.mkm.modele.Product.PRODUCT_ATTS;
import org.api.mkm.services.GameService;
import org.api.mkm.services.OrderService;
import org.api.mkm.services.OrderService.ACTOR;
import org.api.mkm.services.OrderService.STATE;
import org.api.mkm.services.ProductServices;
import org.api.mkm.services.StockService;
import org.api.mkm.tools.MkmAPIConfig;
import org.api.mkm.tools.MkmConstants;
import org.api.mkm.tools.Tools;
import org.magic.api.beans.MagicEdition;
import org.magic.api.beans.enums.EnumCondition;
import org.magic.api.beans.enums.EnumItems;
import org.magic.api.beans.enums.TransactionStatus;
import org.magic.api.beans.shop.Category;
import org.magic.api.beans.shop.Contact;
import org.magic.api.beans.shop.Transaction;
import org.magic.api.exports.impl.MkmOnlineExport;
import org.magic.api.interfaces.MTGCardsProvider;
import org.magic.api.interfaces.MTGProduct;
import org.magic.api.interfaces.MTGStockItem;
import org.magic.api.interfaces.abstracts.AbstractExternalShop;
import org.magic.api.interfaces.abstracts.extra.AbstractProduct;
import org.magic.api.interfaces.abstracts.extra.AbstractStockItem;
import org.magic.services.MTGConstants;
import org.magic.tools.MTG;
import org.magic.tools.UITools;

public class MkmExternalShop extends AbstractExternalShop {
	
	private static final String ID_GAME = "ID_GAME";
	private boolean initied=false;
	private StockService mkmStockService;
	private void init()
	{
		if(!initied) {
			try {
				MkmAPIConfig.getInstance().init(getAuthenticator().getTokensAsProperties());
				initied=true;
			} catch (MkmException e) {
				logger.error(e);
			}
		}
		mkmStockService = new StockService();
	}
	
	
	@Override
	public List<Category> listCategories() throws IOException {
		return new GameService().listCategories().stream().map(c->
			new Category(c.getIdCategory(), c.getCategoryName())
		).toList();
	}
	
	
	private List<File> loadFiles() throws IOException
	{
		var serv = new StockService();
		
		File temp = new File(MTGConstants.DATA_DIR, "mkm_temp_card.csv"); 
		File temp2 = new File(MTGConstants.DATA_DIR, "mkm_temp_sealed.csv"); 
		
		Game g = new Game();
		g.setIdGame(getInt(ID_GAME));
		
		serv.exportStock(temp,getInt(ID_GAME),false);
		serv.exportStock(temp2,getInt(ID_GAME),true);
		
		return List.of(temp,temp2);
		
	}
	
	
	@Override
	public List<MTGStockItem> loadStock(String search) throws IOException {
		
			var ret = new ArrayList<MTGStockItem>();
			for(File f : loadFiles())
				try(CSVParser p = CSVFormat.Builder.create().setDelimiter(";").setHeader().build().parse(new FileReader(f))  )
				{
					p.iterator().forEachRemaining(art->{
		
						if(art.get("English Name").toLowerCase().contains(search.toLowerCase()) || art.get("Exp. Name").toLowerCase().contains(search.toLowerCase()) ||art.get("idArticle").equalsIgnoreCase(search.toLowerCase())) {
						
							var item = AbstractStockItem.generateDefault();
				
							var product = new LightProduct();
								  product.setIdGame(1);
								  product.setLocName(art.get("Local Name"));
								  product.setExpansion(art.get("Exp. Name"));
								  product.setEnName(art.get("English Name"));
								  product.setIdProduct(Integer.parseInt(art.get("idProduct")));
								  product.setRarity("");
								  try {
									  item.setFoil(!art.get("Foil?").isEmpty());
									  item.setSigned(!art.get("Signed?").isEmpty());
									  item.setAltered(!art.get("Altered?").isEmpty());
									  item.setCondition(MkmOnlineExport.convert(art.get("Condition")));
								   }
									catch(IllegalArgumentException e)
									{
										item.setCondition(EnumCondition.SEALED);
										product.setRarity(null);
									}
								  
								  item.setProduct(toProduct(product));
								  item.setQte(Integer.parseInt(art.get("Amount")));
								  item.setPrice(UITools.parseDouble(art.get("Price")));
								  item.setId(Integer.parseInt(art.get("idArticle")));
								  item.setComment(art.get("Comments"));
								  try {
									  var loc = Tools.listLanguages().get(Integer.parseInt(art.get("Language"))-1);
									  item.setLanguage(loc.getLanguageName());  
								  }
								  catch(Exception e)
								  {
									  logger.error("No language for code =" + art.get("Language"));
								  }
								  ret.add(item);
								  
								  notify(item);
						}
					});
				}
			catch(Exception e)
			{
				logger.error(e);
			}
		return ret;
	}
	
	@Override
	/*
	 * Overrried to take as MkmIdProduct
	*/
	public List<MTGStockItem> listStock(String search) throws IOException {
		var list= loadStock(search);
		itemsBkcp.clear();
		list.forEach(item->{
			getRefs(item.getLanguage(),item.getProduct().getProductId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getDestination(),String.valueOf(converterItem.getOutputId())));
			getRefs(item.getLanguage(),item.getProduct().getProductId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getSource(),String.valueOf(converterItem.getInputId())));
			itemsBkcp.put(item, new SimpleEntry<>(item.getQte(), item.getPrice()) );
		});
			
		return list;
	}
	
	
	
	@Override
	/*
	 * Overrried to take as MkmIdProduct
	*/
	public List<Transaction> listTransaction() throws IOException {
		var list= loadTransaction();
		list.forEach(t->
			t.getItems().forEach(item->{
				getRefs(item.getLanguage(),item.getProduct().getProductId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getDestination(),String.valueOf(converterItem.getOutputId())));
				getRefs(item.getLanguage(),item.getProduct().getProductId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getSource(),String.valueOf(converterItem.getInputId())));
			})
			);
		
		return list;
	}
	
	
	@Override
	protected List<Transaction> loadTransaction()  {
		init();
		
		var ret = new ArrayList<Transaction>();
		try {
			var serv = new OrderService();
			
			for(String t: getArray("STATE"))
				ret.addAll(serv.listOrders(ACTOR.valueOf(getString("ACTOR")),STATE.valueOf(t),1).stream().map(this::toTransaction).toList());
			
			return ret;
		} catch (IOException e) {
			logger.error(e);
			return ret;
		}
	}

	@Override
	public List<MTGProduct> listProducts(String name) throws IOException {
		init();
		Map<PRODUCT_ATTS, String> atts = new EnumMap<>(PRODUCT_ATTS.class);
		atts.put(PRODUCT_ATTS.idGame, getString(ID_GAME));
		return new ProductServices().findProduct(name, atts).stream().map(this::toProduct).toList();
	}

	@Override
	public int saveOrUpdateTransaction(Transaction t) throws IOException {
		init();
		var stocks= new ArrayList<LightArticle>();
		for(File f : loadFiles())
		{
			stocks.addAll(mkmStockService.readStockFile(f, getInt(ID_GAME)));
		}
				
		logger.info(getName() + " will only update his stock from this transation");
		logger.debug(getName() + " loaded " + stocks.size() +" items");
		
		
		t.getItems().stream().map(it -> {
			if(it.getTiersAppIds(getName())==null)
			{
				logger.warn(it.getProduct() + " is not synchronized with " + getName());
				return null;
			}
			else
			{
				return parse(it);
			}
			
		}).filter(Objects::nonNull).toList().forEach(art->{
			var articles = stocks.stream().filter(pl->pl.getIdProduct()==art.getIdProduct()).toList();
			if(articles.size()>1)
			{
				logger.warn("Found multiple Articles :"  + articles);
			}
			else if(articles.isEmpty())
			{
				logger.warn("Article " + art.getIdArticle() + " not found in stock");
			}
			else
			{
				try {
					mkmStockService.changeQte(art, -art.getCount());
				} catch (IOException e) {
					logger.error(e);				
				}	
			}
		});
		return t.getId();
	}

	
	
	
	private LightArticle parse(MTGStockItem it) {
		var ret = new LightArticle();
		
		ret.setIdArticle(getRefs(it.getLanguage(), it.getId()).get(0).getIdFor(getName()).intValue());
		
		
		ret.setIdProduct(it.getProduct().getProductId().intValue());
		ret.setLanguage(Tools.listLanguages().stream().filter(l->l.getLanguageName().equalsIgnoreCase(it.getLanguage())).findFirst().orElse(new Localization(1, it.getLanguage())));
		ret.setCount(it.getQte());
		return ret;
	}

	@Override
	public Long createProduct(MTGProduct t,Category c) throws IOException {
		throw new IOException("Not able to create product in Mkm");
	}
	
	
	@Override
	public String getVersion() {
		return MkmConstants.MKM_API_VERSION;
	}

	@Override
	public String getName() {
		return MkmConstants.MKM_NAME;
	}
	
	private Transaction toTransaction(Order o) {
		Transaction t = new Transaction();
							t.setId(o.getIdOrder());
							t.setTransporterShippingCode(null);
							t.setDateCreation(o.getState().getDateBought());
							t.setDatePayment(o.getState().getDatePaid());
							t.setDateSend(o.getState().getDateSent());
							t.setCurrency(o.getCurrencyCode());
							t.setMessage(o.getNote());
							t.setSourceShopName(getName());
							
		Contact c = new Contact();
		
				var name = o.getBuyer().getAddress().getName();
				c.setLastName(name.substring(0, name.indexOf(" ")).trim());
				c.setName(name.substring(name.indexOf(" ")).trim());
				c.setAddress(o.getBuyer().getAddress().getStreet());
				c.setZipCode(o.getBuyer().getAddress().getZip());
				c.setCity(o.getBuyer().getAddress().getCity());
				c.setId(o.getBuyer().getIdUser());
				c.setEmailAccept(false);
				c.setEmail(null);
				
		t.setContact(c);
		t.setShippingPrice(o.getShippingMethod().getPrice());
		t.setTransporterShippingCode(o.getTrackingNumber());
		
		
		if(t.getDateCreation()!=null)
			t.setStatut(TransactionStatus.NEW);
		
		if(t.getDatePayment()!=null)
			t.setStatut(TransactionStatus.PAID);

		if(t.getDateSend()!=null)
			t.setStatut(TransactionStatus.SENT);
		
	
		o.getArticle().forEach(article->{
			var item = AbstractStockItem.generateDefault();
			item.setId(article.getIdArticle());
			item.setLanguage(article.getLanguage().getLanguageName());
			item.setPrice(article.getPrice());
			item.setProduct(toProduct(article.getProduct()));
			item.getProduct().setProductId(Long.valueOf(article.getIdProduct()));
			
			if(article.getCondition()!=null)
				item.setCondition(MkmOnlineExport.convert(article.getCondition()));
			
			item.setQte(article.getCount());
			item.setFoil(article.isFoil());
			item.setAltered(article.isAltered());
			item.setSigned(article.isSigned());
			item.getTiersAppIds().put(getName(), String.valueOf(article.getIdProduct()));
			t.getItems().add(item);
		});
		return t;
	}
	
	
	private MTGProduct toProduct(Product p) {
		var product = new LightProduct();
		
		product.setIdProduct(p.getIdProduct());
		product.setEnName(p.getEnName());
		product.setExpansion(p.getExpansionName());
		product.setImage(p.getImage());
		product.setRarity(p.getRarity());
		
		MTGProduct prod=  toProduct(product);
		prod.setCategory(new Category(0,p.getCategoryName()));
	
		
		return prod;
		
	}
	
	
	private MTGProduct toProduct(LightProduct product) {
		var p = AbstractProduct.createDefaultProduct();
		p.setName(product.getEnName());
		p.setProductId(Long.valueOf(product.getIdProduct()));
		
		try {
		p.setEdition(MTG.getEnabledPlugin(MTGCardsProvider.class).getSetByName(product.getExpansion()));
		}
		catch(Exception e)
		{
			p.setEdition(new MagicEdition("set",product.getExpansion()));	
		}
			
		if(product.getImage()!=null && product.getImage().startsWith("//"))
			p.setUrl("https:"+ product.getImage());
		else
			p.setUrl(product.getImage());
		
		if(product.getRarity()==null)
			p.setTypeProduct(EnumItems.SEALED);
		else
			p.setTypeProduct(EnumItems.CARD);
				
				
		return p;
		
	}

	@Override
	public Map<String, String> getDefaultAttributes() {
		return Map.of("STATE", STATE.paid.name(),
				"ACTOR", ACTOR.seller.name(),
				ID_GAME,"1");
	}

	@Override
	public List<String> listAuthenticationAttributes() {
		return MkmConstants.mkmTokens();
	}

	@Override
	public Integer saveOrUpdateContact(Contact c) throws IOException {
		throw new IOException("contacts can't be updated");
	}

	@Override
	public Contact getContactByEmail(String email) throws IOException {
		throw new IOException("contacts can't be found by email");
	}

	@Override
	public MTGStockItem getStockById(EnumItems typeStock,Long id) throws IOException {
			return loadStock(String.valueOf(id)).stream().findAny().orElse(null);
	}

	
	@Override
	public void saveOrUpdateStock(List<MTGStockItem> stocks) throws IOException {
		init();
		var transformed = stocks.stream().map(it->{
			var art = new Article();
			art.setIdArticle(it.getId().intValue());
			art.setIdProduct(it.getProduct().getProductId().intValue());
			art.setPrice(it.getPrice());
			art.setCondition(MkmOnlineExport.convert(it.getCondition()));
			art.setFoil(it.isFoil());
			art.setSigned(it.isSigned());
			art.setAltered(it.isAltered());
			return art;
		}).toList();
		
		
		
		
		var retour = mkmStockService.updateArticles(transformed);
		stocks.forEach(mtg->mtg.setUpdated(retour.stream().map(LightArticle::getIdArticle).noneMatch(i-> i.intValue() == mtg.getId())));
		
		stocks.stream().filter(it->!it.getQte().equals(itemsBkcp.get(it).getKey())).forEach(it->{
			
			int changeQty = (it.getQte()-itemsBkcp.get(it).getKey());
			try {
				
				var ret = new LightArticle();
					  ret.setIdArticle(it.getId().intValue());
					  ret.setIdProduct(it.getProduct().getProductId().intValue());
					  ret.setCount(it.getQte());
					  logger.debug(it + " new = " + it.getQte() + "  old=" + itemsBkcp.get(it).getKey() + " = " +  changeQty);
					  mkmStockService.changeQte(ret, changeQty);
			} catch (IOException e) {
				logger.error(e);
			}
			
		});
		
		
	}
	
	

	@Override
	public List<Contact> listContacts() throws IOException {
		return listTransaction().stream().map(Transaction::getContact).distinct().toList();
	}

	@Override
	public void deleteContact(Contact contact) throws IOException {
		throw new IOException("contacts can't be deleted");

	}


	@Override
	public void deleteTransaction(Transaction t) throws IOException {
		throw new IOException("Transaction can't be deleted");

		
	}


	@Override
	public Transaction getTransactionById(int parseInt) throws IOException {
		var serv = new OrderService();
		return toTransaction(serv.getOrderById(parseInt));
	}


	@Override
	public Contact getContactByLogin(String login, String passw) throws IOException {
		throw new IOException("get Contact by login is not implemented");
	}


	@Override
	public List<Transaction> listTransactions(Contact c) throws IOException {
		return listTransaction().stream().filter(t->t.getContact().getId()==c.getId()).toList();
	}


	@Override
	public boolean enableContact(String token) throws IOException {
		logger.warn("not authorized");
		return false;
	}
}


