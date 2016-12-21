package com.nm.server.adapter.base.sm;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.descriptor.enumdefine.ProtocolType;
import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommandCollection.CmdsType;
import com.nm.message.Response;
import com.nm.message.ResponseCollection;
import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.EthGate.GateState;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.service.EthGateManager;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.util.ToolUtil;

public class SocketMgrServer extends Thread {
	private static final Log log = LogFactory.getLog(SocketMgrServer.class);

	// private byte[] ss_gate_lock = new byte[0]; // conSelector and scs share
	// one lock.
	private byte[] ss_lock = new byte[0];
	private byte[] cs_lock = new byte[0];
	private byte[] gate_lock = new byte[0];
	// private byte[] ping_lock = new byte[0];
	protected Selector _sel = null;
	protected Collection<SocketSession> socketSessionPool = null;
	protected Collection<SocketSession> connectSessionPool = null;
	private volatile boolean _run = true;

	private ClientMessageListener msgListener;

	private List<EthGate> ethGates = new Vector<EthGate>();

	private EthGateManager ethGateManager;

	private String adapterName;

	private Timer timer;

	private HashMap<String, Integer> mapPingCount = new HashMap<String, Integer>();
	private Map<Integer, GateState> gwMap = new HashMap<Integer, GateState>();

	private Map<Integer, ManagedElement> neMap;

	public SocketMgrServer(Selector sel) {

		this._sel = sel;
		this.socketSessionPool = new HashSet<SocketSession>();
		this.connectSessionPool = new HashSet<SocketSession>();

	}

	public Selector getSelector() {
		return this._sel;
	}

	public synchronized byte[] getGateLock() {
		return gate_lock;
	}

	public void setEthGateManager(EthGateManager ethGateManager) {
		this.ethGateManager = ethGateManager;
	}

	public void setMsgListener(ClientMessageListener msgListener) {
		this.msgListener = msgListener;
	}

	public void setAdapterName(String adapterName) {
		this.adapterName = adapterName;
	}

	public void setNeMap(Map<Integer, ManagedElement> neMap) {
		this.neMap = neMap;
	}

	public Map<Integer, ManagedElement> getNeMap() {
		return neMap;
	}

	public void addConnectSession(SocketSession s) {
		synchronized (cs_lock) {
			connectSessionPool.add(s);
		}

		_sel.wakeup();
	}

	public void removeConnectSession(SocketSession s) {
		synchronized (cs_lock) {
			connectSessionPool.remove(s);
		}

		gwStateTrap(((ConnectSession) s).getEthGate());
	}

	public void removeConnectChannel(EthGate gate) {
		synchronized (cs_lock) {
			Iterator<SocketSession> it = connectSessionPool.iterator();

			int ss_num = 0; // every socket channel has
							// two socket sessions(connect,read) in the
							// collection of _added.

			while (it.hasNext()) {
				ConnectSession ss = (ConnectSession) it.next();

				String ip = ToolUtil.longToIp(ss.getEthGate().getIpAddress());

				if (ss.gw.getNeId()==gate.getNeId()) {
					ss_num++;

					if (ss.key() != null) {
						ss.key().cancel();
					}

					it.remove();
				}

				if (ss_num == 1) {
					try {
						// if(this.socketExists(sc)) // gateways
						// {
						// this.updateEthGate(sc, GateState.NOT_CONNECT);
						// }
						// ss.key().cancel();
						ss.channel().socket().close();
						ss.channel().close();
					} catch (IOException e) {
						log.error("sockect channel close error:", e);
					}

					break;
				}

			}

			log.info("ss_num = " + ss_num);
		}
	}

	public void add(SocketSession s) {
		synchronized (ss_lock) {
			socketSessionPool.add(s);
		}

		_sel.wakeup();
	}

	public void remove(SocketSession s) {
		synchronized (ss_lock) {
			socketSessionPool.remove(s);
		}
	}

