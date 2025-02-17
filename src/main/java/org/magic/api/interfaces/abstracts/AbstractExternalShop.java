package org.magic.api.interfaces.abstracts;

import java.io.IOException;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.api.mkm.modele.Localization;
import org.magic.api.beans.ConverterItem;
import org.magic.api.beans.shop.Category;
import org.magic.api.beans.shop.Transaction;
import org.magic.api.interfaces.MTGDao;
import org.magic.api.interfaces.MTGExternalShop;
import org.magic.api.interfaces.MTGProduct;
import org.magic.api.interfaces.MTGStockItem;
import org.magic.api.interfaces.abstracts.extra.AbstractProduct;
import org.magic.tools.MTG;

public abstract class AbstractExternalShop extends AbstractMTGPlugin implements MTGExternalShop {

	
	protected abstract List<Transaction> loadTransaction() throws IOException;
	protected abstract List<MTGStockItem> loadStock(String search) throws IOException;
	protected abstract void saveOrUpdateStock(List<MTGStockItem> it) throws IOException ;	
	
	protected Map<MTGStockItem, Map.Entry<Integer,Double>> itemsBkcp; 
	
	
	
	
	@Override
	public PLUGINS getType() {
		return PLUGINS.EXTERNAL_SHOP;
	}

	protected AbstractExternalShop() {
		itemsBkcp = new HashMap<>();
	}
	
	
	protected List<ConverterItem> getRefs(String lang, Long id)
	{
		try {
			return MTG.getEnabledPlugin(MTGDao.class).listConversionItems().stream().filter(p->(p.getLang().equalsIgnoreCase(lang) && (p.getInputId().equals(id) || p.getOutputId().equals(id)))).toList();
		} catch (SQLException e) {
			logger.error(e);
			return new ArrayList<>();
		}
	}
	
	
	@Override
	public List<Transaction> listTransaction() throws IOException {
		
		var list= loadTransaction();
		list.forEach(t->
			t.getItems().forEach(item->{
				getRefs(item.getLanguage(),item.getId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getDestination(),String.valueOf(converterItem.getOutputId())));
				getRefs(item.getLanguage(),item.getId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getSource(),String.valueOf(converterItem.getInputId())));
			})
			);
		
		return list;
	}

	@Override
	public List<MTGStockItem> listStock(String search) throws IOException {
		var list= loadStock(search);
		itemsBkcp.clear();
		list.forEach(item->{
			getRefs(item.getLanguage(),item.getId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getDestination(),String.valueOf(converterItem.getOutputId())));
			getRefs(item.getLanguage(),item.getId()).forEach(converterItem->item.getTiersAppIds().put(converterItem.getSource(),String.valueOf(converterItem.getInputId())));
			itemsBkcp.put(item, new SimpleEntry<>(item.getQte(), item.getPrice()) );
		});
		
		
			
		return list;
	}

	@Override
	public void saveOrUpdateStock(List<MTGStockItem> items,boolean allShop) throws IOException {	
			saveOrUpdateStock(items);
		
			if(allShop)
			{
				for(var it :items )
				{
					for(var extComName :  it.getTiersAppIds().keySet().stream().filter(s->!s.equalsIgnoreCase(getName())).toList())
					{
						logger.debug("Updating " + it.getProduct().getName() + " on " + extComName + " with id=" + it.getTiersAppIds(extComName));
						it.setId(Integer.parseInt( it.getTiersAppIds(extComName)));
						MTG.getPlugin(extComName, MTGExternalShop.class).saveOrUpdateStock(it, false);
					}
					
					
				}
			}
	}
	

	@Override
	public void saveOrUpdateStock(MTGStockItem it,boolean allShop) throws IOException {	
		saveOrUpdateStock(List.of(it),allShop);
	}
	
	
	@Override
	public void createTransaction(Transaction t, boolean automaticProductCreation) throws IOException {
	
		logger.debug("Creating transaction " + t.getSourceShopName() +" in " + getName());
		
		t.getItems().forEach(mci->{
			
			if( automaticProductCreation &&  mci.getTiersAppIds(getName())==null)
			{
				MTGProduct p = AbstractProduct.createDefaultProduct();
							 p.setName(mci.getProduct().getName());
							 p.setUrl(mci.getUrl());
							 
				Category c = new Category();
								c.setIdCategory(172);
								c.setCategoryName("Test");
				try {
					var ret = createProduct(p,c);
					
					if(ret>0)
					{
						mci.getTiersAppIds().put(getName(), String.valueOf(ret));
						MTG.getEnabledPlugin(MTGDao.class).saveOrUpdateConversionItem(new ConverterItem( t.getSourceShopName(),getName(), mci.getProduct(), mci.getLanguage(), mci.getId(),ret));
					}
					
				} catch (Exception e) {
					logger.error(e);
				}
			}
		});
		
		saveOrUpdateTransaction(t);
			
	
	}
	
	
	
	@Override
	public Long createProduct(MTGExternalShop input, MTGProduct t,String lang,Category c) throws IOException {
		Localization defaultLoc = new Localization(1, "English");
		defaultLoc.setName(t.getName());
		var ret = createProduct(t,c);
		try {
			
			updateConversion(input.getName(), t.getName(),lang,t.getProductId(), ret);
		} catch (IOException e) {
			throw new IOException(e);
		}
		return ret;
	}

	@Override
	public  void updateConversion(String sourcename, String productName, String language, Long idProduct, Long idDestProduct ) throws IOException
	{
		try {
			MTG.getEnabledPlugin(MTGDao.class).saveOrUpdateConversionItem(new ConverterItem(sourcename,getName(), productName,language, idProduct, idDestProduct));
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}
	
	@Override
	public void deleteTransaction(List<Transaction> list) throws IOException {
		for(Transaction t : list)
			deleteTransaction(t);
	}
	
	
}


