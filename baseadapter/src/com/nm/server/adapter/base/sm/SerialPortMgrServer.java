package com.nm.server.adapter.base.sm;

import gnu.io.NoSuchPortException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.SerialConnectionInfo;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.config.serialport.SerialPortInfoManager;

public class SerialPortMgrServer extends Thread {
	private static final Log log = LogFactory.getLog(SerialPortMgrServer.class);

	// private byte[] ss_gate_lock = new byte[0]; // conSelector and scs share
	// one lock.
	private byte[] sp_lock = new byte[0];
	protected Collection<SerialConnection> connPool = null;
	private volatile boolean _run = true;

	private ClientMessageListener msgListener;
	private SerialPortInfoManager spInfoManager= null;
	private List<SerialConnectionInfo> serialPortInfos = new ArrayList<SerialConnectionInfo>();

	private String adapterName;

	private Timer timer;


	private Map<Integer, ManagedElement> neMap;

	public SerialPortMgrServer() {
		this.connPool = new HashSet<SerialConnection>();
	}

	public void setSpInfoManager(SerialPortInfoManager spInfoManager) {
		this.spInfoManager = spInfoManager;
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

	@Override
	public void run() {
		try {
			this.getSerialPortInfos();
			timer = new Timer("SerialPortMgrServer-RegisterConnect-timer");
			timer.scheduleAtFixedRate(new RegisterConnect(), 0, 10000);

			Thread.sleep(10000);

			while (_run) {
				try {
					if(connPool != null && !connPool.isEmpty()){
						synchronized (connPool) {
							for(SerialConnection conn : connPool){
								conn.reply();
							}
						}
					}
					Thread.sleep(10);
				} catch (Exception e) {
					log.error("SerialPort Manager server run exception", e);
				}

			}

			this.clean();
		} catch (Exception e) {
			log.error("SerialPort Manager server run exception", e);
		}

	}


	private void getSerialPortInfos() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("adapterName", this.adapterName);
		serialPortInfos = this.spInfoManager.getByParam(params);
	}

	public void doConnect() {
		try {
			log.info("--------doConnect()-------------");

			synchronized (sp_lock) {

				for (SerialConnectionInfo portInfo : serialPortInfos) {
					boolean isOpenFlag = false;
//					CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(portInfo
//							.getPortName());
					Iterator<SerialConnection> it =  connPool.iterator();
					while(it.hasNext()){
						SerialConnection conn = it.next();
						if(portInfo.equals(conn.getParameters())){
							if(!conn.isOpen()){
								conn.setMsgListener(msgListener);
								try{
									conn.openConnection();
								}catch (NoSuchPortException e) {
									this.connPool.remove(conn);
									this.spInfoManager.delete(portInfo);
									isOpenFlag = true;
								}
							}
							isOpenFlag = true;
							break;
						}
					}
					if(!isOpenFlag){
						
						SerialConnection conn = new SerialConnection(portInfo);
						try{
							conn.openConnection();
						}catch (NoSuchPortException e) {
							this.connPool.remove(conn);
							this.spInfoManager.delete(portInfo);
						}
						conn.setMsgListener(msgListener);
						connPool.add(conn);
					}
				}
			}

		} catch (Exception e) {
			log.error("doConnect Exception", e);
			
		}

	}

	public SerialConnection getSerialPortConnection() {

		synchronized (this.sp_lock) {
			Iterator<SerialConnection> it = connPool.iterator();

			while (it.hasNext())

			{
				SerialConnection conn = it.next();
				if(conn.isOpen()){
					return conn;
				}
			}
		}

		return null;
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


	synchronized public void stopThread() {
		this._run = false;
		this.notifyAll();
		this.clean();
		timer.cancel();
	}

	public void clean() {
		try {
			synchronized (sp_lock) {
				Iterator<SerialConnection> it = connPool.iterator();

				while (it.hasNext()) {
					SerialConnection conn = it.next();
					log.info("close SerialPort:" + conn.getPortName());
					conn.closeConnection();
					it.remove();
				}

			}
		} catch (Exception e) {
			log.error("SerialPort close exception:", e);
		}

		log.info("all SerialPort are closed.");
	}

	public void openSerialConnect(SerialConnectionInfo portInfo) {
		Iterator<SerialConnectionInfo> it = serialPortInfos.iterator();
		boolean exist = false;
		while(it.hasNext()){
			SerialConnectionInfo p = it.next();
			if(portInfo.equals(p)){
				exist = true;
				break;
			}
		}
		if(!exist){
			serialPortInfos.add(portInfo);
		}
		this.doConnect();
		portInfo.setConnectState(true);
		this.spInfoManager.update(portInfo);
	}

	public void closeSerialConnect(SerialConnectionInfo portInfo) {
		Iterator<SerialConnectionInfo> it = serialPortInfos.iterator();
		while(it.hasNext()){
			SerialConnectionInfo p = it.next();
			if(p.equals(portInfo)){
				synchronized (connPool) {
					Iterator<SerialConnection> itC =  connPool.iterator();
					while(itC.hasNext()){
						SerialConnection conn = itC.next();
						if(portInfo.equals(conn.getParameters())){
							if(conn.isOpen()){
								conn.closeConnection();
							}
							itC.remove();
							break;
						}
					}
					it.remove();
					break;
				}
			}
		}
		doConnect();
	}

}
