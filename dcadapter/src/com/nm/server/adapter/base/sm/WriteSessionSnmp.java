package com.nm.server.adapter.base.sm;




import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.nm.base.util.DefaultThreadFactory;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.service.EthGateManager;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.comm.messagesvr.MessageSvr;
import com.nm.server.common.util.ToolUtil;
import com.nm.server.config.trap.TrapManager;


public class WriteSessionSnmp extends MessageSvr<WriteSessionTask>
{
	
	protected ScheduledThreadPoolExecutor schedule;
	protected ScheduledFuture<?> future;
	protected int period;
	
	private Map<Integer,List<AdapterMessagePair>> mapGwMsgPairs = 
		new HashMap<Integer,List<AdapterMessagePair>>();
	
	
	protected EthGateManager ethGateManager;
	protected TopoManager topoManager;
	protected TrapManager trapManager;
	protected SocketMgrServer socketMgr;
	
	protected Map<Integer,ManagedElement> neMap;
	
	protected byte[] lockGwMp = new byte[0];
	
	public WriteSessionSnmp()
	{
		
	}
	
	public WriteSessionSnmp(ClientMessageListener msgListener,TopoManager topoManager,
			EthGateManager ethGateManager,SocketMgrServer socketMgr)
	{
		this.setMsgListener(msgListener);
		this.setTopoManager(topoManager);
		this.setEthGateManager(ethGateManager);
		this.setSocketMgr(socketMgr);	
//		initSnmp();
	}	

	
	public void setEthGateManager(EthGateManager ethGateManager)
	{
		this.ethGateManager = ethGateManager;
	}

	public EthGateManager getEthGateManager()
	{
		return ethGateManager;
	}

	public void setTopoManager(TopoManager topoManager)
	{
		this.topoManager = topoManager;
	}


	public TopoManager getTopoManager()
	{
		return topoManager;
	}

	public void setTrapManager(TrapManager trapManager)
	{
		this.trapManager = trapManager;
		initSnmp();
	}


	public TrapManager getTrapManager()
	{
		return trapManager;
	}

	public void setSocketMgr(SocketMgrServer socketMgr)
	{
		this.socketMgr = socketMgr;
	}



	public SocketMgrServer getSocketMgr()
	{
		return socketMgr;
	}
	
	/**
	 * @param neMap the neMap to set
	 */
	public void setNeMap(Map<Integer,ManagedElement> neMap)
	{
		this.neMap = neMap;
	}
	
	protected int getEthGwId(ManagedElement me) {
		int ethId = 0;
		
		if(!me.isServeAsEthGateway()) {
			if(me.getEthGatewayInfo() != null) {
				ethId = me.getEthGatewayInfo().getWorkGatewayNeId();
			} else if(me.getMonitorProxyInfo() != null) {
				ethId = this.getGwNeId(me.getMonitorProxyInfo().getProxyNeId());
			} else {
				log.error("------ ne: " + me.getName() + " do not have a gateway info.------");
			}			
		} else {
			ethId = me.getId();
		}
		
		return ethId;
	}
	
	private int getGwNeId(int proxyNeId) {
		if(proxyNeId > 0) {
			synchronized(this.neMap) {
				ManagedElement me = this.neMap.get(proxyNeId);
				
				if(me == null) {
					log.error("$$$$$$db does not  contain the ne which id is " + proxyNeId + "$$$$$$");
					return 0;
				}
				
				if(me.isServeAsEthGateway()) {
					return me.getId();
				} else if(me.getEthGatewayInfo() != null) {
					return me.getEthGatewayInfo().getWorkGatewayNeId();
				} else if(me.getMonitorProxyInfo() != null) {
					return this.getGwNeId(me.getMonitorProxyInfo().getProxyNeId());
				}
			}
		}
		return 0;
	}	
	
	protected int getSendInterval() {
		return ToolUtil.getEntryConfig().getSendFrameInterval();
	}

