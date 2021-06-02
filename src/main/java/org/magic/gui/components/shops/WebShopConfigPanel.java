package org.magic.gui.components.shops;

import static org.magic.tools.MTG.capitalize;
import static org.magic.tools.MTG.getPlugin;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Iterator;

import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListSelectionEvent;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;
import org.jdesktop.swingx.painter.MattePainter;
import org.magic.api.beans.MagicCard;
import org.magic.api.beans.MagicCardStock;
import org.magic.api.beans.MagicCollection;
import org.magic.api.beans.WebShopConfig;
import org.magic.api.interfaces.MTGDao;
import org.magic.api.interfaces.MTGServer;
import org.magic.gui.abstracts.MTGUIComponent;
import org.magic.gui.components.ContactPanel;
import org.magic.gui.components.ServerStatePanel;
import org.magic.gui.components.dialog.CardSearchImportDialog;
import org.magic.gui.components.editor.JCheckableListBox;
import org.magic.gui.components.renderer.CardListPanel;
import org.magic.servers.impl.JSONHttpServer;
import org.magic.servers.impl.ShoppingServer;
import org.magic.services.MTGConstants;
import org.magic.services.MTGControler;
import org.magic.services.TransactionService;
import org.magic.tools.MTG;

public class WebShopConfigPanel extends MTGUIComponent {
	
	private static final long serialVersionUID = 1L;
	private JTextField txtSiteTitle;
	private JTextField txtBannerTitle;
	private JTextArea txtBannerText;
	private JTextArea txtAbout;
	private JTextField txtURLSlides;
	private DefaultListModel<String> listModel;
	private JList<String> listSlides;
	private JTextField txtAnalyticsGoogle;
	private JCheckableListBox<MagicCollection> cboCollections;
	private MagicCardStock topProduct;
	private JSlider maxLastProductSlide;
	private JCheckableListBox<MagicCollection> needCollection;
	private JSpinner spinnerReduction ;
	private JSpinner averageDeliverayDay ;	
	private RSyntaxTextArea txtdeliveryRules ;
	private ContactPanel contactPanel;
	private JTextField txtPaypalClientId;
	private JTextField txtPaypalSendMoneyLink;
	private JCheckBox chkAutomaticValidation;
	private JCheckBox chkAutoProduct;
	private JTextField txtIban;
	private JTextField txtBic;
	
	
	
