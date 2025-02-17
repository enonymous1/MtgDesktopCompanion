package org.magic.game.gui.components;

import static org.magic.tools.MTG.capitalize;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import org.jdesktop.swingx.JXTable;
import org.magic.api.beans.MTGNotification;
import org.magic.api.beans.MagicDeck;
import org.magic.api.interfaces.MTGNetworkClient;
import org.magic.game.model.Player;
import org.magic.game.model.Player.STATE;
import org.magic.game.network.MinaClient;
import org.magic.game.network.actions.AbstractNetworkAction;
import org.magic.game.network.actions.ListPlayersAction;
import org.magic.game.network.actions.ReponseAction;
import org.magic.game.network.actions.ReponseAction.CHOICE;
import org.magic.game.network.actions.RequestPlayAction;
import org.magic.game.network.actions.ShareDeckAction;
import org.magic.game.network.actions.SpeakAction;
import org.magic.gui.components.dialog.JDeckChooserDialog;
import org.magic.gui.renderer.ManaCellRenderer;
import org.magic.services.MTGConstants;
import org.magic.services.MTGControler;
import org.magic.services.threads.MTGRunnable;
import org.magic.services.threads.ThreadManager;
import org.magic.tools.UITools;
import org.utils.patterns.observer.Observable;
import org.utils.patterns.observer.Observer;