	public void remove(SocketChannel sc) {
		synchronized (ss_lock) {
			Iterator<SocketSession> it = socketSessionPool.iterator();

			int ss_num = 0; // every socket channel has
							// two socket sessions(connect,read) in the
							// collection of _added.

			while (it.hasNext()) {
				SocketSession ss = it.next();

				if(ss instanceof ReadSessionUdp){//TCP网关失连时,需要在连接池中移除该连接,只有在TCP连接的情况下才继续执行
					continue;
				}
				if (ss.channel().equals(sc)) {
					ss_num++;
					ss.key().cancel();
					it.remove();
				}

				if (ss_num == 1) {
					try {
						// if(this.socketExists(sc)) // gateways
						// {
						// this.updateEthGate(sc, GateState.NOT_CONNECT);
						// }
						// ss.key().cancel();
						ss.channel().socket().close();
						ss.channel().close();
					} catch (IOException e) {
						log.error("sockect channel close error:", e);
					}
					break;
				}

			}

			log.info("remove(SocketChannel sc) --- ss_num = " + ss_num);
		}
	}
	
	public void remove(DatagramChannel dc) {
		synchronized (ss_lock) {
			Iterator<SocketSession> it = socketSessionPool.iterator();
			int ss_num = 0;
			while (it.hasNext()) {
				SocketSession ss = it.next();
				if(!(ss instanceof ReadSessionUdp)){
					continue;
				}
				ReadSessionUdp sessionUdp = (ReadSessionUdp) ss;
				if (sessionUdp.get_dc().equals(dc)) {
					ss_num++;
					sessionUdp.key().cancel();
					it.remove();
				}
				if (ss_num == 1) {
					try {
						sessionUdp.get_dc().socket().close();
						sessionUdp.get_dc().close();
					} catch (IOException e) {
						log.error("udp sockect channel close error:", e);
					}
					break;
				}
			}
			log.info("ss_num = " + ss_num);
		}
	}

	public void addConnectSession(EthGate ethGate) {
		ConnectSession cs = null;

		try {
            ManagedElement me = null;
			synchronized(this.neMap) {
				me = this.neMap.get(ethGate.getNeId());
			}
			String strIp = me != null ? me.getNatIpEx() : ToolUtil.longToIp(ethGate.getIpAddress());
			int port = me != null ? me.getNatPort(ethGate.getPort()):ethGate.getPort();


			SocketChannel sc = null;
			// String strNic = ToolUtil.getHostIp(strIp);
			sc = SocketChannel.open();
			Socket s = sc.socket();
			s.setSoTimeout(0);
			s.setOOBInline(true);

			s.setKeepAlive(true);
			if(ethGate.getAppointIp() != null){
				s.bind(new InetSocketAddress(ethGate.getAppointIp(), 0));
			}else{
				s.bind(new InetSocketAddress("0.0.0.0", 0));
			}

			sc.configureBlocking(false);
			sc.connect(new InetSocketAddress(InetAddress.getByName(strIp), port));

			if (port == 4000) {
				cs = new SSLConnectSession(sc, msgListener);
			} else {
				cs = new ConnectSession(sc);
			}

			cs.setSm(this);
			cs.setEthGate(ethGate);
			cs.setEthGateManager(ethGateManager);
			this.addConnectSession(cs);

			if (sc.isOpen()) {
				SelectionKey key = sc.register(this._sel, cs.interestOps(), cs);
				cs.registered(key);
				log.info("#####addConnectSession cs_key.hashCode == " + key.hashCode());
			} else {
				this.removeConnectSession(cs);

				log.info("channel is closed!");
			}

			log.info("------- connecting gateway: " + strIp + ":" + port + " host = " + sc.socket().getLocalAddress().getHostAddress() + " ------");
			// log.info("------- add a object of ConnectSession _added.size()="
			// + _added.size() + " -------");

		} catch (ClosedChannelException e) {
			this.removeConnectSession(cs);

			log.error("add connectSession exception:", e);
		} catch (Exception e) {
			ethGate.setGateState(GateState.NOT_CONNECT);

			log.error("add connectSession exception:", e);
		}

	}

	public void addReadSession(SocketChannel sc) {
		try {
			ReadSession rs = new ReadSession(sc);
			rs.setMsgListener(msgListener);
			rs.setSm(this);

			this.add(rs);

			if (sc.isOpen()) {
				SelectionKey key = sc.register(this._sel, rs.interestOps(), rs);
				rs.registered(key);

			} else {
				this.remove(rs);

				log.info("channel is closed!");
			}

			InetAddress addr = sc.socket().getInetAddress();

			// synchronized(this.ping_lock)
			// {
			// this.mapPingCount.put(addr.toString(), 0);
			// }

			log.info("------ Ethernet gate connected: dest=" + addr.toString() + " ------");

			// log.info("------ add a object of ReadSession _added.size()=" +
			// _added.size()+ " -----");
		} catch (Exception e) {
			log.error("add readSession exception:", e);
		}

	}