	protected void initThreadPool() {
		threadPool = new ThreadPoolExecutor(3, 30, 60, 
				TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1), 
				new ThreadPoolExecutor.CallerRunsPolicy());	
		threadPool.setThreadFactory(new DefaultThreadFactory(this.getName()));
	}
	
	protected void initSchedule() {
		
		period = this.getSendInterval();
		schedule = new ScheduledThreadPoolExecutor(1);
		future = schedule.scheduleAtFixedRate(new SendMessageSchedule(), 0, period, TimeUnit.MILLISECONDS);
	}
	
	public void init()	{
		this.initThreadPool();
		this.initSchedule();
	}
	
	public void initSnmp(){
		MessagePool msgPool = this.msgListener.getMsgPool();
		try {
		//	WriteSessionTaskSnmp.getSnmpComm();
//			WriteSessionTaskSnmp.initSnmpTrap(topoManager, trapManager, msgPool,neMap);
			
			WriteSessionTaskSnmpNew.initSnmpTrap(topoManager, trapManager, msgPool,neMap,appContext);
		} catch (IOException e) {
			log.error("init snmp error !!", e);
		}
	}
	
	public void processMessage(MessagePool msgPool) throws Exception
	{
		AdapterMessagePair mp = (AdapterMessagePair)msgPool.getUnSendMsgSnmp();
		
		if(mp.getProtocol() == CommProtocol.CP_SNMP)	{
			ManagedElement me = mp.getManagedElement();
			int neId = me != null ? me.getId() : -10000;
			this.addGwMsgPair(neId, mp);
			log.info("$$$$$$$$$$$$$$$$$$$$$$$$$----process a Snmp MessagePair----$$$$$$$$$$$$$$$$$$$$$$$$$$");
		} else if(mp.getProtocol() == CommProtocol.CP_SNMP_NEW)	{
			ManagedElement me = mp.getManagedElement();
			int neId = me != null ? me.getId() : -10000;
			this.addGwMsgPair(neId, mp);
			log.info("$$$$$$$$$$$$$$$$$$$$$$$$$----process a SnmpNew MessagePair----$$$$$$$$$$$$$$$$$$$$$$$$$$");
		} else if(mp.getProtocol() == CommProtocol.CP_TABS){
			
		}
	}
	
	protected void sendMessage(AdapterMessagePair mp) {
		if(mp.getProtocol() == CommProtocol.CP_SNMP)
		{
			//log.info("$$$$$$$$$$$$$$$$$$$$$$$$$----Send a Snmp MessagePair----$$$$$$$$$$$$$$$$$$$$$$$$$$");
			WriteSessionTaskSnmp writeTaskSnmp = new WriteSessionTaskSnmp(
					this.msgListener.getMsgPool(),mp);
			writeTaskSnmp.setTopoManager(topoManager);
			writeTaskSnmp.setNeMap(neMap);
			this.threadPool.execute(writeTaskSnmp);
		}
		else if(mp.getProtocol() == CommProtocol.CP_SNMP_NEW)
		{
			//log.info("$$$$$$$----Send a SnmpNew MessagePair----$$$$$$"+ mp.getAsynTaskList().getTask(0).getCommand().getId());
			WriteSessionTaskSnmpNew writeTaskSnmpNew = new WriteSessionTaskSnmpNew(
					this.msgListener.getMsgPool(),mp);
//			writeTaskSnmpNew.setTopoManager(topoManager);
			writeTaskSnmpNew.setNeMap(neMap);
			this.threadPool.execute(writeTaskSnmpNew);
		}
		else if(mp.getProtocol() == CommProtocol.CP_TABS){
			
		}
	}
	
	public void addGwMsgPair(int gwId, AdapterMessagePair mp) {
		synchronized(this.lockGwMp) {
			
			int snmpConcurrentSnmp = this.getSnmpConcurrentNum();
			List<AdapterMessagePair> mpList = null;
			
			if(snmpConcurrentSnmp == 0) {
				mpList = this.mapGwMsgPairs.get(gwId);
				
				if(mpList == null) {
					mpList = new ArrayList<AdapterMessagePair>();
					mpList.add(mp);
					this.mapGwMsgPairs.put(gwId, mpList);
				}
				
			} else {
				
				int key = mp.getGwId();
				mpList = this.mapGwMsgPairs.get(key);
				if(mpList == null) {
					mpList = new ArrayList<AdapterMessagePair>();
					mpList.add(mp);
					this.mapGwMsgPairs.put(key, mpList);
				}else{
					mpList.add(mp);
					this.mapGwMsgPairs.put(key, mpList);
				}
				
			}
			
		}
	}	
	