public class GamingRoomPanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JTextField txtServer;
	private JTextField txtPort;
	private JXTable table;
	private transient MTGNetworkClient client;
	private PlayerTableModel mod;
	private JTextField txtName;
	private JList<AbstractNetworkAction> list = new JList<>(new DefaultListModel<>());
	private JButton btnPlayGame;
	private JButton btnConnect;
	private JButton btnLogout;
	private JTextArea editorPane;
	private JComboBox<STATE> cboStates;
	private JButton btnShareDeck;
	private JButton btnColorChoose;
	private Player otherplayer = null;
	private JButton btnDeck ;
	
	private transient Observer obs = new Observer() {

		private void printMessage(AbstractNetworkAction sa) {
			((DefaultListModel<AbstractNetworkAction>) list.getModel()).addElement(sa);
		}

		@Override
		public void update(Observable o, Object arg) {
			if (arg instanceof ShareDeckAction lpa) {
				printMessage(lpa);
			}
			if (arg instanceof ListPlayersAction lpa) {
				mod.init(lpa.getList());
			}
			if (arg instanceof SpeakAction lpa) {
				printMessage(lpa);
			}
			if (arg instanceof RequestPlayAction lpa) {
				int res = JOptionPane.showConfirmDialog(getRootPane(),
						capitalize("CHALLENGE_REQUEST",lpa.getRequestPlayer()),
						capitalize("NEW_GAME_REQUEST"),
						JOptionPane.YES_NO_OPTION);

				if (res == JOptionPane.YES_OPTION) {
					client.reponse(lpa, CHOICE.YES);
				} else {
					client.reponse(lpa, CHOICE.NO);
				}
			}
			if (arg instanceof ReponseAction rep) {
				ReponseAction resp = rep;
				if (resp.getReponse().equals(ReponseAction.CHOICE.YES)) {
					printMessage(new SpeakAction(resp.getRequest().getAskedPlayer(),
							capitalize("CHALLENGE_ACCEPTED")));
					client.changeStatus(STATE.GAMING);
					GamePanelGUI.getInstance().setPlayer(client.getPlayer());
					GamePanelGUI.getInstance().addPlayer(resp.getRequest().getAskedPlayer());
					GamePanelGUI.getInstance().initGame();
				} else {
					printMessage(new SpeakAction(resp.getRequest().getAskedPlayer(),
							" " + capitalize("CHALLENGE_DECLINE")));
				}
			}
		}
	};

	public GamingRoomPanel() {
		setLayout(new BorderLayout(0, 0));
	
		btnLogout = new JButton(capitalize("LOGOUT"));
		var lblIp = new JLabel(capitalize("HOST") + " :");
		btnConnect = new JButton(capitalize("CONNECT"));
		var lblName = new JLabel(capitalize("NAME") + " :");
		var panneauHaut = new JPanel();
		txtServer = new JTextField();
		var lblPort = new JLabel("Port :");
		txtPort = new JTextField();
		txtName = new JTextField();
		mod = new PlayerTableModel();
		table = UITools.createNewTable(mod);
		var panneauBas = new JPanel();
		btnDeck = new JButton("Change deck");
		btnPlayGame = new JButton("Ask for Game");
		var panel = new JPanel();
		editorPane = new JTextArea();
		var panel1 = new JPanel();
		btnShareDeck = new JButton(MTGConstants.ICON_DECK);
		btnColorChoose = new JButton(MTGConstants.ICON_GAME_COLOR);
		cboStates = new JComboBox<>(new DefaultComboBoxModel<>(STATE.values()));
		var panelChatBox = new JPanel();
		
		txtServer.setText("");
		txtServer.setColumns(10);
		txtPort.setText("18567");
		txtPort.setColumns(10);
		txtName.setColumns(10);
		txtName.setText(MTGControler.getInstance().get("/game/player-profil/name"));
		btnLogout.setEnabled(false);
		table.getColumnModel().getColumn(2).setCellRenderer(new ManaCellRenderer());
		btnPlayGame.setEnabled(false);
		panel.setLayout(new BorderLayout(0, 0));
		panelChatBox.setLayout(new BorderLayout(0, 0));
		editorPane.setText(capitalize("CHAT_INTRO_TEXT"));
		editorPane.setLineWrap(true);
		editorPane.setWrapStyleWord(true);
		editorPane.setRows(2);
		btnShareDeck.setToolTipText("Share a deck");
		

		
		try {
			editorPane.setForeground(new Color(Integer.parseInt(MTGControler.getInstance().get("/game/player-profil/foreground"))));
		} catch (Exception e) {
			editorPane.setForeground(Color.BLACK);
		}
		
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,boolean cellHasFocus) {
				var label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,cellHasFocus);
				try {
					label.setForeground(((SpeakAction) value).getColor());
				} catch (Exception e) {
					// do nothing
				}
				return label;
			}
		});

		add(panneauHaut, BorderLayout.NORTH);
		panneauHaut.add(lblIp);
		panneauHaut.add(txtServer);
		panneauHaut.add(lblPort);
		panneauHaut.add(txtPort);
		panneauHaut.add(lblName);
		panneauHaut.add(txtName);
		panneauHaut.add(btnConnect);
		panneauHaut.add(btnLogout);
		add(new JScrollPane(table), BorderLayout.CENTER);
		add(panneauBas, BorderLayout.SOUTH);
		panneauBas.add(btnDeck);
		panneauBas.add(btnPlayGame);
		add(panel, BorderLayout.EAST);
		panel.add(new JScrollPane(list), BorderLayout.CENTER);
		panel.add(panelChatBox, BorderLayout.SOUTH);
		panelChatBox.add(editorPane, BorderLayout.CENTER);
		panelChatBox.add(panel1, BorderLayout.NORTH);
		panel1.add(btnShareDeck);
		panel1.add(btnColorChoose);
		panel1.add(cboStates);
		
		
		initActions();
	}
	
	private void initActions() {
		btnConnect.addActionListener(ae -> {
			try {
				client = new MinaClient(txtServer.getText(), Integer.parseInt(txtPort.getText()));
				client.addObserver(obs);
				client.getPlayer().setName(txtName.getText());
				client.join();

				ThreadManager.getInstance().executeThread(new MTGRunnable() {

					@Override
					protected void auditedRun() {
						while (client.isActive()) {
							txtName.setEnabled(!client.isActive());
							txtServer.setEnabled(!client.isActive());
							txtPort.setEnabled(!client.isActive());
							btnConnect.setEnabled(false);
							btnLogout.setEnabled(true);
						}
						txtName.setEnabled(true);
						txtServer.setEnabled(true);
						txtPort.setEnabled(true);
						btnConnect.setEnabled(true);
						btnLogout.setEnabled(false);
						
					}
					
				},"alived connection listener");
						
						
						
						
	
			} catch (Exception e) {
				MTGControler.getInstance().notify(new MTGNotification(MTGControler.getInstance().getLangService().getError(),e));
			}
		});

	

		btnLogout.addActionListener(ae -> {
			client.sendMessage("logged out");
			client.logout();
		});
	

		
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				int modelrow = table.convertRowIndexToModel(table.getSelectedRow());
				otherplayer = (Player) table.getModel().getValueAt(modelrow, 0);

				if (otherplayer != null && otherplayer.getDeck() != null)
					btnPlayGame.setEnabled(true);

			}
		});
	
	

		btnPlayGame.addActionListener(e -> {
			int res = JOptionPane.showConfirmDialog(getRootPane(), "Want to play with " + otherplayer + " ?",
					"Gaming request", JOptionPane.YES_NO_OPTION);
			if (res == JOptionPane.YES_OPTION)
				client.requestPlay(otherplayer);
		});
		
		
		btnColorChoose.addActionListener(ae -> {
			var c = JColorChooser.showDialog(null, "Choose Text Color", Color.BLACK);
			
			if(c!=null) {
			editorPane.setForeground(c);
			MTGControler.getInstance().setProperty("/game/player-profil/foreground", c.getRGB());
			}
		});

		editorPane.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent fe) {
				if (editorPane.getText()
						.equals(capitalize("CHAT_INTRO_TEXT")))
					editorPane.setText("");
			}
		});

		editorPane.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(java.awt.event.KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					client.sendMessage(editorPane.getText(), editorPane.getForeground());
					editorPane.setText("");
				}

			}

		});

		btnDeck.addActionListener(ae -> {

			var diag = new JDeckChooserDialog();
			diag.setVisible(true);
			MagicDeck d = diag.getSelectedDeck();
			client.updateDeck(d);
		});

	

		btnShareDeck.addActionListener(ae -> {
			var diag = new JDeckChooserDialog();
			diag.setVisible(true);
			var d = diag.getSelectedDeck();
			var p = (Player) table.getModel().getValueAt(table.getSelectedRow(), 0);
			client.sendDeck(d, p);
		});
		
		
		cboStates.addItemListener(ie -> client.changeStatus((STATE) cboStates.getSelectedItem()));

	}

	

}

class PlayerTableModel extends DefaultTableModel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final String[] columns = { capitalize("PLAYER"),
			capitalize("DECK"),
			capitalize("CARD_COLOR"),
			capitalize("FORMAT"),
			capitalize("COUNTRY"),
			capitalize("STATE") };
	private List<Player> players;

	public void init(List<Player> play) {
		this.players = play;
		fireTableDataChanged();
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return false;
	}

	@Override
	public int getColumnCount() {
		return columns.length;
	}

	@Override
	public String getColumnName(int column) {
		return columns[column];
	}

	@Override
	public int getRowCount() {
		if (players == null)
			return 0;

		return players.size();
	}

	@Override
	public Object getValueAt(int row, int column) {
		switch (column) {
		case 0:
			return players.get(row);
		case 1:
			return players.get(row).getDeck();
		case 2:
			return players.get(row).getDeck().getColors();
		case 3:
			return players.get(row).getDeck().getLegality();
		case 4:
			return players.get(row).getLocal();
		case 5:
			return players.get(row).getState();
		default:
			return null;
		}
	}

}
