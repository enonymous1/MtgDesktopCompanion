package org.magic.servers.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.magic.api.interfaces.abstracts.AbstractMTGServer;
import org.magic.game.model.Player;
import org.magic.game.model.Player.STATE;
import org.magic.game.network.actions.AbstractNetworkAction;
import org.magic.game.network.actions.ChangeDeckAction;
import org.magic.game.network.actions.ChangeStatusAction;
import org.magic.game.network.actions.JoinAction;
import org.magic.game.network.actions.ListPlayersAction;
import org.magic.game.network.actions.ReponseAction;
import org.magic.game.network.actions.RequestPlayAction;
import org.magic.game.network.actions.ShareDeckAction;
import org.magic.game.network.actions.SpeakAction;
import org.magic.services.MTGConstants;

public class MTGGameRoomServer extends AbstractMTGServer {

	private static final String SERVER_PORT = "SERVER-PORT";
	private static final String PLAYER = "PLAYER";
	private static final String MAX_CLIENT = "MAX_CLIENT";
	
	
	private IoAcceptor acceptor;
	private IoHandlerAdapter adapter = new IoHandlerAdapter() {

		private void playerUpdate(ChangeStatusAction act) {
			((Player) acceptor.getManagedSessions().get(act.getPlayer().getId()).getAttribute(PLAYER))
					.setState(act.getPlayer().getState());
		}

		private void sendDeck(ShareDeckAction act) {
			acceptor.getManagedSessions().get(act.getTo().getId()).write(act);
		}

		private void join(IoSession session, JoinAction ja) {
			if (!getString(MAX_CLIENT).equals("0")
					&& acceptor.getManagedSessions().size() >= getInt(MAX_CLIENT)) {
				session.write(new SpeakAction(null, "Number of users reached (" + getString(MAX_CLIENT) + ")"));
				session.closeOnFlush();
				return;
			}
			ja.getPlayer().setState(STATE.CONNECTED);
			ja.getPlayer().setId(session.getId());
			session.setAttribute(PLAYER, ja.getPlayer());
			speak(new SpeakAction(ja.getPlayer(), " is now connected"));
			session.write(session.getId());

			refreshPlayers(session);
		}

		@Override
		public void sessionCreated(IoSession session) throws Exception {
			logger.debug("New Session " + session.getRemoteAddress());
			session.write(new SpeakAction(null, getString("WELCOME_MESSAGE")));
		}

		@Override
		public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
			refreshPlayers(session); // refresh list users
		}

		@Override
		public void messageReceived(IoSession session, Object message) throws Exception {
			logger.info(message);
			
			if (message instanceof AbstractNetworkAction act) {
				switch (act.getAct()) {
				case REQUEST_PLAY:
					requestGaming((RequestPlayAction) act);
					break;
				case RESPONSE:
					response((ReponseAction) act);
					break;
				case JOIN:
					join(session, (JoinAction) act);
					break;
				case CHANGE_DECK:
					changeDeck(session, (ChangeDeckAction) act);
					break;
				case SPEAK:
					speak((SpeakAction) act);
					break;
				case CHANGE_STATUS:
					playerUpdate((ChangeStatusAction) act);
					break;
				case SHARE:
					sendDeck((ShareDeckAction) act);
					break;
				default:
					break;
				}
			}
		}

		private void response(ReponseAction act) {
			IoSession s = acceptor.getManagedSessions().get(act.getRequest().getRequestPlayer().getId());
			IoSession s2 = acceptor.getManagedSessions().get(act.getRequest().getAskedPlayer().getId());
			s.write(act);
			s2.write(act);
		}

		@Override
		public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
			logger.error("error sesion", cause);
			refreshPlayers(session);
		}
	};

	@Override
	public String description() {
		return "Enable local player room server";
	}

	public void speak(SpeakAction sa) {
		for (IoSession s : acceptor.getManagedSessions().values())
			s.write(sa);
	}

	protected void changeDeck(IoSession session, ChangeDeckAction cda) {
		Player p = (Player) session.getAttribute(PLAYER);
		p.setDeck(cda.getDeck());
		session.setAttribute(PLAYER, p);

	}

	protected void requestGaming(RequestPlayAction p) {
		IoSession s = acceptor.getManagedSessions().get(p.getAskedPlayer().getId());
		s.write(p);

	}

	public void refreshPlayers(IoSession session) {
		List<Player> list = new ArrayList<>();
		for (IoSession s : acceptor.getManagedSessions().values()) {
			if (session.getId() != ((Player) s.getAttribute(PLAYER)).getId())
				list.add((Player) s.getAttribute(PLAYER));
		}

		session.write(new ListPlayersAction(list));
	}

	public MTGGameRoomServer() throws IOException {

		super();
		acceptor = new NioSocketAcceptor();
		acceptor.setHandler(adapter);
		acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));
		acceptor.getSessionConfig().setReadBufferSize(getInt("BUFFER-SIZE"));
		acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE,getInt("IDLE-TIME"));
	}

	@Override
	public void start() throws IOException {
		acceptor.bind(new InetSocketAddress(getInt(SERVER_PORT)));
		logger.info("Server started on port " + getString(SERVER_PORT) + " ...");
	}

	@Override
	public void stop() throws IOException {
		logger.info("Server closed");
		acceptor.unbind();
	}

	@Override
	public boolean isAlive() {
		return acceptor.isActive();
	}

	@Override
	public boolean isAutostart() {
		return getBoolean("AUTOSTART");
	}

	@Override
	public String getName() {
		return "MTG Game Server";
	}

	@Override
	public STATUT getStatut() {
		return STATUT.DEV;
	}

	@Override
	public Map<String, String> getDefaultAttributes() {
		return Map.of(SERVER_PORT, "18567",
				 "IDLE-TIME", "10",
				 "BUFFER-SIZE", "2048",
				 "AUTOSTART", "false",
				 "WELCOME_MESSAGE", "Welcome to my MTG Desktop Gaming Room",
				 MAX_CLIENT, "0");

	}
	
	@Override
	public Icon getIcon() {
		return MTGConstants.ICON_GAME;
	}

	@Override
	public String getVersion() {
		return "2.0.21";
	}
	
}