//	public void addGwMsgPair(int gwId, AdapterMessagePair mp) {
//		synchronized(this.lockGwMp) {
//			
//			int snmpConcurrentSnmp = this.getSnmpConcurrentNum();
//			List<AdapterMessagePair> mpList = null;
//			
//			if(snmpConcurrentSnmp == 0) {
//				mpList = this.mapGwMsgPairs.get(gwId);
//				
//				if(mpList == null) {
//					mpList = new ArrayList<AdapterMessagePair>();
//					mpList.add(mp);
//					this.mapGwMsgPairs.put(gwId, mpList);
//				}
//				
//			} else if(snmpConcurrentSnmp < this.mapGwMsgPairs.size()){
//				
//				mpList = new ArrayList<AdapterMessagePair>();
//				mpList.add(mp);
//				this.mapGwMsgPairs.put(this.mapGwMsgPairs.size() + 1, mpList);
//				
//			} else {
//				
//				int minNum = snmpConcurrentSnmp;
//				int index = 0;
//				
////				for(int i =0; i < this.mapGwMsgPairs.size(); i++) {
////					if(this.mapGwMsgPairs.get(i).size() < minNum) {
////						minNum = this.mapGwMsgPairs.get(i).size();
////						index = i;
////					}
////				}
////				mpList = this.mapGwMsgPairs.get(index);
//				mpList = new ArrayList<AdapterMessagePair>();
//				mpList.add(mp);
//				this.mapGwMsgPairs.put(this.mapGwMsgPairs.size() + 1, mpList);
//			}
//			
//		}
//	}	

	private int getSnmpConcurrentNum() {
		String snmpConcurrentNum = ToolUtil.getProperty("PING_CONCURRENT_NUM_SNMP");
		
		if(snmpConcurrentNum != null) {
			return Integer.valueOf(snmpConcurrentNum);
		}
		
		return 0;
	}
	
	class SendMessageSchedule implements Runnable {

		@Override
		public void run() {
			try	{		
				synchronized(lockGwMp) {
					Iterator<Integer> it = mapGwMsgPairs.keySet().iterator();
					
					while(it.hasNext()) {
						int key = it.next();
						
						List<AdapterMessagePair> mpList = mapGwMsgPairs.get(key);
						
						if(mpList != null && !mpList.isEmpty()) {
							
							/*timeout first*/
							List<AdapterMessagePair> tmpList = this.getTimeOutMpList(mpList);
							
							if(!tmpList.isEmpty()) {
								sendMessage(tmpList.get(0));
								mpList.remove(tmpList.get(0));
								tmpList.clear();
							}else {
								
								/*normal commands first*/
								tmpList = this.getNormalMpList(mpList);
								
								if(!tmpList.isEmpty()) {
									sendMessage(tmpList.get(0));
									mpList.remove(tmpList.get(0));
									tmpList.clear();
								} else {
									Iterator<AdapterMessagePair> mpIt = mpList.iterator();
									while(mpIt.hasNext()) {
										
										AdapterMessagePair mp = mpIt.next();
										sendMessage(mp);
										
										mpIt.remove();
										break;
									}
								}
							}

						}else {
							if(mpList != null) {
								mpList = null;
							}
							
							it.remove();
						}
					}
				}
				
				if(period != getSendInterval()) {
					period = getSendInterval();
					future.cancel(false);		
					
					if(future.isDone()) {
						schedule.scheduleAtFixedRate(new SendMessageSchedule(), getSendInterval(), getSendInterval(), TimeUnit.MILLISECONDS);
					}
				}
				
		} catch (Exception e) {
				log.error("SendFrameTask Error:", e);
			}
		}
		
		private List<AdapterMessagePair> getTimeOutMpList(List<AdapterMessagePair> mpList) {
			List<AdapterMessagePair> tmpList = new ArrayList<AdapterMessagePair>();
			
			if(mpList == null || mpList.isEmpty()) {
				return tmpList;
			}
			
			for(AdapterMessagePair mp : mpList) {
				if(mp.isReTransmitMessagePair()) {
					tmpList.add(mp);
				}
			}
			
			return tmpList;
		}
		
		private List<AdapterMessagePair> getNormalMpList(List<AdapterMessagePair> mpList) {
			List<AdapterMessagePair> tmpList = new ArrayList<AdapterMessagePair>();
			
			if(mpList == null || mpList.isEmpty()) {
				return tmpList;
			}
			
			for(AdapterMessagePair mp : mpList) {
				if(mp.getScheduleType() == ScheduleType.ST_NA) {
					tmpList.add(mp);
				}
			}
			
			return tmpList;
		}
	}
	

}
