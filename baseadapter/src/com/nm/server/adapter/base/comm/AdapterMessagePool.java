package com.nm.server.adapter.base.comm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.descriptor.NeDescriptionLoader;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CsCommand;
import com.nm.message.CsCommandCollection;
import com.nm.message.CsCommandCollection.CmdsEndType;
import com.nm.message.CsCommandCollection.CmdsType;
import com.nm.message.Response;
import com.nm.message.ResponseCollection;
import com.nm.message.progress.SynProgress;
import com.nm.message.progress.SynProgress.Outline;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.TaskType;
import com.nm.server.adapter.base.sm.TaskManager;
import com.nm.server.adapter.com.CommandFilter;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.HiSysMessage.HiMsgType;
import com.nm.server.comm.MessagePair;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ProcessType;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class AdapterMessagePool extends MessagePool
{
	private static final Log log = LogFactory.getLog(AdapterMessagePool.class);
	
	private ClassPathXmlApplicationContext context;
	
	private List<List<HiSysMessage>> revMsgList = new ArrayList<List<HiSysMessage>>();
	
	private Map<Integer,List<AdapterMessagePair>> mapScheduledGwMsgPairs = 
		new HashMap<Integer,List<AdapterMessagePair>>();
	
	private Map<Integer,ManagedElement> neMap;
	
	protected byte[] lockGwMp = new byte[0];
	protected byte[] lockScheduledGwMp = new byte[0];
	
	public AdapterMessagePool(ClassPathXmlApplicationContext context)
	{
		this.setAppContext(context);
	}
	
	public void setAppContext(ClassPathXmlApplicationContext context)
	{
		this.context = context;
	}

	public ClassPathXmlApplicationContext getAppContext()
	{
		return context;
	}	
	
	public void setNeMap(Map<Integer,ManagedElement> neMap) {
		this.neMap = neMap;
	}
	
	public void addRevMsgList(List<HiSysMessage> msgList)
	{
		synchronized(this.revMsgList)
		{
			this.revMsgList.add(msgList);
		}
	}
	
	public void addScheduledGwMsgPair(int gwId, AdapterMessagePair mp) {
		synchronized(this.lockScheduledGwMp) {
			List<AdapterMessagePair> mpList = this.mapScheduledGwMsgPairs.get(gwId);
			
			if(mpList == null) {
				mpList = new ArrayList<AdapterMessagePair>();
				this.mapScheduledGwMsgPairs.put(gwId, mpList);
			}
			
			mpList.add(mp);
		}
	}
	
	public AdapterMessagePair getScheduledGwMsgPair(int gwId) {
		synchronized(this.lockScheduledGwMp) {
			List<AdapterMessagePair> mpList = this.mapScheduledGwMsgPairs.get(gwId);
			
			if(mpList == null || mpList.isEmpty()) {
				return null;
			}
			
			AdapterMessagePair mp = null;
			
			Iterator<AdapterMessagePair> it = mpList.iterator();
			while(it.hasNext()) {
				mp = it.next();
				it.remove();
				break;
			}

			return mp;
		}
	}
	


	public boolean reply() {
//		synchronized(this.lockRcvMsg)
		{
			if(this.rcvMessage.size() > 0) {
//				log.info("reply:msgList is not null!");
//				for(int i = 0; i < rcvMessage.size();i++)
				{
					List<HiSysMessage> msgList = null;
					try
					{
						msgList = rcvMessage.take();
					}
					catch (InterruptedException e)
					{
						log.error("AdatperMessagePool reply exception:", e);
					}
					ManagedElement me = null;
					for(HiSysMessage msgL : msgList)
					{
//						log.info("===recieve:"+msgL.getSynchnizationId(false)+"===");
						
						boolean isPoll = false;
						ScheduleType stype = null;
						boolean isMatched = true;
						boolean isSync = false;
						// Trap command,the command serial is 0.  
						if(msgL.getCommadSerial() == 0)	{
							if(msgL instanceof TabsMessage
									|| ToolUtil.isIpAmongHostIps(msgL.getDestAddress())) {
//								log.info("Trap:" + msgL.getCommandId());
								
								this.insertTrapMessage(msgL);
							}							
						} else {
//							log.info("before get lockMsgPair");
							synchronized(this.locksendedMsg) {
//								log.info("afater get lockMsgPair");
//								log.info("sendedMsgPairs.size() = " + sendedMsgPairs.size());
								Iterator<MessagePair> it = sendedMsgPairs.iterator();
								while (it.hasNext())
								{
									MessagePair mp = it.next();
									me = mp.getManagedElement();
//									log.info("msgPairs.size = " + msgPairs.size());
									
									if(this.isCancelledMsg(mp)) {
										this.addCancelledMsg(mp);
										
										it.remove();
										continue;
									}
									
									isMatched = mp.reply(msgL);
									
									if(mp.isRepliedAllFromNe() 
											|| (mp.isTimeout() && mp.isSend())
											|| mp.getErrCode() == ErrorMessageTable.PART_SUCCESS)	{
										this.addResponsedMsg(mp);
										it.remove();
										
//										log.info("%%%%%%%%%%%%add responsed msg  %%%%%"+new Date()+"%%%%%%%%%%%");
									}
									
									if(isMatched) {
										isPoll = mp.getScheduleType() != ScheduleType.ST_NA;
										stype = mp.getScheduleType();
										isSync = mp.isSync();
										this.sendProgressToClient((AdapterMessagePair)mp,Outline.INPROGRESS);
										break;
									}
								}					
							}
							
							synchronized(this.lockScheduledSendedMsg) {
//								log.info("afater get lockMsgPair");
//								log.info("sendedMsgPairs.size() = " + sendedMsgPairs.size());
								Iterator<MessagePair> it = this.scheduledSendedMsgPairs.iterator();
								while (it.hasNext())
								{
									MessagePair mp = it.next();
									
//									log.info("msgPairs.size = " + msgPairs.size());
									
									isMatched = mp.reply(msgL);

									if(mp.isRepliedAllFromNe() 
											|| (mp.isTimeout() && mp.isSend())
											|| mp.getErrCode() == ErrorMessageTable.PART_SUCCESS)	{
										this.addResponsedMsg(mp);
										it.remove();
										
//										AdapterMessagePair unSendMp = this.getScheduledGwMsgPair(((AdapterMessagePair)mp).getGwId());
//										
//										if(unSendMp != null) {
//											this.addScheduledUnSendMsg(unSendMp);
//										} 
										
//										log.info("%%%%%%%%%%%%add responsed msg  %%%%%"+new Date()+"%%%%%%%%%%%");
									}
									
									if(isMatched) {
										isPoll = mp.getScheduleType() != ScheduleType.ST_NA;
										stype = mp.getScheduleType();
										isSync = mp.isSync();
										break;
									}
								}					
							}							
						}
						
						String recordName = this.getRecordName(isPoll, isMatched, isSync, msgL,stype);
						
						if(this.isRecordFrame(isPoll) == 1 && recordName != null) {
							
							if(msgL instanceof MsdhMessage) {
								FileTool.write("FrameInfo",recordName , ((MsdhMessage)msgL).getFrameEx());
							} else if(msgL instanceof OlpMessage){
								FileTool.writeSimple("FrameInfo",recordName , ((OlpMessage)msgL).getUserData());
							}else if(msgL instanceof TabsMessage){
								TabsMessage msg = (TabsMessage)msgL;
								String tabsAddr = msg.getTabsAddr();
								if(me != null && (tabsAddr == null || tabsAddr.isEmpty())){
									tabsAddr = me.getAddress().getTabsAddress() + "-" + me.getAddress().getNodeAddress();
								}
								FileTool.writeTabsFrame("FrameInfo", recordName, msg.getFrame(), ToolUtil.getHostIp(), tabsAddr);
							}else if(msgL instanceof UdpMessage){
								StringBuilder frameData = new StringBuilder();
								String ip = "0.0.0.0";
								int port = 0;
								if(me==null){
									continue;
								}
								if(me.getEthGatewayInfo()!=null){
									if(neMap!=null && neMap.get(me.getEthGatewayInfo().getWorkGatewayNeId())!=null){
										ip = ToolUtil.longToIp(neMap.get(me.getEthGatewayInfo().getWorkGatewayNeId()).getAddress().getIpAddress());
										port = neMap.get(me.getEthGatewayInfo().getWorkGatewayNeId()).getAddress().getMonitorServicePort();
									}
								}
								frameData.append("ip=").append(ip).append(" ").append("port=").append(port).append("\n");
								byte[] frame = ((UdpMessage)msgL).getUserData();
								for(int i=0; i<frame.length; i++){
									String hv = Integer.toHexString(frame[i] & 0xff);
									if(hv.length()<2){
										frameData.append(0);
									}
									frameData.append(hv);
									if(i<frame.length-1){
										frameData.append(" ");
									}
								}
								FileTool.writeSimple("FrameInfo", recordName , frameData.toString());
								ip = null;
								frameData = null;
							}
							
								
						}
					}
				}
//				this.rcvMessage.removeAllElements();
				
				
				
				
			} else	{
				synchronized(this.revMsgList) {
					Iterator<List<HiSysMessage>> it = this.revMsgList.iterator();					
					
					while(it.hasNext())	{
						List<HiSysMessage> msgList = it.next();
						
						this.insertRcvMessageList(msgList);
						
						it.remove();
					}
				}
				
				
				synchronized(this.locksendedMsg) {
					Iterator<MessagePair> it = sendedMsgPairs.iterator();
					
					while (it.hasNext()) {
						MessagePair mp = it.next();		
						
						if(this.isCancelledMsg(mp)) {
							this.addCancelledMsg(mp);
							it.remove();
							continue;
						}
						
						if(mp.isRepliedAllFromNe() 
								|| (mp.isTimeout() && mp.isSend())
								|| mp.getErrCode() == ErrorMessageTable.PART_SUCCESS)	{

							this.addResponsedMsg(mp);							
							it.remove();
							
							continue;
//							log.info("%%%%%%%%%%%%add responsed msg  %%%%%"+new Date()+"%%%%%%%%%%%");
						}
						
						if(!((AdapterMessagePair)mp).isManualTimeout() 
								&&((AdapterMessagePair)mp).isAllManualFrameTimeout()) {
							this.sendProgressToClient((AdapterMessagePair)mp,Outline.TIMEOUT);
							((AdapterMessagePair)mp).setManualTimeout(true);
						}
						
						
						if(((AdapterMessagePair)mp).canBeSendedAgain()) {
							
							it.remove();
							
							mp.setIsSend(false);
							((AdapterMessagePair)mp).setSendedMsgPair(false);
							((AdapterMessagePair)mp).setSendAgin(true);
							this.addUnSendMsg(mp);
							
							continue;
						}
					}					
				}
				
				synchronized(this.lockScheduledSendedMsg) {
					Iterator<MessagePair> it = scheduledSendedMsgPairs.iterator();
					
					while (it.hasNext()) {
						MessagePair mp = it.next();		
						
						if(mp.isRepliedAllFromNe() 
								|| (mp.isTimeout() && mp.isSend())
								|| mp.getErrCode() == ErrorMessageTable.PART_SUCCESS)	{

							this.addResponsedMsg(mp);							
							it.remove();
							
//							AdapterMessagePair unSendMp = this.getScheduledGwMsgPair(((AdapterMessagePair)mp).getGwId());
//							
//							if(unSendMp != null) {
//								this.addScheduledUnSendMsg(unSendMp);
//							}
							
							
							continue;
//							log.info("%%%%%%%%%%%%add responsed msg  %%%%%"+new Date()+"%%%%%%%%%%%");
						}
					}					
				}
			}
		}
		return true;
	}	
	
	private int isRecordFrame(boolean isPoll) {
		
		return isPoll ? 
				ToolUtil.getEntryConfig().getPollFrameRecordOnOff():
					ToolUtil.getEntryConfig().getFrameRecordOnOff();
	}
	
	
	private String getRecordName(boolean isPoll,boolean isMatched, 
			boolean isSync,HiSysMessage msgL,ScheduleType stype) {
		String recordName = "Response";
		
		if(!isPoll)	{
				
			if(msgL.getCommadSerial() == 0)	{
				if(ToolUtil.isIpAmongHostIps(msgL.getDestAddress())) {
					recordName = "Trap";
				} else	{
					recordName = null;
				}
			} else if(!isMatched) {
				recordName = "Timeout";
			} else if(isSync){
				recordName = "SyncResponse";
			}
			
		} else	{
			if(stype == ScheduleType.ST_ALARMPOLL||stype == ScheduleType.ST_HISALARMPOLL){
				return "AlarmPollResponse";
			}
			if(isSync) {
				recordName = "SyncResponse";
			} else {
				recordName = "PollResponse";
			}
		}
		
		return recordName;
	}
	
	public void insertTrapMessage(HiSysMessage msg)	{
		
		
		ManagedElement me = CommandFilter.getBelogedNe(
					ToolUtil.bytesToLong(true, msg.getSrcAddress()), neMap);
		
		if(me == null 
				&& !(msg.getMessageType() == HiMsgType.HTM_NONETRAP 
				&& TaskManager.getInstance().getAdapterName().equalsIgnoreCase("adapter-1"))) {
			return;
		}
		
		
		String clsName = "Trap_";
		AdapterMessagePair mp = new AdapterMessagePair();	
		
		String neType = null;
		String neVer = null;
		
		if(me != null) {
			neType = me.getProductName();
			neVer = me.getVersion();
		} else {
			neType = ((BaseMessage)msg).getNeType();
			neVer = ((BaseMessage)msg).getNeVersion();
		}
		ResFunc resFunc = null;
		if(msg instanceof SnmpNewMessage){
			clsName += neType;
			clsName += "_";
			clsName += msg.getCommandId();
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
			
			resFunc = getResFunc(clsName, neType, neVer);
			if(resFunc == null){
				clsName = "Trap_" + msg.getCommandId();
				resFunc = getResFunc(clsName, neType, neVer);
			}
		}
		else if(msg instanceof SnmpMessage)
		{
			clsName += msg.getCommandId();
			mp.setProtocol(CommProtocol.CP_SNMP);
			resFunc = getResFunc(clsName, neType, neVer);
			if(resFunc == null){
				resFunc = getResFunc(clsName, "GPN", neVer);
			}
		}else if(msg instanceof TabsMessage)
		{
			clsName += msg.getCommandId();
			mp.setProtocol(CommProtocol.CP_MSDH);
			resFunc = getResFunc(clsName, neType, neVer);
		}
		else
			
		{
			clsName += msg.getCommandId();
			mp.setProtocol(CommProtocol.CP_MSDH);
			resFunc = getResFunc(clsName, neType, neVer);
		}

		log.info(clsName + " ipaddr = " + ToolUtil.bytesToIp(true, msg.getSrcAddress()));

		if(resFunc == null)
		{
			return;
		}
		resFunc.setAppContext(context);

//		clsName = resFunc.getCmdId() != null ? resFunc.getCmdId() : clsName;
		
		AsynTaskList asynTaskList = null;
		
		if(!resFunc.isTrapToServer())
		{
			asynTaskList = new AsynTaskList(TaskType.TASK_TRAP);	
		}
		else
		{
			asynTaskList = new AsynTaskList(TaskType.TASK_TRAPTOSERVER);
		}
		
		AsynTask asynTask = new AsynTask();	
		Response res = new Response();
		if (resFunc.getCmdId() != null) {
			res.setcmdId(resFunc.getCmdId());
		} else {
			res.setcmdId(clsName);
		}
		asynTask.setResponse(res);
		
		TaskCell taskCell = new TaskCell(clsName);
		
		taskCell.addResMsg("0", msg);
		taskCell.setObj(resFunc);
		
		asynTask.addTaskCell(taskCell);
		asynTaskList.addTask(asynTask);
		
		ResponseCollection rc = new ResponseCollection();
		rc.setCmdsType(CmdsType.CT_TRAP);
		rc.addResponse(res);
		
		mp.setResponseCollection(rc);
		
		mp.setAsynTaskList(asynTaskList);
		mp.setTrap(true);
		mp.setIsSend(true);
		
		if(me != null){
			ManagedElement ne = me.clone();
			ne.setProductName(neType);
			mp.setManagedElement(ne);
		}

//		this.insertMessagePair(mp);
		
		this.addResponsedMsg(mp);
		
	}
	
	private ResFunc getResFunc(String sCommand,String sNeType,String sVersion)
	{
		ResFunc resFunc = null;
		
	
		if(sCommand != null)
		{			
			
			if(sNeType != null && sNeType != "")
			{				
				
				// special implements
				List<String> verList = NeDescriptionLoader.getVersionList(sNeType);
				
				boolean startTag = false;
				
				for(int verIndex = verList.size() - 1; verIndex > 0; verIndex--)
				{
					if(sVersion.equals(verList.get(verIndex)) && !startTag)
					{
						startTag = true;
					}
					
					if(startTag)
					{
						
						String funcNameL = sNeType + "_" + verList.get(verIndex) + "_" + sCommand;
						
						
		    			try
		    			{
		    				resFunc = this.getResFunc(funcNameL);
		    			}
		    			catch (NoSuchBeanDefinitionException ee)
		    			{
//		    				log.info("no bean:" + funcNameL);
		    				
		    			}						
					}
				}
				// common implements
				if(resFunc == null)
				{
					String funcNameL = sNeType + "_" + sCommand;
					
					
	    			try
	    			{
	    				resFunc = this.getResFunc(funcNameL);
	    			}
	    			catch (NoSuchBeanDefinitionException ee)
	    			{
//	    				log.info("no bean:" + funcNameL);
	    				
	    				try
	    				{
	    					resFunc = this.getResFunc(sCommand);
	    				}
	    				catch (NoSuchBeanDefinitionException eee)
	    				{
	    					log.info("no bean:" + sCommand);	
	    					log.error(sCommand + " is error!");
	    				}	
	    				
	    			}							
				}				
				
			}
			else
			{
				try
				{
					resFunc = this.getResFunc(sCommand);
				}
				catch (NoSuchBeanDefinitionException eee)
				{
					log.info("no bean:" + sCommand);	
					log.error(sCommand + " is error!");
				}	
			}
	
		}
		
		if(resFunc != null)
		{
			resFunc.setCmdId(sCommand);
		}
		
		return resFunc;
	}	
	
	
	
	private ResFunc getResFunc(String name)
	{
		if(this.context != null)
		{
				return (ResFunc)this.context.getBean(name);
		}
		
		return null;
	}
	
	
	public MessagePair getMessagePair()
	{			
		synchronized(lockMsgPair)
		{
			Iterator<MessagePair> it = msgPairs.iterator();
			while (it.hasNext())
			{
				MessagePair mp = it.next();
				
				// remove replied /timeout message 
				if((mp.isRepliedAllFromNe() || mp.isTimeout() || mp.isErr()) && 
						mp.isSend() && 
						!mp.isSync()&& 
//						!mp.isRepliedAll()
						mp.getProcessType() == ProcessType.PT_UNPROCESSED)				
//				if((mp.isRepliedAllFromNe() || mp.isTimeout() || mp.isTrap() || mp.isErr()) 
//						&& mp.isSend() 
//						&& !mp.isSync()
//						&& !mp.isRepliedAll() || mp.isCancel())
				
				{				
//					if(mp.getSchedule() == 0)
//					{
//						it.remove();						
//					}
					
					mp.setProcessType(ProcessType.PT_PROCESSING);
					
					return mp;
				}			 
			}
			return null;			
		}		
		
	}
	
	public void enableTransmitMessagePair(String key) {
		try {
			synchronized (this.locksendedMsg) {
				Iterator<MessagePair> it = sendedMsgPairs.iterator();
				while (it.hasNext()) {
					MessagePair mp = it.next();
					
					if(mp.getCmds() == null 
							|| mp.getMsg() == null 
							|| mp.getManagedElement() == null|| 
							mp.getCmds().getCmdsEndType() == CmdsEndType.CET_AUTO) {
						continue;
					}
					
					if(key.equalsIgnoreCase(mp.getMsgKey())) {
						((AdapterMessagePair)mp).enableTransmitFrames();
						break;
					}
					

				}
			}
		} catch (Exception e) {
			log.error("enableTransmitMessagePair exception:", e);
		}
	}
	
	private void sendProgressToClient(AdapterMessagePair mp,Outline ol) {
		
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
			CsCommand cc=ccc.getCmdList().get(0);
			
			
			SynProgress progress = SyncNotifyManager.getInstance().getProgress(
					ol, mp.getTotalFrames(),mp.getSendedFrames(),
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
	
	

}