	public void addReadSession(SocketChannel sc,EthGate gw) {
		try {
			ReadSession rs = null;
			if(gw.getGateProtocol() == ProtocolType.PT_OLP) {
				rs = new ReadSessionOlp(sc);
			} else {
				rs = new ReadSession(sc);
			}
			
			rs.setMsgListener(msgListener);
			rs.setSm(this);
			rs.setEthGate(gw);
			this.add(rs);

			if (sc.isOpen()) {
				SelectionKey key = sc.register(this._sel, rs.interestOps(), rs);
				rs.registered(key);
				log.info("#####addReadSession rs_key.hashCode == " + key.hashCode()
						+ "--- SocketChannel.hashcode = " + sc.hashCode()
						+ "--- EthGate.id = " + gw.getId() + "---EthGate.neid = " + gw.getNeId());
			} else {
				this.remove(rs);

				log.info("channel is closed!");
			}

			InetAddress addr = sc.socket().getInetAddress();

			// synchronized(this.ping_lock)
			// {
			// this.mapPingCount.put(addr.toString(), 0);
			// }

			log.info("------ Ethernet gate connected: dest=" + addr.toString() + " ------");

			// log.info("------ add a object of ReadSession _added.size()=" +
			// _added.size()+ " -----");
		} catch (Exception e) {
			log.error("add readSession exception:", e);
		}

	}
	
	public void addReadSession(EthGate gw) {
		ReadSession rs = null;
		try {
			if(gw.getGateProtocol() == ProtocolType.PT_UDP) {
				String strIp = ToolUtil.longToIp(gw.getIpAddress());
				int port = gw.getPort();
				
				rs = new ReadSessionUdp(null);
				DatagramChannel dc = DatagramChannel.open();
				dc.configureBlocking(false);
				DatagramSocket socket = dc.socket();
				socket.setSoTimeout(0);
				socket.bind(new InetSocketAddress("0.0.0.0", 0));
				dc.connect(new InetSocketAddress(InetAddress.getByName(strIp), port));
				rs.setMsgListener(msgListener);
				rs.setSm(this);
				rs.setEthGate(gw);
				rs.setEthGateManager(ethGateManager);
				((ReadSessionUdp)rs).set_dc(dc);
				if (dc.isConnected()) {
					this.add(rs);
					gw.setGateState(GateState.CONNECTED);
					SelectionKey key = dc.register(this._sel, rs.interestOps(), rs);
					rs.registered(key);
				} else {
					this.remove(rs);
					log.info("UDP channel is closed!");
				}
				InetAddress addr = dc.socket().getInetAddress();
				log.info("------ Ethernet gate connected: dest=" + addr.toString() + " ------");				
			}
		} catch (Exception e) {
			gw.setGateState(GateState.NOT_CONNECT);
			log.error("add readSessionUdp exception:", e);
		}
		if(rs!=null){
			rs.getEthGateManager().updateEthGate(gw);
		}
	}
	
	@Override
	public void run() {
		try {
			this.resetConnectState();
			this.getGateways();
			timer = new Timer("SocketMgrServer-RegisterConnect-timer");
			timer.scheduleAtFixedRate(new RegisterConnect(), 0, 10000);
//			timer.scheduleAtFixedRate(new KeepAlive(), 0, 120000);
			// timer.scheduleAtFixedRate(new UpdateConnect(), 0, 100);
			// timer.scheduleAtFixedRate(new RegisterConnectSession(), 0,
			// 10000);
			// timer.scheduleAtFixedRate(new RegisterReadSession(), 0, 10000);

			Thread.sleep(10000);

			while (_run) {
				try {

					if (_sel.select(10) == 0) {
						// log.info("---------no select event---------");
					}

					Set<SelectionKey> selected = _sel.selectedKeys();
					for (Iterator<SelectionKey> itr = selected.iterator(); itr.hasNext();) {
						SelectionKey key = (SelectionKey) itr.next();
						SocketSession s = (SocketSession) key.attachment();
						s.process();
						itr.remove();

						// log.info("----------"+s.getClass().getSimpleName()+"---------------");
					}

					// Thread.sleep(10);

					// synchronized(ss_lock)
					// {
					// for (Iterator<SocketSession> itr = _added.iterator();
					// itr.hasNext();)
					// {
					// SocketSession s = (SocketSession) itr.next();
					//
					// if(s.key() == null)
					// {
					// if(s.channel().isOpen())
					// {
					// SelectionKey key = s.channel().register(_sel,
					// s.interestOps(), s);
					// s.registered(key);
					// }
					// else
					// {
					// log.info("channel is closed!");
					// // this.updateEthGateConnectState(s.channel(),
					// GateState.NOT_CONNECT);
					// itr.remove();
					//
					// }
					//
					// }
					// }
					// }
				} catch (Exception e) {
					log.error("Socket Manager server run exception", e);
				}

			}

			this.clean();
		} catch (Exception e) {
			log.error("Socket Manager server run exception", e);
		}

	}

