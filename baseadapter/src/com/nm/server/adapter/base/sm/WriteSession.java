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
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.AsynTaskList;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.syslog.SyslogTrap;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.comm.messagesvr.MessageSvr;
import com.nm.server.common.util.ToolUtil;
import com.nm.server.config.trap.TrapManager;


public class WriteSession extends MessageSvr<WriteSessionTask>
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
	protected SerialPortMgrServer serialPortMgr;
	
	protected Map<Integer,ManagedElement> neMap;
	
	protected byte[] lockGwMp = new byte[0];
	
	public WriteSession()
	{
		
	}
	
	public WriteSession(ClientMessageListener msgListener,TopoManager topoManager,
			EthGateManager ethGateManager,SocketMgrServer socketMgr)
	{
		this.setMsgListener(msgListener);
		this.setTopoManager(topoManager);
		this.setEthGateManager(ethGateManager);
		this.setSocketMgr(socketMgr);	
//		initSnmp();
	}	

	
	public void setSerialPortMgr(SerialPortMgrServer serialPortMgr) {
		this.serialPortMgr = serialPortMgr;
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
			//WriteSessionTaskSnmp.getSnmpComm();
			WriteSessionTaskSnmp.initSnmpTrap(topoManager, trapManager, msgPool,neMap);
			
			WriteSessionTaskSnmpNew.initSnmpTrap(topoManager, trapManager, msgPool,neMap, appContext);
			
//			SyslogTrap st = new SyslogTrap();
//			st.setMsgPool(msgPool);
//			st.setTopoManager(topoManager);
//			st.start();
		} catch (IOException e) {
			log.error("init snmp error !!", e);
		}
	}
	
	public void processMessage(MessagePool msgPool) throws Exception
	{
		AdapterMessagePair mp = (AdapterMessagePair)msgPool.getUnSendMsg();
		
		if(mp.getProtocol() == CommProtocol.CP_MSDH) {
			this.addGwMsgPair(this.getEthGwId(mp.getManagedElement()), mp);
		} else if(mp.getProtocol() == CommProtocol.CP_SNMP)	{
			ManagedElement me = mp.getManagedElement();
			int neId = me != null ? me.getId() : -10000;
			this.addGwMsgPair(neId, mp);
		} else if(mp.getProtocol() == CommProtocol.CP_SNMP_NEW)	{
			ManagedElement me = mp.getManagedElement();
			int neId = me != null ? me.getId() : -10000;
			this.addGwMsgPair(neId, mp);
		} else if(mp.getProtocol() == CommProtocol.CP_TABS){
			this.addGwMsgPair(mp.getManagedElement().getId(), mp);
		} else if(mp.getProtocol() == CommProtocol.CP_OLP) {
			this.addGwMsgPair(this.getEthGwId(mp.getManagedElement()), mp);
		} else if(mp.getProtocol() == CommProtocol.CP_UDP) {
			this.addGwMsgPair(this.getEthGwId(mp.getManagedElement()), mp);
		}
	}
	
	protected void sendMessage(AdapterMessagePair mp) {
		Map<String, Object> ptMap = new HashMap<String, Object>();
		checkProtocol(mp, ptMap);
		log.info("----WriteSeesion-sendMessage----" + mp.getScheduleType());
		if(ptMap.size() > 1 && !mp.isSendAgin()){
			if (ptMap.containsKey(CommProtocol.CP_MSDH.name())) {
				WriteSessionTask writeTask = new WriteSessionTask(
						this.msgListener.getMsgPool(), mp);
				writeTask.setTopoManager(topoManager);
				writeTask.setSocketMgr(socketMgr);
				writeTask.setEthGateManager(ethGateManager);
				writeTask.setNeMap(neMap);

				this.threadPool.execute(writeTask);
			}
			if (ptMap.containsKey(CommProtocol.CP_SNMP.name())) {
				WriteSessionTaskSnmp writeTaskSnmp = new WriteSessionTaskSnmp(
						this.msgListener.getMsgPool(), mp);
				writeTaskSnmp.setTopoManager(topoManager);
				writeTaskSnmp.setNeMap(neMap);
				this.threadPool.execute(writeTaskSnmp);
			}
			if (ptMap.containsKey(CommProtocol.CP_SNMP_NEW.name())) {
				WriteSessionTaskSnmpNew writeTaskSnmpNew = new WriteSessionTaskSnmpNew(
						this.msgListener.getMsgPool(), mp);
				writeTaskSnmpNew.setAppContext(this.appContext);
				writeTaskSnmpNew.setNeMap(neMap);
				this.threadPool.execute(writeTaskSnmpNew);
			} 
		}else{
			if(mp.getProtocol() == CommProtocol.CP_MSDH)
			{
				WriteSessionTask writeTask = new WriteSessionTask(
						this.msgListener.getMsgPool(),mp);
				writeTask.setTopoManager(topoManager);
				writeTask.setSocketMgr(socketMgr);
				writeTask.setEthGateManager(ethGateManager);
				writeTask.setNeMap(neMap);
				
				this.threadPool.execute(writeTask);
			}
			else if(mp.getProtocol() == CommProtocol.CP_SNMP )
			{
				WriteSessionTaskSnmp writeTaskSnmp = new WriteSessionTaskSnmp(
						this.msgListener.getMsgPool(),mp);
				writeTaskSnmp.setTopoManager(topoManager);
				writeTaskSnmp.setNeMap(neMap);
				this.threadPool.execute(writeTaskSnmp);
			}
			else if(mp.getProtocol() == CommProtocol.CP_SNMP_NEW)
			{
				WriteSessionTaskSnmpNew writeTaskSnmpNew = new WriteSessionTaskSnmpNew(
						this.msgListener.getMsgPool(),mp);
//				writeTaskSnmpNew.setTopoManager(topoManager);
				writeTaskSnmpNew.setNeMap(neMap);
				writeTaskSnmpNew.setAppContext(this.appContext);
				this.threadPool.execute(writeTaskSnmpNew);
			}
			else if(mp.getProtocol() == CommProtocol.CP_TABS){
			WriteSessionTaskTabs writeTask = new WriteSessionTaskTabs(
					this.msgListener.getMsgPool(),mp);
			writeTask.setTopoManager(topoManager);
			writeTask.setSerialPortMgr(serialPortMgr);
			writeTask.setNeMap(neMap);
			
			this.threadPool.execute(writeTask);
		} else if(mp.getProtocol() == CommProtocol.CP_OLP) {
			WriteSessionTaskOlp writeTask = new WriteSessionTaskOlp(
					this.msgListener.getMsgPool(),mp);
			writeTask.setTopoManager(topoManager);
			writeTask.setSocketMgr(socketMgr);
			writeTask.setEthGateManager(ethGateManager);
			writeTask.setNeMap(neMap);
			this.threadPool.execute(writeTask);
			}else if(mp.getProtocol() == CommProtocol.CP_UDP) {
				WriteSessionTaskUdp writeTask = new WriteSessionTaskUdp(
						this.msgListener.getMsgPool(),mp);
				writeTask.setTopoManager(topoManager);
				writeTask.setSocketMgr(socketMgr);
				writeTask.setEthGateManager(ethGateManager);
				writeTask.setNeMap(neMap);
				this.threadPool.execute(writeTask);
				}
		}
	}
	
	/**
	 *检测多协议
	 * */
	private void checkProtocol(AdapterMessagePair mp, Map<String, Object> ptMap) {
		AsynTaskList asynTaskList = mp.getAsynTaskList();
		if(asynTaskList == null || asynTaskList.getTaskList() == null){
			return;
		}
		Iterator<AsynTask> itAt = asynTaskList.getTaskList().iterator();
		while (itAt.hasNext()) {
			AsynTask asynTask = itAt.next();
			List<TaskCell> tcs = asynTask.getTaskCellList();
			Iterator<TaskCell> itTc = tcs.iterator();
			while (itTc.hasNext()) {
				TaskCell tc = itTc.next();
				if(CommProtocol.CP_MSDH.equals(tc.getProtocolType())){
					ptMap.put(CommProtocol.CP_MSDH.name(), true);
				}else if(CommProtocol.CP_CLI.equals(tc.getProtocolType())
						|| CommProtocol.CP_SNMP.equals(tc.getProtocolType())
						|| CommProtocol.CP_SNMP_NEW.equals(tc.getProtocolType())
						|| CommProtocol.CP_SNMP_II.equals(tc.getProtocolType())){
					ptMap.put(CommProtocol.CP_SNMP_NEW.name(), true);
				}else if(CommProtocol.CP_SNMP_I.equals(tc.getProtocolType())){
					ptMap.put(CommProtocol.CP_SNMP.name(), true);
				}
				if(ptMap.size() >= 2){
					break;
				}
			}
			if(ptMap.size() >= 2){
				break;
			}
		}
	}

	public void addGwMsgPair(int gwId, AdapterMessagePair mp) {
		synchronized(this.lockGwMp) {
			List<AdapterMessagePair> mpList = this.mapGwMsgPairs.get(gwId);
			
			if(mpList == null) {
				mpList = new ArrayList<AdapterMessagePair>();
				this.mapGwMsgPairs.put(gwId, mpList);
			}
			
			mpList.add(mp);
		}
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
								tmpList = null;
							}else {
								
								/*normal commands first*/
								tmpList = this.getNormalMpList(mpList);
								
								
								if(!tmpList.isEmpty()) {
									sendMessage(tmpList.get(0));
									mpList.remove(tmpList.get(0));
									tmpList.clear();
									tmpList = null;
								} else {
									tmpList = this.getGWPollMpList(mpList);
									if (!tmpList.isEmpty()) {
										Iterator<AdapterMessagePair> tmpIt = tmpList.iterator();
										while (tmpIt.hasNext()) {

											AdapterMessagePair mp = tmpIt.next();
											sendMessage(mp);
											mpList.remove(mp);
										}
										tmpList.clear();
										tmpList = null;
									} else{
										tmpList = this.getNeAndAlarmPollMpList(mpList);
										if (!tmpList.isEmpty()) {
											sendMessage(tmpList.get(0));
											mpList.remove(tmpList.get(0));
											tmpList.clear();
											tmpList = null;
										} else {
											Iterator<AdapterMessagePair> mpIt = mpList.iterator();
											while (mpIt.hasNext()) {

												AdapterMessagePair mp = mpIt.next();
												sendMessage(mp);
												mpIt.remove();
												break;
											}
										}
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
		
		private List<AdapterMessagePair> getGWPollMpList(
				List<AdapterMessagePair> mpList) {
			List<AdapterMessagePair> tmpList = new ArrayList<AdapterMessagePair>();
			
			if(mpList == null || mpList.isEmpty()) {
				return tmpList;
			}
			
			for(AdapterMessagePair mp : mpList) {
				if(mp.getScheduleType() == ScheduleType.ST_GWPOLL) {
					tmpList.add(mp);
				}
			}
			
			return tmpList;
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
		
		private List<AdapterMessagePair> getNeAndAlarmPollMpList(List<AdapterMessagePair> mpList) {
			List<AdapterMessagePair> tmpList = new ArrayList<AdapterMessagePair>();
			
			if(mpList == null || mpList.isEmpty()) {
				return tmpList;
			}
			
			for(AdapterMessagePair mp : mpList) {
				if(mp.getScheduleType() == ScheduleType.ST_ALARMPOLL
						||mp.getScheduleType() == ScheduleType.ST_NEPOLL
						||mp.getScheduleType() == ScheduleType.ST_GWPOLL
						||mp.getScheduleType() == ScheduleType.ST_HISALARMPOLL) {
					tmpList.add(mp);
				}
			}
			
			return tmpList;
		}
	}
	

}