	private JPanel createBoxPanel(String keyName, Icon ic, LayoutManager layout,boolean collapsed)
	{
		var pane = new JXTaskPane();
		pane.setTitle(capitalize(keyName));
		pane.setIcon(ic);
		pane.setCollapsed(collapsed);
		pane.setLayout(layout);
		return pane;
	}
	
	
	public WebShopConfigPanel() {
		
		setLayout(new BorderLayout());
		contactPanel = new ContactPanel(false);
		var container = new JXTaskPaneContainer();
		container.setBackgroundPainter(new MattePainter(MTGConstants.PICTURE_PAINTER, true));
		
		
		
		WebShopConfig conf = MTGControler.getInstance().getWebConfig();
		var btnSave = new JButton("Save");
		
		
		
		JPanel panelGeneral = createBoxPanel("GENERAL", MTGConstants.ICON_TAB_CONSTRUCT, new GridLayout(0, 2, 0, 0), false );
		
			var lblTitleSite = new JLabel("SITETITLE");
			panelGeneral.add(lblTitleSite);
			
			txtSiteTitle = new JTextField(conf.getSiteTitle());
			panelGeneral.add(txtSiteTitle);
			
			txtAnalyticsGoogle = new JTextField(conf.getGoogleAnalyticsId());
			txtAbout = new JTextArea(conf.getAboutText());
			txtBannerTitle = new JTextField(conf.getBannerTitle());
			txtBannerText = new JTextArea(conf.getBannerText());
			
			panelGeneral.add(new JLabel("BANNERTITLE"));
			panelGeneral.add(txtBannerTitle);
			panelGeneral.add(new JLabel("BANNERTEXT"));
			panelGeneral.add(new JScrollPane(txtBannerText));
			panelGeneral.add(new JLabel("ABOUT"));
			panelGeneral.add(new JScrollPane(txtAbout));
			panelGeneral.add(new JLabel("GOOGLE_ID_ANALYTICS"));
			panelGeneral.add(txtAnalyticsGoogle);
			
			
			
		JPanel panelSlides = createBoxPanel("SLIDES", MTGConstants.ICON_TAB_PICTURE, new BorderLayout(0, 0), true);
		
		var btnDeleteLink = new JButton(MTGConstants.ICON_SMALL_DELETE);
		btnDeleteLink.setEnabled(false);
		listModel = new DefaultListModel<>();
		
		
		for(String s : conf.getSlidesLinksImage())
			listModel.addElement(s);
		
		txtURLSlides = new JTextField();
		txtURLSlides.addActionListener((ActionEvent e)->{
				listModel.addElement(txtURLSlides.getText());
				txtURLSlides.setText("");
		});
		panelSlides.add(txtURLSlides, BorderLayout.NORTH);
		
		listSlides = new JList<>(listModel);
		listSlides.setVisibleRowCount(4);
		listSlides.setFixedCellHeight(25);
		panelSlides.add(new JScrollPane(listSlides), BorderLayout.CENTER);

		
		var deleteButtonLinkPanel = new JPanel();
		panelSlides.add(deleteButtonLinkPanel, BorderLayout.EAST);
		
		deleteButtonLinkPanel.add(btnDeleteLink);
		
		
		
		JPanel panelContact = createBoxPanel("CONTACT", MTGConstants.ICON_TAB_EVENTS, new GridLayout(0, 2, 0, 0), true);
		panelContact.setLayout(new BorderLayout());
		panelContact.add(contactPanel,BorderLayout.CENTER);
		contactPanel.setContact(MTGControler.getInstance().getWebConfig().getContact());
		
		JPanel panelServer = createBoxPanel("SERVER", MTGConstants.ICON_TAB_SERVER, new BorderLayout(), false);
		var serverStatPanel = new ServerStatePanel(false,getPlugin(new ShoppingServer().getName(), MTGServer.class));
		panelServer.add(serverStatPanel,BorderLayout.CENTER);
		var btnClearCache = new JButton("Clear Cache",MTGConstants.ICON_TAB_CACHE);
		btnClearCache.addActionListener(il->((JSONHttpServer)getPlugin(new JSONHttpServer().getName(), MTGServer.class)).clearCache());
		panelServer.add(btnClearCache,BorderLayout.SOUTH);
		
		JPanel panelStock = createBoxPanel("STOCK",MTGConstants.ICON_TAB_STOCK, new GridLayout(0, 2, 0, 0),true);
		cboCollections = new JCheckableListBox<>();
		needCollection = new JCheckableListBox<>();
		chkAutomaticValidation = new JCheckBox();
		chkAutomaticValidation.setSelected(conf.isAutomaticValidation());
		
		
		try {
			for(MagicCollection mc : MTG.getEnabledPlugin(MTGDao.class).listCollections())
			{
				cboCollections.addElement(mc, conf.getCollections().contains(mc));
				needCollection.addElement(mc, conf.getNeedcollections().contains(mc));
			}
		} catch (SQLException e1) {
			logger.error(e1);
		}
		
		panelStock.add(new JLabel("SELL_STOCK_IN_COLLECTION"));
		panelStock.add(cboCollections);

		panelStock.add(new JLabel("SEARCH_CARDS_IN_COLLECTION"));
		panelStock.add(needCollection);
		
		panelStock.add(new JLabel("AUTOMATIC_VALIDATION"));
		panelStock.add(chkAutomaticValidation);
		
		
		
	
		JPanel panelProduct = createBoxPanel("PRODUCT",MTGConstants.ICON_TAB_CARD, new GridLayout(0, 2, 0, 0),true);
		topProduct = conf.getTopProduct();
		var b = new JButton("Choose Top Product Card",MTGConstants.ICON_SEARCH);
		chkAutoProduct = new JCheckBox("Automatic Top Product");
		b.setEnabled(!chkAutoProduct.isSelected());
		
		spinnerReduction = new JSpinner(new SpinnerNumberModel(conf.getPercentReduction()*100,0,100,0.5));
		
		var paneSlide = new JPanel();
		maxLastProductSlide = new JSlider(0, 16, conf.getMaxLastProduct());
		var valueLbl = new JLabel(String.valueOf(maxLastProductSlide.getValue()));
		
		maxLastProductSlide.addChangeListener(cl->valueLbl.setText(String.valueOf(maxLastProductSlide.getValue())));
		var cardPanel = new CardListPanel();
		
		if(topProduct!=null)
			cardPanel.setMagicCard(topProduct.getMagicCard());
		
		
		paneSlide.add(maxLastProductSlide);
		paneSlide.add(valueLbl);
		
		b.addActionListener(il->{
							   var diag = new CardSearchImportDialog();
								   diag.setVisible(true); 
								   topProduct= MTGControler.getInstance().getDefaultStock();
								   topProduct.setMagicCard(diag.getSelected());
								   cardPanel.setMagicCard(topProduct.getMagicCard());
		});
		
		chkAutoProduct.addItemListener(il->{
			  		
				if(chkAutoProduct.isSelected())
					try {
			  			
			  			topProduct = TransactionService.getBestProduct();
						cardPanel.setMagicCard(topProduct.getMagicCard());
					} catch (Exception e1) {
						logger.error(e1);
					}
				
				b.setEnabled(!chkAutoProduct.isSelected());
		});
		chkAutoProduct.setSelected(conf.isAutomaticProduct());
		
		
		var panelButton = new JPanel();
		panelButton.setLayout(new GridLayout(2, 1));
		panelButton.add(b);
		panelButton.add(chkAutoProduct);
		
		
		panelProduct.add(panelButton);
		panelProduct.add(cardPanel);
		panelProduct.add(new JLabel("X_LASTEST_PRODUCT"));
		panelProduct.add(paneSlide);
		panelProduct.add(new JLabel("PERCENT_REDUCTION_FOR_SELL"));
		panelProduct.add(spinnerReduction);
		
		JPanel panelDelivery = createBoxPanel("DELIVERY",MTGConstants.ICON_TAB_DELIVERY, new BorderLayout(),true);
		averageDeliverayDay = new JSpinner(new SpinnerNumberModel(conf.getAverageDeliveryTime(),0,8,1));
		txtdeliveryRules = new RSyntaxTextArea(10,1);
		txtdeliveryRules.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
		txtdeliveryRules.setText(conf.getShippingRules());	
		var panelHaut = new JPanel();	
			((FlowLayout)panelHaut.getLayout()).setAlignment(FlowLayout.LEFT);
			panelHaut.add(new JLabel("DELIVERY_DAY"));
			panelHaut.add(averageDeliverayDay);
		
		panelDelivery.add(panelHaut,BorderLayout.NORTH);
		panelDelivery.add(new JLabel("DELIVERY_RULES"),BorderLayout.WEST);
		panelDelivery.add(new JScrollPane(txtdeliveryRules), BorderLayout.CENTER);
		
		JPanel panelPayment = createBoxPanel("PAYMENT",MTGConstants.ICON_TAB_PRICES, new GridLayout(0, 2, 0, 0),true);
		
		txtPaypalClientId = new JTextField(conf.getPaypalClientId());
		panelPayment.add(new JLabel("PAYPAL_CLIENT_ID"));
		panelPayment.add(txtPaypalClientId);
		
		txtPaypalSendMoneyLink = new JTextField(conf.getSetPaypalSendMoneyUri().toString());
		panelPayment.add(new JLabel("PAYPAL_SEND_MONEY_LINK"));
		panelPayment.add(txtPaypalSendMoneyLink);
		
		txtIban = new JTextField(conf.getIban(),20);
		txtBic = new JTextField(conf.getBic(),10);
		var panelIbanBic  = new JPanel();
		
		((FlowLayout)panelIbanBic.getLayout()).setAlignment(FlowLayout.LEFT);
		
		panelIbanBic.add(txtIban);
		panelIbanBic.add(new JLabel("BIC"));
		panelIbanBic.add(txtBic);
		
		
		panelPayment.add(new JLabel("IBAN"));
		panelPayment.add(panelIbanBic);
		
		
		add(container,BorderLayout.CENTER);
		container.add(btnSave);
		container.add(panelGeneral);
		container.add(panelSlides);
		container.add(panelContact);
		container.add(panelStock);
		container.add(panelProduct);
		container.add(panelDelivery);
		container.add(panelPayment);
		
		container.add(panelServer);
		
		
		listSlides.addListSelectionListener((ListSelectionEvent e)->btnDeleteLink.setEnabled(listSlides.getSelectedIndex()>-1));
		btnDeleteLink.addActionListener((ActionEvent e)->listModel.removeElement(listSlides.getSelectedValue()));
		btnSave.addActionListener(al->{
			
			WebShopConfig newBean = MTGControler.getInstance().getWebConfig();
			
				newBean.setAboutText(txtAbout.getText());
				newBean.setBannerText(txtBannerText.getText());
				newBean.setBannerTitle(txtBannerTitle.getText());
				newBean.setSiteTitle(txtSiteTitle.getText());
				newBean.setTopProduct(topProduct);
				newBean.setMaxLastProduct(maxLastProductSlide.getValue());
				newBean.setGoogleAnalyticsId(txtAnalyticsGoogle.getText());
				newBean.setAverageDeliveryTime(Integer.parseInt(averageDeliverayDay.getValue().toString()));
				newBean.setShippingRules(txtdeliveryRules.getText());
				newBean.setPercentReduction(Double.parseDouble(spinnerReduction.getValue().toString())/100);
				newBean.setPaypalClientId(txtPaypalClientId.getText());
				newBean.setAutomaticValidation(chkAutomaticValidation.isSelected());
				newBean.setAutomaticProduct(chkAutoProduct.isSelected());
				newBean.setIban(txtIban.getText());
				newBean.setBic(txtBic.getText());
				
				try {
					newBean.setPaypalSendMoneyUri(new URI(txtPaypalSendMoneyLink.getText()));
				} catch (URISyntaxException e1) {
					MTGControler.getInstance().notify(e1);
				}
				
				newBean.getCollections().clear();
				newBean.getCollections().addAll(cboCollections.getSelectedElements());
				
				newBean.getNeedcollections().clear();
				newBean.getNeedcollections().addAll(needCollection.getSelectedElements());
				
			
			
			
			newBean.getSlidesLinksImage().clear();
			Iterator<String> it = listModel.elements().asIterator();
			while(it.hasNext())
				newBean.getSlidesLinksImage().add(it.next());
			
			
			newBean.setContact(contactPanel.getContact());
			
			
			
			MTGControler.getInstance().saveWebConfig(newBean);
			
		});
		

	}
	
	@Override
	public ImageIcon getIcon() {
		return MTGConstants.ICON_TAB_ADMIN;
	}


	@Override
	public String getTitle() {
		return "WebShop Config";
	}

}