	private void registerConnectSession() {
		synchronized (cs_lock) {
			try {

				for (Iterator<SocketSession> itr = connectSessionPool.iterator(); itr.hasNext();) {
					SocketSession s = (SocketSession) itr.next();

					if (s.key() == null) {
						if (s.channel().isOpen()) {
							SelectionKey key = s.channel().register(this._sel, s.interestOps(), s);
							s.registered(key);

						} else {
							log.info("channel is closed!");
							// this.updateEthGateConnectState(s.channel(),
							// GateState.NOT_CONNECT);
							itr.remove();

						}

					}
				}
			} catch (Exception e) {
				log.error("RegisterSocketSession exception", e);
			}
		}
	}

	private void registerReadSession() {
		synchronized (ss_lock) {
			try {

				for (Iterator<SocketSession> itr = socketSessionPool.iterator(); itr.hasNext();) {
					SocketSession s = (SocketSession) itr.next();

					if (s.key() == null) {
						if (s.channel().isOpen()) {
							SelectionKey key = s.channel().register(_sel, s.interestOps(), s);
							s.registered(key);
						} else {
							log.info("channel is closed!");
							// this.updateEthGateConnectState(s.channel(),
							// GateState.NOT_CONNECT);
							itr.remove();

						}

					}
				}
			} catch (Exception e) {
				log.error("RegisterSocketSession exception", e);
			}
		}
	}

	private void resetConnectState() {
		this.ethGateManager.resetConnectState();
	}

	private void getGateways() {
		this.ethGates = this.ethGateManager.getEthGatesByAdapterName(this.adapterName);
	}

	public void addGateWay(EthGate ethGate) {
		if (ethGate != null) {
			synchronized (gate_lock) {
				boolean isExist = false;

				for (EthGate gw : this.ethGates) {
					if (gw.getNeId() == ethGate.getNeId()) {
						isExist = true;
						break;
					}
				}

				if (!isExist) {
					this.ethGates.add(ethGate);
				}

			}
		}

	}

	public void removeGateWay(long neId, boolean trapAlarm) {
		if (neId > 0) {
			synchronized (gate_lock) {
				Iterator<EthGate> it = this.ethGates.iterator();

				while (it.hasNext()) {
					EthGate gw = it.next();

					if (gw.getNeId() == neId) {
						String strIp = ToolUtil.longToIp(Long.valueOf(gw.getIpAddress()));
						try {
							this.removeSocketChannel(gw);
						} catch (Exception e) {
							log.error("remove gateway exception:", e);
						}

						if (trapAlarm) {
							gw.setGateState(GateState.NOT_CONNECT);
							gwStateTrap(gw);
						}

						deleteGwMap(gw);

						it.remove();
					}
				}

			}
		}

	}

	public void updateGateWay(EthGate ethGate) {
		if (ethGate != null) {
			synchronized (gate_lock) {

				for (EthGate gw : this.ethGates) {
					if (gw.getNeId() == ethGate.getNeId()) {

						gw.setIpAddress(ethGate.getIpAddress());
						gw.setPort(ethGate.getPort());
						gw.setGateState(ethGate.getGateState());
						gw.setAppointIp(ethGate.getAppointIp());
						break;
					}
				}

			}
		}

	}

