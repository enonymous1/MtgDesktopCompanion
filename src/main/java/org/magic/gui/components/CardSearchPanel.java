package org.magic.gui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.DefaultListModel;
import javax.swing.DefaultRowSorter;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.apache.commons.beanutils.BeanUtils;
import org.jdesktop.swingx.JXTable;
import org.magic.api.beans.MagicCard;
import org.magic.api.beans.MagicCardNames;
import org.magic.api.beans.MagicCollection;
import org.magic.api.beans.MagicDeck;
import org.magic.api.beans.MagicEdition;
import org.magic.api.beans.MagicRuling;
import org.magic.api.criterias.SQLCriteriaBuilder;
import org.magic.api.interfaces.MTGCardsExport.MODS;
import org.magic.api.interfaces.MTGCardsProvider;
import org.magic.api.interfaces.MTGDao;
import org.magic.api.interfaces.MTGPlugin;
import org.magic.game.gui.components.DisplayableCard;
import org.magic.game.gui.components.HandPanel;
import org.magic.gui.abstracts.AbstractBuzyIndicatorComponent;
import org.magic.gui.abstracts.MTGUIComponent;
import org.magic.gui.components.charts.CmcChartPanel;
import org.magic.gui.components.charts.HistoryPricesPanel;
import org.magic.gui.components.charts.ManaRepartitionPanel;
import org.magic.gui.components.charts.RarityRepartitionPanel;
import org.magic.gui.components.charts.TypeRepartitionPanel;
import org.magic.gui.components.dialog.AdvancedSearchQueryDialog;
import org.magic.gui.models.MagicCardTableModel;
import org.magic.gui.renderer.MagicEditionIconListRenderer;
import org.magic.gui.renderer.MagicEditionsJLabelRenderer;
import org.magic.gui.renderer.ManaCellRenderer;
import org.magic.services.MTGConstants;
import org.magic.services.MTGControler;
import org.magic.services.threads.ThreadManager;
import org.magic.services.workers.AbstractObservableWorker;
import org.magic.sorters.CardsEditionSorter;
import org.magic.sorters.NumberSorter;
import org.magic.tools.UITools;
import org.utils.patterns.observer.Observable;

