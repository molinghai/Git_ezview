package com.nm.server.adapter.base.sm;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;

import com.nm.error.ErrorMessageTable;
import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.CsCommandCollection;
import com.nm.message.CsCommandCollection.CmdsEndType;
import com.nm.message.Response;
import com.nm.message.ResponseCollection;
import com.nm.message.progress.SynProgress;
import com.nm.message.progress.SynProgress.Outline;
import com.nm.nmm.res.Address.AddressType;
import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.MonitorLinkType;
import com.nm.nmm.service.EthGateManager;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.comm.messagesvr.MessageTask;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class WriteSessionTask extends MessageTask {
	
	private EthGateManager ethGateManager;
	protected TopoManager topoManager;
	protected SocketMgrServer socketMgr;
	private Map<Integer,ManagedElement> neMap;
	protected static byte[] ws_lock = new byte[0];
	
	public WriteSessionTask(MessagePool msgPool,Object o) {
		super(msgPool,o);
	}
	
	public void setEthGateManager(EthGateManager ethGateManager) {
		this.ethGateManager = ethGateManager;
	}

	public void setTopoManager(TopoManager topoManager)	{
		this.topoManager = topoManager;
	}

	public void setSocketMgr(SocketMgrServer socketMgr)	{
		this.socketMgr = socketMgr;
	}

	public void setNeMap(Map<Integer,ManagedElement> neMap)	{
		this.neMap = neMap;
	}
	
	protected boolean isRecordFrame(ScheduleType st)	{
		if(st == ScheduleType.ST_NA) {
			return ToolUtil.getEntryConfig().getFrameRecordOnOff() == 1;
		} else {
			return ToolUtil.getEntryConfig().getPollFrameRecordOnOff() == 1;
		}
	}
	
	protected String getRecordFileName(AdapterMessagePair mp) {
		
		if(mp.isSync()) {
			return "SyncCommand";
		} else {
			if(mp.getScheduleType() == ScheduleType.ST_NA){
				return "Command";
			}else if(mp.getScheduleType() == ScheduleType.ST_NEPOLL
					|| mp.getScheduleType() == ScheduleType.ST_GWPOLL){
				return "PollCommand";
			}else if(mp.getScheduleType() == ScheduleType.ST_ALARMPOLL||mp.getScheduleType() == ScheduleType.ST_HISALARMPOLL){
				return "AlarmPollCommand";
			}else{
				return "Command";
			}
		}
	}
	
	protected int getWriteFrameInterval() {
		return ToolUtil.getEntryConfig().getWriteFrameInterval();
	}
	
	protected int getManualSendFrameInterval() {
		return ToolUtil.getEntryConfig().getManualSendFrameInterval();
	}
	
	protected int getSyncSendMode() {
		return ToolUtil.getEntryConfig().getSyncSendMode();
	}
	
	protected int getManualSyncSendMode() {
		return ToolUtil.getEntryConfig().getManualSyncSendMode();
	}
	
	protected int getManualSingleFrameTimeOut() {
		return ToolUtil.getEntryConfig().getManualSingleFrameTimeout() * 1000;
	}
	
	protected int getEthGwId(ManagedElement me) {
		int ethId = 0;
		
//		if(!me.isServeAsEthGateway()) {
			if(me.getEthGatewayInfo() != null) {
				ethId = me.getEthGatewayInfo().getWorkGatewayNeId();
			} else if(me.getMonitorProxyInfo() != null) {
				ethId = this.getGwNeId(me.getMonitorProxyInfo().getProxyNeId());
			} else {
				log.error("------ ne: " + me.getName() + " do not have a gateway info.------");
			}			
//		} else {
//			ethId = me.getId();
//		}
		
		return ethId;
	}
	
	

	protected int getMainEthGwId(ManagedElement me) {
		int ethId = 0;

		if (me.getEthGatewayInfo() != null) {
			ethId = me.getEthGatewayInfo().getMainGatewayNeId();// me.getEthGatewayInfo().getStandby1NeId()
		} else if (me.getMonitorProxyInfo() != null) {
			ethId = this.getMainGwNeId(me.getMonitorProxyInfo().getProxyNeId());
		} else {
			log.error("------ ne: " + me.getName() + " do not have a gateway info.------");
		}

		return ethId;
	}

	protected int getStandby1EthGwId(ManagedElement me) {
		int ethId = 0;

		if (me.getEthGatewayInfo() != null) {
			ethId = me.getEthGatewayInfo().getStandby1NeId();// me.getEthGatewayInfo().getStandby1NeId()
		} else if (me.getMonitorProxyInfo() != null) {
			ethId = this.getStandby1GwNeId(me.getMonitorProxyInfo().getProxyNeId());
		} else {
			log.error("------ ne: " + me.getName() + " do not have a gateway info.------");
		}

		return ethId;
	}

	protected int getStandby2EthGwId(ManagedElement me) {
		int ethId = 0;

		String strIp = "";
		SocketChannel sc = null;
		if (me.getEthGatewayInfo() != null) {
			ethId = me.getEthGatewayInfo().getStandby2NeId();// me.getEthGatewayInfo().getStandby1NeId()
		} else if (me.getMonitorProxyInfo() != null) {
			ethId = this.getStandby2GwNeId(me.getMonitorProxyInfo().getProxyNeId());
		} else {
			log.error("------ ne: " + me.getName() + " do not have a gateway info.------");
		}

		return ethId;
	}
	
	protected SocketChannel getSocketChannel(ManagedElement me) throws Exception {
		int ethId = this.getEthGwId(me);
		String strIp = "";
		SocketChannel sc = null;
		EthGate ethGate = this.ethGateManager.getEthGate(ethId);
		
		if (ethGate == null) {
			// log.error("------ do not find a gateway related with ne: " +
			// me.getName() + "------");
			// return null;
		} else {
			strIp = ToolUtil.longToIp(ethGate.getIpAddress());
			// log.info("%%%%%%%%%%%%get socket begin %%%%%"+new
			// Date()+"%%%%%%%%%%%");
			sc = this.socketMgr.getSocketChannel(ethGate);
		}

		int iOnOff = ToolUtil.getEntryConfig().getPingPollSwitchGatewayOnOff();
		if (iOnOff == 1 && (ethGate == null || sc == null)) {// 工作网关socket为空，依次找主用网关、备用网关1、备用网关2的neid，设为工作网关
			ethId = findAviableEthGateway(me);
			if (ethId == -1 || ethId == 0 || me.getEthGatewayInfo()==null) {//没有可用的
				return null;
			}
			// 通知客户端，me的网关网元变化了
			me.getEthGatewayInfo().setWorkGatewayNeId(ethId);
			HashMap<String, String> paramMap = new HashMap<String, String>();
			paramMap.put("workGatewayNeId", String.valueOf(ethId));
			sendModifyNeGwInfoNotifyMessage(paramMap, me);
			ManagedElement meEth = topoManager.getManagedElementById(ethId);
			log.info("--------------NE Name："+me.getName()+", switch working gateway: " + meEth.getName()+ "--------------");

			EthGate ethGateNew = this.ethGateManager.getEthGate(ethId);
			if(ethGateNew != null){
				sc = this.socketMgr.getSocketChannel(ethGateNew);
			}
			//倒换后ping状态计数清零
			//state.setCount(0);
		} else {// 工作在备，恢复模式为恢复，主网关socket可用
//			int mainEthId = this.getMainEthGwId(me);
//			if (ethId != mainEthId && me.getEthGatewayInfo().isRevertive()) {// 工作在备,恢复模式为恢复
//				EthGate ethGateMain = this.ethGateManager.getEthGate(mainEthId);
//				if (ethGateMain != null) {
//					String strIpMain = ToolUtil.longToIp(ethGateMain.getIpAddress());
//					SocketChannel scMain = this.socketMgr.getSocketChannel(InetAddress.getByName(strIpMain));
//					if (scMain != null) {// 主网关socket可用
//						// 恢复
//						me.getEthGatewayInfo().setWorkGatewayNeId(mainEthId);
//						HashMap<String, String> paramMap = new HashMap<String, String>();
//						paramMap.put("workGatewayNeId", String.valueOf(mainEthId));
//						sendModifyNeGwInfoNotifyMessage(paramMap, me);
//						log.info("~~~~~~~~~~~~~~~~~~revertive workGatewayNeId:" + mainEthId + "，GateWay NE Name：" + me.getName()+"~~~~~~~~~~");
//					}
//				}
//			}
		}
		return sc;
		// log.info("%%%%%%%%%%%%get socket end  %%%%%"+new
		// Date()+"%%%%%%%%%%%");
	}
	
	protected DatagramChannel getDatagramChannel(ManagedElement me) throws Exception {
		int ethId = this.getEthGwId(me);
		DatagramChannel dc = null;
		EthGate ethGate = this.ethGateManager.getEthGate(ethId);
		if(ethGate!=null){
			dc = this.socketMgr.getDatagramChannel(ethGate);
		}
		return dc;
	}
	

	private int findAviableEthGateway(ManagedElement me) {
		int ethId = -1;
		SocketChannel sc = null;
		EthGate ethGate = null;
		ethId = this.getMainEthGwId(me);
		if (ethId != 0) {//先找主用
			ethGate = this.ethGateManager.getEthGate(ethId);
			if (ethGate != null) {
				sc = this.socketMgr.getSocketChannel(ethGate);
			}
		}
		if (sc == null) {
			ethId = this.getStandby1EthGwId(me);
			if (ethId != 0) {//再找备用1
				ethGate = this.ethGateManager.getEthGate(ethId);
				if (ethGate != null) {
					sc = this.socketMgr.getSocketChannel(ethGate);
				}
			}
		}
		if (sc == null) {
			ethId = this.getStandby2EthGwId(me);
			if (ethId != 0) {//再找备用2
				ethGate = this.ethGateManager.getEthGate(ethId);
				if (ethGate != null) {
					sc = this.socketMgr.getSocketChannel(ethGate);
				}
			}
		}
		return ethId;
	}

	public void sendModifyNeGwInfoNotifyMessage(HashMap<String, String> paramMap, ManagedElement ne) {

		ManagedElement meL = topoManager.getManagedElementById(ne.getId());
		meL.getEthGatewayInfo().setWorkGatewayNeId(ne.getEthGatewayInfo().getWorkGatewayNeId());
		topoManager.updateManagedElement(meL);

		ResponseCollection rc = new ResponseCollection();
		Response res = new Response();
		res.setcmdId("modifyNeGwInfo");
		res.setResult(0);
		CommandParameter cp = new CommandParameter();
		cp.setParaMap(paramMap);
		res.addParam(cp);

		CommandDestination dest = new CommandDestination();
		dest.setNeId(ne.getId());
		dest.setNeType(ne.getProductName());
		dest.setNeVersion(ne.getVersion());
		dest.setNeName(ne.getName());
		cp.setDest(dest);
		res.setDest(dest);
		rc.addResponse(res);
		SyncNotifyManager notifyManager = SyncNotifyManager.getInstance();
		notifyManager.notifyToAll(rc);
	}
	
	protected ReadSession getReadSession(ManagedElement me) throws Exception {
		int ethId = this.getEthGwId(me);
		
		EthGate ethGate = this.ethGateManager.getEthGate(ethId);
		
		if(ethGate == null)	{
//		    log.error("------ do not find a gateway related with ne: " + me.getName() + "------");
			return null;
		}
		ManagedElement gw = null;
		synchronized(this.neMap) {
			gw = this.neMap.get(ethGate.getNeId());
		}
		
		String strIp = gw != null ? gw.getNatIpEx() : ToolUtil.longToIp(ethGate.getIpAddress());
		return this.socketMgr.getReadSession(ethGate);
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
	
	private int getMainGwNeId(int proxyNeId) {
		if (proxyNeId > 0) {
			synchronized (this.neMap) {
				ManagedElement me = this.neMap.get(proxyNeId);

				if (me == null) {
					log.error("$$$$$$db does not  contain the ne which id is " + proxyNeId + "$$$$$$");
					return 0;
				}

				if (me.isServeAsEthGateway()) {
					return me.getId();
				} else if (me.getEthGatewayInfo() != null) {
					return me.getEthGatewayInfo().getMainGatewayNeId();
				} else if (me.getMonitorProxyInfo() != null) {
					return this.getMainGwNeId(me.getMonitorProxyInfo().getProxyNeId());
				}
			}
		}
		return 0;
	}

	private int getStandby1GwNeId(int proxyNeId) {
		if (proxyNeId > 0) {
			synchronized (this.neMap) {
				ManagedElement me = this.neMap.get(proxyNeId);

				if (me == null) {
					log.error("$$$$$$db does not  contain the ne which id is " + proxyNeId + "$$$$$$");
					return 0;
				}

				if (me.isServeAsEthGateway()) {
					return me.getId();
				} else if (me.getEthGatewayInfo() != null) {
					return me.getEthGatewayInfo().getStandby1NeId();
				} else if (me.getMonitorProxyInfo() != null) {
					return this.getStandby1GwNeId(me.getMonitorProxyInfo().getProxyNeId());
				}
			}
		}
		return 0;
	}

	private int getStandby2GwNeId(int proxyNeId) {
		if (proxyNeId > 0) {
			synchronized (this.neMap) {
				ManagedElement me = this.neMap.get(proxyNeId);

				if (me == null) {
					log.error("$$$$$$db does not  contain the ne which id is " + proxyNeId + "$$$$$$");
					return 0;
				}

				if (me.isServeAsEthGateway()) {
					return me.getId();
				} else if (me.getEthGatewayInfo() != null) {
					return me.getEthGatewayInfo().getStandby2NeId();
				} else if (me.getMonitorProxyInfo() != null) {
					return this.getStandby2GwNeId(me.getMonitorProxyInfo().getProxyNeId());
				}
			}
		}
		return 0;
	}
	
	
	public void run() {
//		log.info("%%%%%%%%%%%%WriteSessionTask  begin%%%%%"+new Date()+"%%%%%%%%%%%");
//		synchronized(ws_lock)
		{
			
			AdapterMessagePair mp = (AdapterMessagePair)o;
			if(mp.getScheduleType() == ScheduleType.ST_NEPOLL
					|| mp.getScheduleType() == ScheduleType.ST_GWPOLL){
				mp.setBeginTime();
			}
			/*send manual cmd's progress to client*/
			this.sendProgressToClient(mp);
			
			try  {
				if(mp.isRepliedAll() || mp.isTimeout() || mp.isErr() 
						|| mp.getManagedElement() == null) {
					if(mp.getScheduleType() == ScheduleType.ST_NEPOLL){
						msgPool.subtractNePollNoResponseCount(mp);
					}
					return;
				}
				
				ManagedElement me = mp.getManagedElement();
			
				if (me.getMonitorLinkType() == MonitorLinkType.MLT_ETH)	{
					
					SocketChannel sc = this.getSocketChannel(me);
					
	    			if (null == sc)	{
//	    				log.error("Can not find socket channel for " + strIp);
	    				
	    				mp.setErrCode(ErrorMessageTable.COMM_PORT_NOEXISTS);
	    				this.addMsgToProcessedPoolAndCancelProgress(mp);	    				
	    				
	    				return;
	    			}
	    			
//	    			synchronized(sc) {
						// must put into pool firstly
	    			synchronized(mp){
	    				if(!mp.isSendedMsgPair() || mp.isSendAgin()){
	    					msgPool.addSendedMsg(mp);
	    					mp.setSendedMsgPair(true);
	    				}
	    			}
		    			
		    			
		    			boolean isSyncMode = false;
		    			
						Iterator<AsynTask> taskIt = mp.getAsynTaskList().getTaskList().iterator();
						
						while(!mp.isCancel() && !mp.isErr()&& taskIt.hasNext())	{
								
							AsynTask asynTask = taskIt.next();					

							String destIP = ToolUtil.longToIp(me.getAddress().getIpAddress());			
							
							byte [] srcAddr = sc.socket().getLocalAddress().getAddress();
							byte [] destAddr = ToolUtil.ipStringToBytes(destIP);		
							
			    			for(int ii = 0; !mp.isCancel() && !mp.isErr()
			    							&& ii < asynTask.getTaskCellList().size(); ii++) {
			    				TaskCell taskCell = asynTask.getTaskCellList().get(ii);		    				
			    				//比对协议类型，如果不通则跳过
								if(taskCell.getProtocolType() != null && !(CommProtocol.CP_MSDH.equals(taskCell.getProtocolType())
										|| CommProtocol.CP_OLP.equals(taskCell.getProtocolType()))){
									continue;
								}
			    				if(isSyncMode) {
			    					break;
			    				}

			    				isSyncMode = this.addSendMsg(mp,taskCell, sc,srcAddr, destAddr);	
		    				}
			    			
		    				if(isSyncMode) {
		    					break;
		    				}
						}					
					
						if(mp != null) {
							// avoid timeout before sended
							mp.setIsSend(true);
						} else {
							log.error("mp is null, may be caused by cancel!");
						}

//	    			}
	    			

				}
//			} catch(NullPointerException nullEx) {
//				log.error("------------Null Pointer Exception!-------------");
			} catch (Exception e) {
				mp.setErrCode(ErrorMessageTable.SERVER_EXEC_ERROR);
				this.addMsgToProcessedPoolAndCancelProgress(mp);
				
				log.error("write frame exception:", e);
			}
		}
//		log.info("%%%%%%%%%%%%WriteSessionTask  end%%%%%"+new Date()+"%%%%%%%%%%%");
	}
	
	protected boolean addSendMsg(AdapterMessagePair mp,
			TaskCell taskCell,SocketChannel sc,
			byte[] srcAddr,byte[] destAddr)	{
	
		boolean sslWrite = false;
		SSLReadSession sslrs =null;
		
//		if(!sc.socket().isConnected()){
//			return false;
//		}
		
		if(sc.socket().getPort() == 4000){//有时候取到的是0
			sslWrite = true;
			try {
				 sslrs = (SSLReadSession)this.getReadSession(mp.getManagedElement());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		boolean isSyncMode = this.getSyncSendMode() == 1;
		
//		synchronized(this.msgPool.getLockSendedMsg()) {
			synchronized(taskCell.getLockRawMsg()) {
				
				try	{
					List<HiSysMessage> msgList = taskCell.getMsgList();				
					
					if(msgList == null || msgList.isEmpty()) {
						return false;
					}
					
					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();
	
					for(int iii = 0; iii < msgList.size(); iii++) {						
						HiSysMessage msg = msgList.get(iii);
						
						MsdhMessage frame = (MsdhMessage)msg;	
						
						sendedMsgList.add(frame);
						
						if(msg.isReplied()) {
							continue;
						}
						
						String priorFrameId = frame.getSynchnizationId(true);
				
						frame.setSrcAddress(srcAddr);
						frame.setDstAddress(destAddr);	
						
						this.processTabsFrame(mp, frame);
	
						synchronized(ws_lock) {
							// 构造完整的帧，并注入帧序号				
							frame.buildFrame(EthGate.getNextMsgSequenceNumber());	 
							
//							FileTool.writeSimple("FrameInfo", "FrameId", frame.getSynchnizationId());
						
							// add sent message into sendMsgMap
							taskCell.addSendMsg(frame.getSynchnizationId(true), frame);
							taskCell.removeSendMsg(priorFrameId); 	
							
							if(this.isManualMessagePair(mp)) {
								Thread.sleep(this.getManualSendFrameInterval());
								frame.setTimeThreshold(this.getManualSingleFrameTimeOut());
							} else {
								Thread.sleep(this.getWriteFrameInterval());
							}
							
							
							
							// 构造完整的帧，并注入帧序号
							
							byte[] frameB = frame.getFrame();	        						
							
							// 发送
							ByteBuffer bb = ByteBuffer.wrap(frameB);
							try	{
								if(sslWrite){
									synchronized(sslrs.sslcs){
										sslrs.sslcs.write(bb);
									}
								}else{
									
									synchronized(sc) {
										sc.write(bb);
									}
								}
								frame.setBeginTime(new Date());	
							} catch (Exception e) {
								
								log.error("---write data to socket error: make the socket disconnect!---");
//								log.error("write data to socket error!",e);
								
								// remove sent message from sendMsgMap
								taskCell.removeSendMsg(frame.getSynchnizationId(true));    							
								
								mp.setErrCode(ErrorMessageTable.COMM_WIRTE_DATA_ERROR);

//								this.msgPool.removeSendedMsg(mp);
								
								this.addMsgToProcessedPoolAndCancelProgress(mp);	        							
								
								this.socketMgr.remove(sc);
								this.socketMgr.doConnect();        								
							}    						
	
							if(this.isRecordFrame(mp.getScheduleType())) {
								
								if(frame.getReTransmittedTimes() == 0) {
									FileTool.write("FrameInfo", this.getRecordFileName(mp), frame.getFrameEx());
								} else	{
									FileTool.write("FrameInfo", "ReTransmit", frameB);
								}
							}	
							
							/*manual command*/
							if(this.isManualMessagePair(mp)) {
								this.sendProgressToClient(mp);
								isSyncMode = false;
								continue;
							}
							
							if(iii == 0 && this.getSyncSendMode() == 1) {
								break;
							}
						}							
					}
					
					// remove sended msg
					taskCell.getMsgList().removeAll(sendedMsgList);		
						
				} catch(Exception e) {
					log.error("write frame exception:", e);
				}
			}
			
			if(mp.isErr()) {
				this.msgPool.removeSendedMsg(mp);
			}
			
			
			return isSyncMode;
//		}
	}
	
	private void processTabsFrame(AdapterMessagePair mp,MsdhMessage frame) {
		ManagedElement me = mp.getManagedElement();
		
		if(me == null || me.getAddress() == null) {
			log.info("**********processTabsFrame : me or address is null!*************");
			return;
		}
		
		if(me.getAddress().getAddressType() != AddressType.AT_TABSnIPV4) {
			return;
		}
		
		byte[] data = frame.getFixedData();
		data[4] = (byte)me.getAddress().getNodeAddress();
		
		int checkSum = 0;
		
		for(int i = 0; i < data.length - 2; i++){
			checkSum += getPositive(data[i]);
		}

		data[data.length - 2] = (byte)(checkSum & 0xff);
		data[data.length - 1] = (byte)((checkSum >> 8) & 0xff);
	}
	
	private int getPositive(byte btVal)
	{
		return btVal >= 0 ? btVal : 256 + btVal;
	}
	
	protected void sendProgressToClient(AdapterMessagePair mp) {
		
		if(mp.getMsg() == null 
				|| mp.getManagedElement() == null 
				|| mp.getCmds() == null
				|| mp.getCmds().getCmdsEndType() == CmdsEndType.CET_AUTO) {
			return;
		}
		
		String connectionId;
		try {
			connectionId = mp.getMsg().getStringProperty("connectionID");
			CsCommandCollection ccc=mp.getCmds();
			if(ccc == null || ccc.getCmdList()== null){
				return;
			}
			CsCommand cc=ccc.getCmdList().get(0);
			
			
			SynProgress progress = SyncNotifyManager.getInstance().getProgress(
					Outline.INPROGRESS, mp.getTotalFrames(),mp.getSendedFrames(),
					mp.getResponsedFrames(), "command progress");
			SyncNotifyManager.getInstance().notifyToClientProgress(
					connectionId, 
					ccc.getUser(),
					mp.getManagedElement().getId(), 
					cc.getId(),
					progress);
		} catch (JMSException e) {
			
			log.error("sendProgressToClient exception:", e);
		}

	}
	
	private boolean isManualMessagePair(AdapterMessagePair mp) {
		return mp.getCmds() != null 
				&& mp.getCmds().getCmdsEndType() == CmdsEndType.CET_MANUAL 
				&& this.getManualSyncSendMode() == 0;
	}
}