	public void doConnect() {
		try {
			log.info("--------doConnect()-------------ethGates.size = " + ethGates.size());

			synchronized (gate_lock) {
				for (EthGate ethGate : ethGates) {

					synchronized(this.neMap) {
						if(this.neMap.get(ethGate.getNeId()) == null){
							log.info("##### this.neMap.get(ethGate.getNeId()) == null --- neId = " + ethGate.getNeId());
							continue;
						}
					}
					initGwMap(ethGate);

					if (!this.socketExists(ethGate)) {
						log.info("#####!this.socketExists(ethGate) --- ethGate.id = " + ethGate.getId());
						ethGate.setGateState(GateState.NOT_CONNECT);

						this.ethGateManager.updateEthGate(ethGate);

						gwStateTrap(ethGate);
					} 

					if (ethGate.getGateState() == GateState.CONNECTED) {
						this.updateSocket(ethGate);

						gwStateTrap(ethGate);
					}

					if (ethGate.getGateState() == GateState.NOT_CONNECT) {
						if(ethGate.isTcp()){//网关为TCP连接
							ethGate.setGateState(GateState.CONNECTING);
							this.addConnectSession(ethGate);
						}else{//网关为UDP连接
							ethGate.setGateState(GateState.CONNECTED);
							this.addReadSession(ethGate);
						}
					}

				}
			}

		} catch (Exception e) {
			log.error("Exception", e);

		}

	}

	private boolean ping(InetAddress addr, int port) throws Exception {
		boolean reachable = addr.isReachable(5000);

		if (log.isInfoEnabled()) {
			log.info("-------gateway: " + addr + ":" + port + (reachable ? " can be reached." : " can't be reached.---"));
		}

		return reachable;

	}

	private void updateSocket(EthGate ethGate) throws Exception {

		String strIp = ToolUtil.longToIp(ethGate.getIpAddress());
		int port = ethGate.getPort();

		InetAddress addr = InetAddress.getByName(strIp);

		log.info("-------gateway: " + addr + ":" + port + " can be reached.---");

		// if(!this.ping(addr, port))
		// {
		// synchronized(this.ping_lock)
		// {
		// String key = addr.toString();
		//
		// if(this.mapPingCount.get(key) != null)
		// {
		// int count = this.mapPingCount.get(key) + 1;
		//
		//
		// if(count >= 3)
		// {
		// this.mapPingCount.remove(key);
		//
		// this.removeSocketChannel(addr);
		// }
		// else
		// {
		// this.mapPingCount.put(key, count);
		// }
		//
		//
		// }
		// }
		// }

	}

	@SuppressWarnings("unused")
	private boolean socketExists(SocketChannel sc) {

		if (!sc.isConnected()) {
			return true;
		}

		InetAddress addr = sc.socket().getInetAddress();

		synchronized (gate_lock) {
			for (EthGate gate : ethGates) {
				try {
					if (addr.equals(InetAddress.getByName(ToolUtil.longToIp(gate.getIpAddress())))) {
						return true;
					}
				} catch (UnknownHostException e) {
					log.error("socket exists exception:", e);
				}
			}
		}

		return false;
	}

	private boolean socketExists(EthGate gate) {

		if (gate == null || gate.getGateState() != GateState.CONNECTED) {
			return true;
		}

		synchronized (ss_lock) {

			try {
				Iterator<SocketSession> it = socketSessionPool.iterator();

				while (it.hasNext()) {
					SocketSession ss = it.next();

					InetAddress addr;
					int port;
					if(gate.getGateProtocol()!=null 
							&& ProtocolType.PT_UDP.equals(gate.getGateProtocol())){//UDP
						if(!(ss instanceof ReadSessionUdp)){
							continue;
						}
						DatagramChannel dc = ((ReadSessionUdp)ss).get_dc();
						addr = dc.socket().getInetAddress();
						port =dc.socket().getPort();
					}else{//TCP
						if(ss instanceof ReadSessionUdp){
							continue;
						}
						SocketChannel sc = ss.channel();
						addr = sc.socket().getInetAddress();
						port =sc.socket().getPort();
					}
				
				ManagedElement me = null;
				
				synchronized(this.neMap) {
					me = this.neMap.get(gate.getNeId());
				}
				String strIp = me != null ? me.getNatIpEx() : ToolUtil.longToIp(gate.getIpAddress());
				int portEX = me != null ? me.getNatPort(gate.getPort()):gate.getPort();
				

				if (addr != null && addr.equals(InetAddress.getByName(strIp))&&port==portEX) {
					return true;
				}
             }

			} catch (Exception e) {
				log.error("socket exists exception:", e);
			}
		}

		return false;
	}

//	private void sendKeepAlivePacket() {
//		synchronized (ss_lock) {
//
//			try {
//				Iterator<SocketSession> it = socketSessionPool.iterator();
//
//				while (it.hasNext()) {
//					SocketSession ss = it.next();
//
//					SocketChannel sc = ss.channel();
//
//					byte[] frameB = new byte[1];
//					frameB[0] = (byte) 1;
//					ByteBuffer bb = ByteBuffer.wrap(frameB);
//					// 发送一个心跳包
//					sc.write(bb);
//				}
//			} catch (Exception e) {
//				log.error("send keepalive packet exception:", e);
//			}
//		}
//	}

