package com.nm.server.adapter.base.sm;


import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.nm.base.util.DefaultThreadFactory;
import com.nm.nmm.service.EthGateManager;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.MessagePool;
import com.nm.server.common.util.ToolUtil;

public class WritePollSession extends WriteSession {
	
	public WritePollSession(){
		
	}
	
	public WritePollSession(ClientMessageListener msgListener,
			TopoManager topoManager,
			EthGateManager ethGateManager,
			SocketMgrServer socketMgr)	{
		this.setMsgListener(msgListener);
		this.setTopoManager(topoManager);
		this.setEthGateManager(ethGateManager);
		this.setSocketMgr(socketMgr);	
	}	
	
	protected int getSendInterval() {
		int interval = ToolUtil.getEntryConfig().getSendPollFrameInterval_msdh();
		return interval;
	}

	public void initThreadPool() {
		threadPool = new ThreadPoolExecutor(10, 50, 60, 
				TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1), 
				new ThreadPoolExecutor.CallerRunsPolicy());	
		threadPool.setThreadFactory(new DefaultThreadFactory(this.getName()));
	}	
	
	public void processMessage(MessagePool msgPool) throws Exception {
		
		AdapterMessagePair mp = (AdapterMessagePair)msgPool.getScheduledUnSendMsg();
		
		if(ToolUtil.getEntryConfig().getPingSerial() == 1) {
			this.addGwMsgPair(0, mp);
		} else {
			if(mp.getProtocol() == CommProtocol.CP_MSDH) {
				this.addGwMsgPair(this.getEthGwId(mp.getManagedElement()), mp);
			} else if(mp.getProtocol() == CommProtocol.CP_SNMP)	{
				this.addGwMsgPair(mp.getManagedElement().getId(), mp);
			} else if(mp.getProtocol() == CommProtocol.CP_SNMP_NEW)	{
				this.addGwMsgPair(mp.getManagedElement().getId(), mp);
			} else if(mp.getProtocol() == CommProtocol.CP_TABS){
				this.addGwMsgPair(mp.getManagedElement().getId(), mp);
			} else if(mp.getProtocol() == CommProtocol.CP_OLP) {
				this.addGwMsgPair(this.getEthGwId(mp.getManagedElement()), mp);
			} else if(mp.getProtocol() == CommProtocol.CP_UDP) {
				this.addGwMsgPair(this.getEthGwId(mp.getManagedElement()), mp);
			}
		}
		

		
	}

}
