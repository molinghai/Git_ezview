package com.nm.server.adapter.base.sm;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.EthGate.GateState;
import com.nm.server.common.util.ToolUtil;


public class ConnectSocketTask  implements Runnable{

	protected static final Log log = LogFactory.getLog(ConnectSocketTask.class);
	
	private EthGate gw;
	private SocketMgrServer sm;	
	
	public ConnectSocketTask(SocketMgrServer sm,EthGate gw) {
		this.sm = sm;
		this.gw = gw;
	}
	
	public void setGw(EthGate gw) {
		this.gw = gw;
	}

	public EthGate getGw() {
		return gw;
	}

	public void setSm(SocketMgrServer sm) {
		this.sm = sm;
	}

	public SocketMgrServer getSm() {
		return sm;
	}

	@Override
	public void run() {
		addConnectSession();
	}
	
	public void addConnectSession()
	{
		String strIp = ToolUtil.longToIp(gw.getIpAddress());
		
		try
		{
			int port = gw.getPort();
			
			
			SocketChannel sc = null;

//			String strNic = ToolUtil.getHostIp(strIp);
			sc = SocketChannel.open();
			Socket s = sc.socket();
			//s.setSoTimeout(0);
			s.setKeepAlive(true);
			s.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0));
			log.info("------- connecting gateway: " 
					+ strIp + ":" + port 
					+ " host = " + sc.socket().getLocalAddress().getHostAddress() + " ------");

			s.connect(new InetSocketAddress(InetAddress.getByName(strIp), port),1);	
			sc.configureBlocking(false);
			this.sm.addReadSession(sc,this.getGw());
			
			gw.setGateState(GateState.CONNECTED);
			
		}
		catch(ClosedChannelException e)
		{
			log.error("add connectSession exception:", e);
		}
		catch(Exception e)
		{			
			gw.setGateState(GateState.DELETE);
			log.info("connect to "+ strIp + " timeout !");
		}
	}	
	
}