	@SuppressWarnings("unused")
	private void updateEthGate(SocketChannel sc, GateState state) throws UnknownHostException {
		InetAddress gateAddr = sc.socket().getInetAddress();

		if (gateAddr == null) {
			return;
		}

		synchronized (gate_lock) {
			for (EthGate gate : ethGates) {
				if (InetAddress.getByName(ToolUtil.longToIp(gate.getIpAddress())).equals(gateAddr)) {
					gate.setGateState(state);
					// ethGateManager.updateEthGate(gate);
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private void updateGwConnect() {

		log.info("--------updateGwConnect()-------------");

		synchronized (ss_lock) {
			Iterator<SocketSession> it = socketSessionPool.iterator();

			while (it.hasNext()) {
				SocketSession ss = it.next();
				SocketChannel sc = ss.channel();

				try {

					if (sc.isConnected()) {
						if (!this.ping(sc.socket().getInetAddress(), sc.socket().getPort())) {
							// synchronized(this.ping_lock)
							{
								String key = sc.socket().getInetAddress().toString();

								if (this.mapPingCount.get(key) != null) {
									int count = this.mapPingCount.get(key) + 1;

									if (count >= 3) {
										this.mapPingCount.remove(key);

										sc.close();

										ss = null;

										it.remove();

									} else {
										this.mapPingCount.put(key, count);
									}

								}
							}
						}
					}

				} catch (Exception e) {
					log.error("update gw connect exception:", e);
				}

			}

			log.info("------- Retained SocketSession _added.size()=" + socketSessionPool.size() + " -------");

		}
	}

	public ReadSession getReadSession(EthGate gate) {

		synchronized (this.ss_lock) {
			Iterator<SocketSession> it = socketSessionPool.iterator();

			while (it.hasNext())

			{
				SocketSession ss = it.next();

				SocketChannel sc = ss.channel();

				if (ss.getEthGate().getNeId()==gate.getNeId()) {
					return (ReadSession) ss;
				}
			}
		}

		return null;
	}

	public SocketChannel getSocketChannel(EthGate gate) {

		synchronized (this.ss_lock) {
			Iterator<SocketSession> it = socketSessionPool.iterator();

			while (it.hasNext())

			{
				SocketSession ss = it.next();

				SocketChannel sc = ss.channel();

				if (ss.getEthGate() != null &&
						ss.getEthGate().getNeId()==gate.getNeId()) {
					return sc;
				}else if(ss.getEthGate() == null){
					log.info("######getSocketChannel()->ss.getEthGate() == null!!!");
				}

			}
		}

		return null;
	}

	public DatagramChannel getDatagramChannel(EthGate gate) {
		
		synchronized (this.ss_lock) {
			Iterator<SocketSession> it = socketSessionPool.iterator();
			while (it.hasNext()){
				SocketSession ss = it.next();
				if(ss instanceof ReadSessionUdp){
					DatagramChannel dc = ((ReadSessionUdp)ss).get_dc();
					if (ss.getEthGate()!=null 
							&& ss.getEthGate().getNeId()==gate.getNeId()) {
						return dc;
					}else if(ss.getEthGate() == null){
						log.info("######getDatagramChannel()->ss.getEthGate() == null!!!");
					}
				}
			}
		}

		return null;
	}
	
	public void removeSocketChannel(EthGate gate) {

		this.removeConnectChannel(gate);

		SocketChannel sc = this.getSocketChannel(gate);

		if (sc != null) {
			this.remove(sc);
		}

	}

	class UpdateConnect extends TimerTask {

		@Override
		public void run() {

			try {

			} catch (Exception e) {
				log.error("Exception", e);
			}

		}

	}

	class RegisterConnect extends TimerTask {

		@Override
		public void run() {
			try {
				doConnect();
			} catch (Exception e) {
				log.error("resister connect Exception", e);
			}
		}

	}

//	class KeepAlive extends TimerTask {
//
//		@Override
//		public void run() {
//			try {
//				sendKeepAlivePacket();
//			} catch (Exception e) {
//				log.error("resister connect Exception", e);
//			}
//		}
//
//	}

	class RegisterConnectSession extends TimerTask {

		@Override
		public void run() {
			try {
				registerConnectSession();
			} catch (Exception e) {
				log.error("resister connect Exception", e);
			}
		}

	}

	class RegisterReadSession extends TimerTask {

		@Override
		public void run() {
			try {
				registerReadSession();
			} catch (Exception e) {
				log.error("resister connect Exception", e);
			}
		}

	}

	synchronized public void stopThread() {
		this._run = false;
		this.notifyAll();
		this.clean();
		timer.cancel();
	}

	public void clean() {
		try {
			synchronized (ss_lock) {
				Iterator<SocketSession> it = socketSessionPool.iterator();

				while (it.hasNext()) {

					SocketSession ss = it.next();

					if(ss instanceof ReadSessionUdp){
						ReadSessionUdp sessionUdp = (ReadSessionUdp) ss;
						log.info("close socket:" + sessionUdp.get_dc().socket().getInetAddress().toString());
						sessionUdp.get_dc().socket().close();
						sessionUdp.get_dc().close();
						sessionUdp.key().cancel();
						it.remove();
					}else{
						log.info("close socket:" + ss.channel().socket().getInetAddress().toString());
						ss.channel().socket().close();
						ss.channel().close();

						ss.key().cancel();

						it.remove();
					}
				}

			}
		} catch (IOException e) {
			log.error("sockect channel close exception:", e);
		}

		log.info("all sockets are closed......");
	}

	public void initGwMap(EthGate gw) {
		synchronized (gwMap) {
			if (gwMap.get(gw.getNeId()) == null) {
				gwMap.put(gw.getNeId(), gw.getGateState());
			}
		}
	}

	public void gwStateTrap(EthGate gw) {

		if (this._run) {
			synchronized (gwMap) {
				if (gwMap.get(gw.getNeId()) != gw.getGateState()) {
					gwMap.put(gw.getNeId(), gw.getGateState());
					gwStateTrapToAlarm(gw);
				}
			}
		}
	}

	public void deleteGwMap(EthGate gw) {

		if (this._run) {
			synchronized (gwMap) {
				gwMap.remove(gw.getNeId());
			}
		}
	}

	public void gwStateTrapToAlarm(EthGate gw) {

		ResponseCollection rc = new ResponseCollection();
		rc.setCmdsType(CmdsType.CT_TRAP);

		Response res = new Response();
		res.setcmdId("NMAlarmAutoReport");
		res.setResult(0);

		CommandDestination dest = new CommandDestination();
		dest.setNeId(gw.getNeId());
		res.setDest(dest);

		CommandParameter cp = new CommandParameter();
		res.addParam(cp);
		rc.addResponse(res);

		if (gw.getGateState() == GateState.CONNECTED) {
			// 上报连接
			log.info("********* ne: " + ToolUtil.longToIp(gw.getIpAddress()) + " is connected! *********");

			cp.addValue("alarmName", "GNE_CONNECT_FAIL");
			cp.addValue("neId", String.valueOf(gw.getNeId()));
			cp.addValue("alarmStateByTime", "0");

			Date date = new Date();
			long time = date.getTime() / 1000;
			cp.addValue("endTime", String.valueOf(time));

		} else {
			// 上报失连
			log.info("********* ne: " + ToolUtil.longToIp(gw.getIpAddress()) + " can't be connected! *********");

			cp.addValue("alarmName", "GNE_CONNECT_FAIL");
			cp.addValue("neId", String.valueOf(gw.getNeId()));
			cp.addValue("alarmStateByTime", "1");

			Date date = new Date();
			long time = date.getTime() / 1000;
			cp.addValue("occurTime", String.valueOf(time));
		}

		SyncNotifyManager.getInstance().notifyToAlarm(rc);

		rc.clear();
		rc = null;
	}
}