public class CardSearchPanel extends MTGUIComponent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String SEARCH_MODULE = "SEARCH_MODULE";

	public static final int INDEX_THUMB = 1;

	private MagicCard selectedCard;
	private MagicEdition selectedEdition;
	private MagicCardTableModel cardsModeltable;
	private JTabbedPane tabbedCardsView;
	private static CardSearchPanel inst;
	private HandPanel thumbnailPanel;
	private ManaRepartitionPanel manaRepartitionPanel;
	private TypeRepartitionPanel typeRepartitionPanel;
	private RarityRepartitionPanel rarityRepartitionPanel;
	private SimilarityCardPanel similarityPanel;
	private CmcChartPanel cmcChart;
	private CardsPicPanel cardsPicPanel;
	private HistoryPricesPanel historyChartPanel;
	private MagicEditionDetailPanel magicEditionDetailPanel;
	private MagicCardDetailPanel detailCardPanel;
	private PricesTablePanel priceTablePanel;
	private JTextArea txtRulesArea;
	private CardStockPanel stockPanel;
	private ObjectViewerPanel panelJson;
	private CriteriaComponent searchComponent;
	private JPopupMenu popupMenu = new JPopupMenu();
	private JXTable tableCards;
	private JExportButton btnExport;
	private JList<MagicEdition> listEdition;
	private CardsDeckCheckerPanel deckPanel;
	private AbstractBuzyIndicatorComponent lblLoading;
	private CardAbilitiesPanel abilitiesPanel;
	private JButton defaultEnterButton;
	
	
	public AbstractBuzyIndicatorComponent getLblLoading() {
		return lblLoading;
	}
	
	@Override
	public ImageIcon getIcon() {
		return MTGConstants.ICON_SEARCH_24;
	}
	
	@Override
	public String getTitle() {
		return MTGControler.getInstance().getLangService().getCapitalize(SEARCH_MODULE);
	}
	
	public static CardSearchPanel getInstance() {
		if (inst == null)
			inst = new CardSearchPanel();

		return inst;
	}

	public List<MagicCard> getMultiSelection() {
		return UITools.getTableSelections(tableCards,0);
	}

	public MagicCard getSelected() {
		return selectedCard;
	}

	public void initPopupCollection() throws SQLException {
		JMenu menuItemAdd = new JMenu(MTGControler.getInstance().getLangService().getCapitalize("ADD"));
		menuItemAdd.setIcon(MTGConstants.ICON_NEW);
		for (MagicCollection mc : MTGControler.getInstance().getEnabled(MTGDao.class).listCollections()) {

			JMenuItem adds = new JMenuItem(mc.getName());
			adds.setIcon(MTGConstants.ICON_COLLECTION);
			adds.addActionListener(addEvent -> {

				String collec = ((JMenuItem) addEvent.getSource()).getText();
				lblLoading.start(tableCards.getSelectedRowCount());
				lblLoading.setText(MTGControler.getInstance().getLangService().getCapitalize("ADD_CARDS_TO") + " " + collec);

				for (int i = 0; i < tableCards.getSelectedRowCount(); i++) {

					int viewRow = tableCards.getSelectedRows()[i];
					int modelRow = tableCards.convertRowIndexToModel(viewRow);

					MagicCard mcCard = (MagicCard) tableCards.getModel().getValueAt(modelRow, 0);
					try {
						MTGControler.getInstance().saveCard(mcCard, MTGControler.getInstance().getEnabled(MTGDao.class).getCollection(collec),null);
					} catch (SQLException e1) {
						logger.error(e1);
						MTGControler.getInstance().notify(e1);
					}

				}
				lblLoading.end();
			});
			menuItemAdd.add(adds);
		}

		popupMenu.add(menuItemAdd);
	}

	
	public void initGUI() {
		cardsModeltable = new MagicCardTableModel();
		JPanel panelResultsCards;
		JPanel panelFilters;
		JPanel panelmana;
		JPanel editionDetailPanel;
		JPanel panneauHaut;
		JPanel panneauCard;
		JPanel panneauStat;
		JTextField txtFilter;
		JButton btnClear;
		JButton btnFilter;
		
		DefaultRowSorter<TableModel, Integer> sorterCards;
		sorterCards = new TableRowSorter<>(cardsModeltable);
		sorterCards.setComparator(7, new NumberSorter());

		List<MagicEdition> li = new ArrayList<>();
		try {
			li = MTGControler.getInstance().getEnabled(MTGCardsProvider.class).listEditions();
			Collections.sort(li);
		} catch (Exception e2) {
			logger.error("error no edition loaded", e2);
		}

		//////// INIT COMPONENTS

		JScrollPane scrollThumbnails = new JScrollPane();
		JSplitPane panneauCentral = new JSplitPane();
		panneauStat = new JPanel();
		panneauHaut = new JPanel();
		panneauCard = new JPanel();
		JTabbedPane tabbedCardsInfo = new JTabbedPane(SwingConstants.TOP);
		editionDetailPanel = new JPanel();
		panelResultsCards = new JPanel();
		abilitiesPanel = new CardAbilitiesPanel();
		cmcChart = new CmcChartPanel();
		manaRepartitionPanel = new ManaRepartitionPanel();
		typeRepartitionPanel = new TypeRepartitionPanel();
		stockPanel = new CardStockPanel();
		historyChartPanel = new HistoryPricesPanel(true);
		cardsPicPanel = new CardsPicPanel();
		priceTablePanel = new PricesTablePanel();
		rarityRepartitionPanel = new RarityRepartitionPanel();
		detailCardPanel = new MagicCardDetailPanel();
		panelmana = new JPanel();
		panelFilters = new JPanel();
		ManaPanel pan = new ManaPanel();
		panelJson = new ObjectViewerPanel();
		tabbedCardsView = new JTabbedPane(SwingConstants.TOP);
		thumbnailPanel = new HandPanel();
		thumbnailPanel.setBackground(MTGConstants.THUMBNAIL_BACKGROUND_COLOR);
		btnExport = new JExportButton(MODS.EXPORT);
		UITools.bindJButton(btnExport, KeyEvent.VK_E, "Search Export");
		btnFilter = UITools.createBindableJButton(null, MTGConstants.ICON_FILTER, KeyEvent.VK_F, "searchfilter");
		btnClear = UITools.createBindableJButton(null, MTGConstants.ICON_CLEAR, KeyEvent.VK_C, "clear");
		similarityPanel = new SimilarityCardPanel();
		tableCards = new JXTable();
		lblLoading = AbstractBuzyIndicatorComponent.createProgressComponent();
		JLabel lblFilter = new JLabel();
		listEdition = new JList<>();
		JButton advancedSearch = UITools.createBindableJButton(null, MTGConstants.ICON_SEARCH_ADVANCED, KeyEvent.VK_A, "AdvancedSearch");
		searchComponent = new CriteriaComponent(false);
		defaultEnterButton = new JButton(MTGConstants.ICON_SEARCH);
		txtRulesArea = new JTextArea();
		
		txtFilter = new JTextField();

		UITools.initTableFilter(tableCards);
	
		deckPanel = new CardsDeckCheckerPanel();
		
		//////// MODELS
		listEdition.setModel(new DefaultListModel<>());
		tableCards.setModel(cardsModeltable);

		//////// RENDERER
		tableCards.getColumnModel().getColumn(2).setCellRenderer(new ManaCellRenderer());
		tableCards.getColumnModel().getColumn(6).setCellRenderer(new MagicEditionsJLabelRenderer());
		listEdition.setCellRenderer(new MagicEditionIconListRenderer());
		
		///////// CONFIGURE COMPONENTS
		txtRulesArea.setLineWrap(true);
		txtRulesArea.setWrapStyleWord(true);
		txtRulesArea.setEditable(false);
		btnFilter.setToolTipText(MTGControler.getInstance().getLangService().getCapitalize("FILTER"));
		btnExport.setToolTipText(MTGControler.getInstance().getLangService().getCapitalize("EXPORT_RESULTS"));
		btnExport.setEnabled(false);
		listEdition.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		thumbnailPanel.enableDragging(false);
		panneauCentral.setOrientation(JSplitPane.VERTICAL_SPLIT);
		panneauCentral.setRightComponent(tabbedCardsInfo);
		panneauCentral.setLeftComponent(tabbedCardsView);
		tableCards.setRowHeight(MTGConstants.TABLE_ROW_HEIGHT);
		tableCards.setRowSorter(sorterCards);
		
		
		cardsModeltable.setDefaultHiddenComlumns(8,9,10);
		for(int i : cardsModeltable.defaultHiddenColumns())
		{
			tableCards.getColumnExt(cardsModeltable.getColumnName(i)).setVisible(false);
		}
		
		panneauCentral.setDividerLocation(0.5);
		panneauCentral.setResizeWeight(0.5);
		
		/////// LAYOUT
		setLayout(new BorderLayout());
		panneauStat.setLayout(new GridLayout(2, 2, 0, 0));
		panneauCard.setLayout(new BorderLayout());
		editionDetailPanel.setLayout(new BorderLayout());
		panelResultsCards.setLayout(new BorderLayout(0, 0));
		panelmana.setLayout(new GridLayout(1, 0, 2, 2));

		FlowLayout flpanelFilters = (FlowLayout) panelFilters.getLayout();
		flpanelFilters.setAlignment(FlowLayout.LEFT);

		FlowLayout flowLayout = (FlowLayout) panneauHaut.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);

		/////// DIMENSION
		thumbnailPanel.setThumbnailSize(new Dimension(179, 240));
		tabbedCardsInfo.setPreferredSize(new Dimension(0, 350));
		historyChartPanel.setPreferredSize(new Dimension(400, 10));
		cardsPicPanel.setPreferredSize(new Dimension(400, 10));
		tabbedCardsInfo.setMinimumSize(new Dimension(23, 200));
		scrollThumbnails.getVerticalScrollBar().setUnitIncrement(10);
		txtFilter.setColumns(25);
	
		/////// VISIBILITY
		tableCards.setColumnControlVisible(true);
		panelFilters.setVisible(false);
		lblLoading.setVisible(false);
		tableCards.setShowVerticalLines(false);
	
		////// ADD PANELS
		for (String s : new String[] { "W", "U", "B", "R", "G", "C", "1" }) {
			final JButton btnG = new JButton();
			btnG.setToolTipText(s);
			if (s.equals("1"))
				btnG.setToolTipText("[0-9]*");

			btnG.setIcon(new ImageIcon(pan.getManaSymbol(s).getScaledInstance(15, 15, Image.SCALE_SMOOTH)));
			btnG.setForeground(btnG.getBackground());
			btnG.addActionListener(e -> {
				txtFilter.setText("\\{" + btnG.getToolTipText() + "}");
				sorterCards.setRowFilter(RowFilter.regexFilter(txtFilter.getText()));
			});
			panelmana.add(btnG);

		}
		scrollThumbnails.setViewportView(thumbnailPanel);
		thumbnailPanel.setEnclosingScrollPane(scrollThumbnails);
		

		panneauHaut.add(searchComponent);
		panneauHaut.add(advancedSearch);
		panneauHaut.add(btnFilter);
		panneauHaut.add(btnExport);
		panneauHaut.add(lblLoading);
		panneauCard.add(new JScrollPane(listEdition), BorderLayout.SOUTH);
		panneauCard.add(cardsPicPanel, BorderLayout.CENTER);

		panelResultsCards.add(panelFilters, BorderLayout.NORTH);
		panelResultsCards.add(new JScrollPane(tableCards));
		magicEditionDetailPanel = new MagicEditionDetailPanel();

		editionDetailPanel.add(magicEditionDetailPanel, BorderLayout.CENTER);

		panelFilters.add(lblFilter);
		panelFilters.add(txtFilter);
		panelFilters.add(btnClear);
		panelFilters.add(panelmana);

		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("DETAILS"), MTGConstants.ICON_TAB_DETAILS,detailCardPanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("EDITION"), MTGConstants.ICON_BACK,editionDetailPanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("PRICES"), MTGConstants.ICON_TAB_PRICES,priceTablePanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("RULES"), MTGConstants.ICON_TAB_RULES,new JScrollPane(txtRulesArea), null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("PRICE_VARIATIONS"), MTGConstants.ICON_TAB_VARIATIONS,historyChartPanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("MORE_LIKE_THIS"), MTGConstants.ICON_TAB_SIMILARITY,similarityPanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("DECK_MODULE"), MTGConstants.ICON_TAB_DECK,deckPanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("STOCK"), MTGConstants.ICON_TAB_STOCK,stockPanel, null);
		tabbedCardsInfo.addTab(MTGControler.getInstance().getLangService().getCapitalize("ABILITIES"), abilitiesPanel.getIcon(),abilitiesPanel, null);
		
		if (MTGControler.getInstance().get("debug-json-panel").equalsIgnoreCase("true"))
			tabbedCardsInfo.addTab("Object", MTGConstants.ICON_TAB_JSON, panelJson, null);

		panneauStat.add(cmcChart);
		panneauStat.add(manaRepartitionPanel);
		panneauStat.add(typeRepartitionPanel);
		panneauStat.add(rarityRepartitionPanel);

		tabbedCardsView.addTab(MTGControler.getInstance().getLangService().getCapitalize("RESULTS"),  MTGConstants.ICON_TAB_RESULTS,panelResultsCards, null);
		tabbedCardsView.addTab(MTGControler.getInstance().getLangService().getCapitalize("THUMBNAIL"), MTGConstants.ICON_TAB_THUMBNAIL,scrollThumbnails, null);
		tabbedCardsView.addTab(MTGControler.getInstance().getLangService().getCapitalize("STATS"), MTGConstants.ICON_TAB_ANALYSE, panneauStat,null);

		add(panneauHaut, BorderLayout.NORTH);
		add(panneauCard, BorderLayout.EAST);
		add(panneauCentral, BorderLayout.CENTER);

		/////// Right click
		try {
			initPopupCollection();
		} catch (Exception e2) {
			logger.error("error init popup",e2);
		}

		/////// Action listners
		addComponentListener(new ComponentAdapter() {
			@Override
		    public void componentShown(ComponentEvent componentEvent){
		        searchComponent.requestFocus();
				panneauCentral.setDividerLocation(.45);
				removeComponentListener(this);
		    }
		}); 
		
		
		similarityPanel.getTableSimilarity().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				
				if(similarityPanel.getTableSimilarity().getSelectedRow()==-1)
					return;
				
				MagicCard mc = UITools.getTableSelection(similarityPanel.getTableSimilarity(), 0);
				cardsPicPanel.showPhoto(mc);
			}
		});
		
		
		advancedSearch.addActionListener(il->{
			AdvancedSearchQueryDialog diag = new AdvancedSearchQueryDialog();
									  diag.setVisible(true);
									
			if(diag.getCrits().isEmpty())
				return;
			
			
			lblLoading.start();
			lblLoading.setText(MTGControler.getInstance().getLangService().getCapitalize("SEARCHING"));
			cardsModeltable.clear();
			
			AbstractObservableWorker<List<MagicCard>, MagicCard, MTGCardsProvider> wk = new AbstractObservableWorker<>(lblLoading,MTGControler.getInstance().getEnabled(MTGCardsProvider.class)) {

						@Override
						protected List<MagicCard> doInBackground() throws Exception {
							return plug.searchByCriteria(diag.getCrits());
						}

						@Override
						protected void process(List<MagicCard> chunks) {
							super.process(chunks);
							cardsModeltable.addItems(chunks);
						}

						@Override
						protected void done() {
							super.done();
							open(getResult());
							btnExport.setEnabled(tableCards.getRowCount() > 0);
						}
						
						
			};
			
			
			ThreadManager.getInstance().runInEdt(wk,"searching "+diag.getCrits());
			
			
		});
		
		btnClear.addActionListener(ae -> {
			txtFilter.setText("");
			sorterCards.setRowFilter(null);
		});

		btnFilter.addActionListener(ae -> panelFilters.setVisible(!panelFilters.isVisible()));

		searchComponent.addButton(defaultEnterButton,true);
		
		defaultEnterButton.addActionListener(el->{
					selectedEdition = null;
					lblLoading.start();
					lblLoading.setText(MTGControler.getInstance().getLangService().getCapitalize("SEARCHING"));
					
					MTGPlugin plug = searchComponent.isCollectionSearch() ? MTGControler.getInstance().getEnabled(MTGDao.class):MTGControler.getInstance().getEnabled(MTGCardsProvider.class);
					cardsModeltable.clear();
					
					AbstractObservableWorker<List<MagicCard>, MagicCard, MTGPlugin> wk = new AbstractObservableWorker<>(lblLoading,plug) {
						@Override
						protected List<MagicCard> doInBackground() throws Exception {
							List<MagicCard> cards;
							
							if (searchComponent.isCollectionSearch()) {
								cards=((MTGDao)plug).listCardsFromCollection((MagicCollection) searchComponent.getMTGCriteria().getFirst());
							}
							else if(searchComponent.isSetSearch()) {
								cards=((MTGCardsProvider)plug).searchCardByEdition((MagicEdition)searchComponent.getMTGCriteria().getFirst());
							}
							else if (searchComponent.isAllCardsSearch()) {
								cards=((MTGCardsProvider)plug).listAllCards();
								
							}
							else {
								cards=((MTGCardsProvider)plug).searchCardByCriteria(searchComponent.getMTGCriteria().getAtt(), ((MTGCardsProvider)plug).getMTGQueryManager().getValueFor(searchComponent.getMTGCriteria().getFirst()).toString(), null, false);
							}
							
							try {
								Collections.sort(cards, new CardsEditionSorter());
							}
							catch(IllegalArgumentException e)
							{
								logger.error("error sorting result "+e);
							}
							
							return cards;
						}
						
						@Override
						protected void process(List<MagicCard> chunks) {
							super.process(chunks);
							cardsModeltable.addItems(chunks);
						}

						@Override
						protected void done() {
							super.done();
							open(getResult());
							btnExport.setEnabled(tableCards.getRowCount() > 0);
						}
					};
					ThreadManager.getInstance().runInEdt(wk,"searching "+searchComponent.getMTGCriteria());
		});
		
		tableCards.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				try {
					selectedCard = UITools.getTableSelection(tableCards, 0);
					
					if(selectedCard==null)
						return;
					
					selectedEdition = selectedCard.getCurrentSet();
					updateCards();
				} catch (Exception e) {
					logger.error("error selecting line",e);
				}
			}
		});
		
		
		
		
		tableCards.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				
				if(tableCards.getSelectedRow()==-1)
					return;
				
				
				if (SwingUtilities.isRightMouseButton(evt)) {
					Point point = evt.getPoint();
					popupMenu.show(tableCards, (int) point.getX(), (int) point.getY());
				} else {
					try {
						selectedCard = UITools.getTableSelection(tableCards, 0);
						selectedEdition = selectedCard.getCurrentSet();
						updateCards();
					} catch (Exception e) {
						logger.error(e);
					}

				}
			}
		});

		listEdition.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent mev) {
				selectedEdition = listEdition.getSelectedValue();
				
				
				SwingWorker<MagicCard, MagicCard> sw = new SwingWorker<>()
						{
							
							@Override
							protected void process(List<MagicCard> chunks) {
								detailCardPanel.setMagicCard(chunks.get(0));
								magicEditionDetailPanel.setMagicEdition(selectedEdition);
							} 
					
							@Override
							protected MagicCard doInBackground() throws Exception {
								try {
									MagicCard mc = MTGControler.getInstance().getEnabled(MTGCardsProvider.class).searchCardByName( selectedCard.getName(), selectedEdition, false).get(0);
									publish(mc);
									return mc;
								} catch (Exception e) {
									logger.error(e);
									return null;
								}
								
								
							}
							@Override
							protected void done() {
								try {
									selectedCard = get();
									cardsPicPanel.showPhoto(selectedCard); // backcard
									historyChartPanel.init(selectedCard, selectedEdition, selectedCard.getName());
									priceTablePanel.init(selectedCard,selectedEdition);
								} catch (Exception e) {
									logger.error(e);
								} 
							}
					
						};
				
				
				ThreadManager.getInstance().runInEdt(sw,"loading " + selectedCard);
			}
		});
	
		detailCardPanel.addObserver((Observable o, Object obj)->{
			MagicCardNames selLang = (MagicCardNames)obj;
			try {
					MagicEdition ed = (MagicEdition) BeanUtils.cloneBean(selectedEdition);
								 ed.setMultiverseid(String.valueOf(selLang.getGathererId()));

					logger.trace("change lang to " + selLang + " for " + ed);
					cardsPicPanel.showPhoto(selectedCard, ed);
			} catch (Exception e1) {
				logger.error(e1);
			}
			
		});
		
		btnExport.initCardsExport(new Callable<MagicDeck>() {
			@Override
			public MagicDeck call() throws Exception {
				List<MagicCard> export = UITools.getTableSelections(tableCards,0);
				
				if(export.isEmpty())
					export =  ((MagicCardTableModel) tableCards.getRowSorter().getModel()).getItems();
				
				return MagicDeck.toDeck(export);
			}
		}, lblLoading);

		txtFilter.addActionListener(ae -> {
			String text = txtFilter.getText();
			if (text.length() == 0) {
				sorterCards.setRowFilter(null);
			} else {
				sorterCards.setRowFilter(RowFilter.regexFilter(text));
			}
		});

		thumbnailPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				DisplayableCard lab = (DisplayableCard) thumbnailPanel.getComponentAt(new Point(e.getX(), e.getY()));
				selectedCard = lab.getMagicCard();
				selectedEdition = lab.getMagicCard().getCurrentSet();
				cardsPicPanel.showPhoto(selectedCard);
				updateCards();
			}

		});
	}

	
	
	public void thumbnail(List<MagicCard> cards) {
		tabbedCardsView.setSelectedIndex(INDEX_THUMB);
		thumbnailPanel.initThumbnails(cards, false, false);
	}

	public void setSelectedCard(MagicCard mc) {
		this.selectedCard = mc;
		updateCards();
	}

	@Override
	public void onFirstShowing() {
		SwingUtilities.getRootPane(this).setDefaultButton(defaultEnterButton);
	}
	
	
	public CardSearchPanel() {

		try {
			
			initGUI();
		} catch (Exception e) {
			logger.error("Error init", e);
			MTGControler.getInstance().notify(e);
		}

		logger.debug("construction of SearchGUI : done");
	}

	public HandPanel getThumbnailPanel() {
		return thumbnailPanel;
	}

	public void updateCards() {
		try {
			txtRulesArea.setText("");

			((DefaultListModel<MagicEdition>) listEdition.getModel()).removeAllElements();

			for (MagicEdition me : selectedCard.getEditions())
				((DefaultListModel<MagicEdition>) listEdition.getModel()).addElement(me);

			detailCardPanel.setMagicCard(selectedCard, true);
			magicEditionDetailPanel.setMagicEdition(selectedCard.getCurrentSet());
			cardsPicPanel.showPhoto(selectedCard, selectedEdition);
			
			for (MagicRuling mr : selectedCard.getRulings()) {
				txtRulesArea.append(mr.toString());
				txtRulesArea.append("\n");
			}

			priceTablePanel.init(selectedCard,selectedEdition);
			similarityPanel.init(selectedCard);
			panelJson.show(selectedCard);
			deckPanel.init(selectedCard);
			stockPanel.initMagicCardStock(selectedCard, new MagicCollection(MTGControler.getInstance().get("default-library")));
			abilitiesPanel.init(selectedCard);
			ThreadManager.getInstance().executeThread(
					() -> historyChartPanel.init(selectedCard, selectedEdition, selectedCard.getName()),
					"load history for " + selectedEdition);

		} catch (Exception e1) {
			logger.error("error ",e1);
		}

	}

	public void open(List<MagicCard> cards) {
		logger.debug("results " + cards.size() + " cards");

		if (!cards.isEmpty()) {
			cardsModeltable.init(cards);
			thumbnailPanel.initThumbnails(cards, false, false);
			cmcChart.init(cards);
			typeRepartitionPanel.init(cards);
			manaRepartitionPanel.init(cards);
			rarityRepartitionPanel.init(cards);
			tabbedCardsView.setTitleAt(0, MTGControler.getInstance().getLangService().getCapitalize("RESULTS") + " ("+ cardsModeltable.getRowCount() + ")");
			btnExport.setEnabled(true);
		}
	}

}
