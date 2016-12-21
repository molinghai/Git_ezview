package com.nm.server.adapter.base.comm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jms.Message;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.error.ErrorMessageTable;
import com.nm.message.CsCommandCollection;
import com.nm.message.CsCommandCollection.CmdsEndType;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePair;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class AdapterMessagePair extends MessagePair
{
	private static final Log log = LogFactory.getLog(AdapterMessagePair.class);
	
	private CommProtocol 	protocol = CommProtocol.CP_MSDH;
	
	private AsynTaskList asynTaskList;
	
	private int gwId;
	
	private boolean supportTransmit = true;
	private boolean isManualTimeout = false;
	
	public AdapterMessagePair()	{
		super();
	}
	
	public AdapterMessagePair(Message msg,CsCommandCollection cmds)	{
		super(msg,cmds);
	}	
	
	
	public AdapterMessagePair(Message msg,CsCommandCollection cmds,AsynTaskList asynTaskList)
	{
		this(msg,cmds);
		this.setAsynTaskList(asynTaskList);
		
//		this.setTimeThreshold(cmds);			// set ne synchronized threshold time.
		
//		this.setTotal();
		
		this.setNotify();
	}
	
	
	/**
	 * @param protocol the protocol to set
	 */
	public void setProtocol(CommProtocol protocol)
	{
		this.protocol = protocol;
	}

	/**
	 * @return the protocol
	 */
	public CommProtocol getProtocol()
	{
		return protocol;
	}

	public void setAsynTaskList(AsynTaskList asynTaskList)
	{
		this.asynTaskList = asynTaskList;
	}

	public AsynTaskList getAsynTaskList()
	{
		return asynTaskList;
	}	
	
	public void setGwId(int gwId) {
		this.gwId = gwId;
	}

	public int getGwId() {
		return gwId;
	}
	
	public void setSupportTransmit(boolean supportTransmit) {
		this.supportTransmit = supportTransmit;
	}

	public boolean isSupportTransmit() {
		return supportTransmit;
	}

	public void setManualTimeout(boolean isManualTimeout) {
		this.isManualTimeout = isManualTimeout;
	}

	public boolean isManualTimeout() {
		return isManualTimeout;
	}

	private boolean isSendedMsgPair = false;
	private boolean isSendAgin = false;//避免多协议重发
	public boolean isSendAgin() {
		return isSendAgin;
	}

	public void setSendAgin(boolean isSendAgin) {
		this.isSendAgin = isSendAgin;
	}
	public boolean isSendedMsgPair() {
		return isSendedMsgPair;
	}

	public void setSendedMsgPair(boolean isSendedMsgPair) {
		this.isSendedMsgPair = isSendedMsgPair;
	}

	public boolean reply(HiSysMessage msg) {
//		log.info("===reply-out:"+msg.getSynchnizationId(false)+"===");
	
		if(this.asynTaskList == null 
				|| this.asynTaskList.getTaskList() == null) {
			log.info("task list is null");
			return false;
		}
		
		boolean findMatch = false;
		
		Iterator<AsynTask> taskIt = this.asynTaskList.getTaskList().iterator();
			
		while(taskIt.hasNext())	{
				
			AsynTask asynTask = taskIt.next();				

			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {	
				TaskCell taskCell = asynTask.getTaskCell(ii);
				
				synchronized(taskCell.getLockSendMsg())	{
					Map<String,HiSysMessage> sendMsgMap = taskCell.getSendMsgMap();							
					
					Iterator<String> keyIt = sendMsgMap.keySet().iterator();
					
					while (keyIt.hasNext())	{
						String synchronizedId = keyIt.next();
						HiSysMessage sendedMsg = sendMsgMap.get(synchronizedId);
//								log.info("===match-out:"+synchronizedId+"===");
						
						if(sendedMsg.isReplied()) {
							continue;
						}
						
						// IP地址 + 帧序号 唯一确定一条命令
						if (!findMatch
								&& (0 == synchronizedId.compareTo(
										msg.getSynchnizationId(false))) 
										|| msg.hasSameSynchronizationId(sendedMsg)) {
							
							/*超时的应答帧，不再处理。*/
							if(sendedMsg.isTimeOut()) {
								return false;
							}
							
							findMatch = true;
							sendedMsg.setReplied(true);
							taskCell.addResMsg(synchronizedId, msg);

							break;
						}									
					}
				}							
				
				if(findMatch) {
					break;
				}
			}
		}			
		return findMatch;
	}	
	
	public boolean isRepliedAllFromNe()	{
		
		boolean isReponsed = this.isAllFrameReponsed();
		boolean isTimeOut = this.isAllFrameTimeout();
		
		if(isReponsed || isTimeOut)	{
			if(isTimeOut) {
				this.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
				this.setTimeout(true);
			}
			return true;
		}
		return false;
	}	
	
	private boolean isAllFrameReponsed()
	{
		boolean bRtn = true;
		
		
		if(this.asynTaskList != null)
		{
			// polling frames don't need to be retransmitted.
			if(this.getScheduleType() == ScheduleType.ST_NA 
					&& this.getProtocol() != CommProtocol.CP_SNMP 
					&& this.getProtocol() != CommProtocol.CP_SNMP_NEW
					&& this.getProtocol() != CommProtocol.CP_CLI
					&& this.isSupportTransmit())
			{
				this.processTimeoutFrames();
			}						
			
			bRtn = this.isAllFrameResponsed();
			
		}	
		
		return bRtn;
	}
	
	private boolean isAllFrameResponsed(TaskCell tc)
	{
		
		if(tc == null)
		{
			return true;
		}
		
		return (tc.getRawMsgSize() == 0 &&
		tc.getSendMsgSize() == tc.getResMsgSize());
	}
	
	
	
	private boolean isAllFrameResponsed()
	{
		boolean bRtn = true;
		
		
		for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)
		{
				
			AsynTask asynTask = this.asynTaskList.getTask(i);		
			
			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++)
			{	
				
				TaskCell taskCell =  asynTask.getTaskCell(ii);
				
				// 主动上报
				if(taskCell.getRawMsgSize() == 0 && 
						taskCell.getSendMsgSize() == 0 &&
						taskCell.getResMsgSize() > 0)	{
					break;
				}
				
				// 命令未完全发送/未完全应答
				if(!this.isAllFrameResponsed(taskCell))	{						

					bRtn = false;
					break;
				}			
			}	
		}
		
		return bRtn;
	}
	
	private void processTimeoutFrames()	{
//		boolean isNeedReTransmitted = false;
		
		int transmittedCounts = ToolUtil.getEntryConfig().getRetransmitTimes();
			
		for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)	{
				
			AsynTask asynTask = this.asynTaskList.getTask(i);		
			
			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {	
				
				TaskCell taskCell =  asynTask.getTaskCell(ii);
				
				/* 主动上报 */
				if(taskCell.getRawMsgSize() == 0 && 
						taskCell.getSendMsgSize() == 0 &&
						taskCell.getResMsgSize() > 0)	{
					break;
				}
				
				//snmp、cli功能不需要在这里重发
				if(CommProtocol.CP_SNMP_NEW.equals(taskCell.getProtocolType())
						|| CommProtocol.CP_SNMP.equals(taskCell.getProtocolType())
						|| CommProtocol.CP_CLI.equals(taskCell.getProtocolType())){
							continue;
						}
				/* 有未发送完全的帧，不处理超时帧*/
//				if(!taskCell.getMsgList().isEmpty()) {
//					break;
//				}
				
				List<HiSysMessage> timeoutMsgList= new ArrayList<HiSysMessage>();
						
				// 单条命令超时，需要重发
				synchronized(taskCell.getLockSendMsg())
				{
					Iterator<String> it = taskCell.getSendMsgMap().keySet().iterator();
					
					while(it.hasNext())	{
						String cmdId = it.next();
						HiSysMessage sendedMsg = taskCell.getSendMsgMap().get(cmdId);
					
						if(!sendedMsg.isReplied() && sendedMsg.isTimeOut())	{
//								sendedMsg.setSyncSend(false);								
							
							if(sendedMsg.getReTransmittedTimes() < transmittedCounts) {
								sendedMsg.increaseReTransmittedTimes();
								
								// avoid timeout for many times.
								sendedMsg.setBeginTime(null);
								
//								isNeedReTransmitted = true;	
								
								timeoutMsgList.add(sendedMsg);
							}								
							
							if(sendedMsg instanceof MsdhMessage) {
								FileTool.write("FrameInfo", "timeout", ((MsdhMessage)sendedMsg).getFrame());
							}else if(sendedMsg instanceof OlpMessage){
								FileTool.writeSimple("FrameInfo", "timeout", ((OlpMessage)sendedMsg).getUserData());
							}else if(sendedMsg instanceof TabsMessage){
								ManagedElement me = this.getManagedElement();
								TabsMessage msg = (TabsMessage)sendedMsg;
								String tabsAddr = msg.getTabsAddr();
								if(me != null && (tabsAddr == null || tabsAddr.isEmpty())){
									tabsAddr = me.getAddress().getTabsAddress() + "-" + me.getAddress().getNodeAddress();
								}
								FileTool.writeTabsFrame("FrameInfo","timeout", msg.getFrame(),  ToolUtil.getHostIp(), msg.getTabsAddr());
							}
							
							
//							it.remove();								
						}
					}
				}			

 				synchronized(taskCell.getLockRawMsg()) {
					
					if(!timeoutMsgList.isEmpty())
					{	
						taskCell.getMsgList().addAll(0,timeoutMsgList);
					}
				}
			}
		}
	}
	
	private boolean isAllFrameTimeout()
	{
		if(this.asynTaskList != null)
		{
			if (this.getCmds() == null
					|| this.getCmds().getCmdsEndType() == CmdsEndType.CET_MANUAL ) {
				return false;
			}
						
			Iterator<AsynTask> taskIt = this.asynTaskList.getTaskList().iterator();
			
			while(taskIt.hasNext())
			{
					
				AsynTask asynTask = taskIt.next();		
				
				for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++)
				{	
					
					TaskCell tc =  asynTask.getTaskCell(ii);
					
					
					if(this.isAllFrameTimeout(tc))
					{
						return true;
					}
					
					
				}			
			}
			
		}
		
		return false;
	}
	
	private boolean isAllFrameTimeout(TaskCell tc)
	{
		
		if(tc != null)
		{		
			if(/*tc.getMsgList().isEmpty() &&*/ tc.getSendMsgSize() != tc.getResMsgSize())
			{
				int transmittedCounts = ToolUtil.getEntryConfig().getRetransmitTimes();
				int transmittedCountsSerial = ToolUtil.getEntryConfig().getRetransmitTimesSerial();
				synchronized(tc.getLockSendMsg()) {
				
					Iterator<String> it = tc.getSendMsgMap().keySet().iterator();
					
					while(it.hasNext())
					{
						String key = it.next();
						
						HiSysMessage msg = tc.getSendMsgMap().get(key);
						
						if(!msg.isReplied() && msg.isTimeOut())	{
							 
							if(this.getScheduleType() == ScheduleType.ST_NEPOLL
								|| this.getScheduleType() == ScheduleType.ST_ALARMPOLL 
								|| this.getScheduleType() == ScheduleType.ST_HISALARMPOLL 
								|| this.getProtocol() == CommProtocol.CP_SNMP 
								|| this.getProtocol() == CommProtocol.CP_SNMP_NEW) {
								return true;
							} else if( this.getProtocol().equals(CommProtocol.CP_TABS)
									&& msg.getReTransmittedTimes() >= transmittedCountsSerial){
								return true;
							}else if(msg.getReTransmittedTimes() >= transmittedCounts){
								return true;
							}
						}
					}				
				}
			}
		}
		
		return false;
	}
	
	public boolean canBeSendedAgain() {
		
		/* must be sended.*/
		if(!this.isSend()) {
			return false;
		}
		
		/* don't have frames which can't have reponses.*/
		if(this.haveUnResponsedFrames()) {
			return false;
		}
		
		/*msgList is empty*/
		if(this.haveUnSendedFrames()) {
			return true;
		}

		return false;
	}
	
	public boolean haveUnResponsedFrames() {
			
		if(this.asynTaskList == null) {
			return false;
		}
		
		/* don't have frames which can't have reponses.*/
		for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)	{
				
			AsynTask asynTask = this.asynTaskList.getTask(i);		
			
			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {	
				
				TaskCell taskCell =  asynTask.getTaskCell(ii);
				if(CommProtocol.CP_SNMP.equals(taskCell.getProtocolType())
						|| CommProtocol.CP_CLI.equals(taskCell.getProtocolType())){
					continue;
				}
				// 主动上报
				if(taskCell.getRawMsgSize() == 0 && 
						taskCell.getSendMsgSize() == 0 &&
						taskCell.getResMsgSize() > 0)	{
					break;
				}

				if(this.haveUnResponsedFrames(taskCell)) {						
					return true;
				}			
			}	
		}

		return false;
	}
	
	public boolean haveUnSendedFrames() {
		
		if(this.asynTaskList == null) {
			return false;
		}
		
		for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)	{
			
			AsynTask asynTask = this.asynTaskList.getTask(i);		
			
			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {	
				
				TaskCell taskCell =  asynTask.getTaskCell(ii);
				if(CommProtocol.CP_SNMP.equals(taskCell.getProtocolType())
						|| CommProtocol.CP_CLI.equals(taskCell.getProtocolType())){
					continue;
				}
				if(taskCell.getRawMsgSize() > 0) {
					return true;
				}
			}	
		}
		
		return false;
	}
	
	private boolean haveUnResponsedFrames(TaskCell tc) {
		
		if(tc == null) {
			return false;
		}
		
		synchronized(tc.getLockSendMsg()) {
			Iterator<String> it = tc.getSendMsgMap().keySet().iterator();
			while(it.hasNext()) {
				String key = it.next();
				
				
				
				if(tc.getResMsg(key) == null) {
					HiSysMessage msg = tc.getSendMsgMap().get(key);
					
					if(msg.getBeginTime() != null && !msg.isTimeOut()) {
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	public boolean isReTransmitMessagePair() {
		return this.haveUnSendedFrames() && !this.isSendedMapEmpty();
	}
	
	public boolean isSendedMapEmpty() {
		
		if(this.asynTaskList == null) {
			return true;
		}
		
		/* don't have frames which can't have reponses.*/
		for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)	{
				
			AsynTask asynTask = this.asynTaskList.getTask(i);		
			
			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {	
				
				TaskCell taskCell =  asynTask.getTaskCell(ii);
				
				if(!this.isSendedMapEmpty(taskCell)) {						
					return false;
				}			
			}	
		}

		return true;
	}
	
	
	private boolean isSendedMapEmpty(TaskCell tc) {
		
		if(tc == null) {
			return true;
		}
		
		synchronized(tc.getLockSendMsg()) {
			Iterator<String> it = tc.getSendMsgMap().keySet().iterator();
			while(it.hasNext()) {
				String key = it.next();
				
				if(tc.getResMsg(key) == null) {
					return false;
				}
			}
		}
		
		return true;
	}	
	
	public void enableTransmitFrames() {
		for (int i = 0; i < this.asynTaskList.getTaskList().size(); i++) {
			AsynTask asynTask = this.asynTaskList.getTask(i);
			for (int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {
				TaskCell taskCell = asynTask.getTaskCell(ii);
				/* 主动上报 */
				if (taskCell.getRawMsgSize() == 0 && taskCell.getSendMsgSize() == 0
						&& taskCell.getResMsgSize() > 0) {
					break;
				}
				List<HiSysMessage> timeoutMsgList = new ArrayList<HiSysMessage>();
				
				synchronized (taskCell.getLockSendMsg()) {
					Iterator<String> it = taskCell.getSendMsgMap().keySet().iterator();
					while (it.hasNext()) {
						String cmdId = it.next();
						HiSysMessage sendedMsg = taskCell.getSendMsgMap().get(cmdId);
						if (!sendedMsg.isReplied() && sendedMsg.isTimeOut()) {
							// avoid timeout for many times.
							sendedMsg.setBeginTime(null);
							timeoutMsgList.add(sendedMsg);
							it.remove();
						}
					}
				}
				synchronized (taskCell.getLockRawMsg()) {
					if (!timeoutMsgList.isEmpty()) {
						taskCell.getMsgList().addAll(0, timeoutMsgList);
						this.setManualTimeout(false);
					}
				}
			}
		}
	}
	
	public int  getTotalFrames() {
		
		int total = 0;
		
		for (int i = 0; i < this.asynTaskList.getTaskList().size(); i++) {
			AsynTask asynTask = this.asynTaskList.getTask(i);
			for (int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {
				TaskCell taskCell = asynTask.getTaskCell(ii);
				total += (taskCell.getRawMsgSize() + taskCell.getSendMsgSize());
			}
		}
		
		return total;
	}
	
	public int getSendedFrames() {
		int responsedNum = 0;
		for (int i = 0; i < this.asynTaskList.getTaskList().size(); i++) {
			AsynTask asynTask = this.asynTaskList.getTask(i);
			for (int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {
				TaskCell taskCell = asynTask.getTaskCell(ii);
				
				responsedNum+=taskCell.getSendMsgSize();
			}
		}
		
		return responsedNum;
	}
	
	
	public int getResponsedFrames() {
		int responsedNum = 0;
		for (int i = 0; i < this.asynTaskList.getTaskList().size(); i++) {
			AsynTask asynTask = this.asynTaskList.getTask(i);
			for (int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {
				TaskCell taskCell = asynTask.getTaskCell(ii);
				
				responsedNum+=taskCell.getResMsgSize();
			}
		}
		
		return responsedNum;
	}
	
	public boolean isAllManualFrameTimeout() {
		if (this.getCmds() == null
				|| this.getCmds().getCmdsEndType() != CmdsEndType.CET_MANUAL 
				|| this.asynTaskList == null) {
			return false;
		}
		if(this.isSend()){
			processManualTimeoutFrames();
		}
		
		
		Iterator<AsynTask> taskIt = this.asynTaskList.getTaskList().iterator();
		while (taskIt.hasNext()) {
			AsynTask asynTask = taskIt.next();
			for (int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {
				TaskCell tc = asynTask.getTaskCell(ii);
				if (!this.isAllManualFrameTimeout(tc)) {
					return false;
				}
			}
		}
		return true;
	}
	
	private boolean isAllManualFrameTimeout(TaskCell tc) {
		
		if(tc == null) {
			return true;
		}
		
		if(tc.getRawMsgSize() > 0 || tc.getSendMsgSize() == tc.getResMsgSize()) {
			return false;
		}
		
		if (tc.getSendMsgSize() != tc.getResMsgSize()) {
			
			synchronized(tc.getLockSendMsg()) {
				Iterator<String> it = tc.getSendMsgMap().keySet().iterator();
				while (it.hasNext()) {
					String key = it.next();
					HiSysMessage msg = tc.getSendMsgMap().get(key);
					if (!msg.isReplied() && !msg.isTimeOut()) {
						return false;
					}
				}
			}
		}
		return true;
	}
	

	
	public void setTotal()
	{
		this.total = 0;
		
		if(this.asynTaskList != null)
		{
			
			for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)
			{
				AsynTask task = this.asynTaskList.getTaskList().get(i);
				
				for(int j = 0; j < task.getTaskCellList().size(); j++)
				{
					this.total += task.getTaskCell(j).getRawMsgSize();
				}
			}			
		}
	}
	
	public void setNotify()
	{
		if(this.asynTaskList != null)
		{
			for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)
			{
				AsynTask task = this.asynTaskList.getTask(i);
				
				for(int ii = 0; ii < task.getTaskCellList().size(); ii++)
				{
					if(task.getTaskCell(ii).isNotfify() || task.getTaskCell(ii).isNotifyToServer())
					{
						this.setNotify(task.getTaskCell(ii).isNotfify());
						this.setNotifySvr(task.getTaskCell(ii).isNotifyToServer());
						break;
					}
					
				}
				
			}
		}
	}
	
	
	public AdapterMessagePair clone()
	{		
		AdapterMessagePair o  = (AdapterMessagePair)super.clone();
		
		if(this.asynTaskList != null)
		{
			AsynTaskList alClone = this.asynTaskList.clone();
			
			o.setAsynTaskList(alClone);
		}
		
		return o;
	}
	
	public void clear()
	{
		super.clear();
		
		if(this.asynTaskList != null)
		{
			this.asynTaskList.clear();
			this.asynTaskList = null;
		}
	}
	
	
	private void processManualTimeoutFrames()	{
//		boolean isNeedReTransmitted = false;
		
		int transmittedCounts = ToolUtil.getEntryConfig().getManualRetransmitTimes();
			
		for(int i = 0; i < this.asynTaskList.getTaskList().size(); i++)	{
				
			AsynTask asynTask = this.asynTaskList.getTask(i);		
			
			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {	
				
				TaskCell taskCell =  asynTask.getTaskCell(ii);
				
				/* 主动上报 */
				if(taskCell.getRawMsgSize() == 0 && 
						taskCell.getSendMsgSize() == 0 &&
						taskCell.getResMsgSize() > 0)	{
					break;
				}
				
				//snmp、cli功能不需要在这里重发
				if(CommProtocol.CP_SNMP_NEW.equals(taskCell.getProtocolType())
						|| CommProtocol.CP_SNMP.equals(taskCell.getProtocolType())
						|| CommProtocol.CP_CLI.equals(taskCell.getProtocolType())){
							continue;
						}
				
				List<HiSysMessage> timeoutMsgList= new ArrayList<HiSysMessage>();
						
				// 单条命令超时，需要重发
				synchronized(taskCell.getLockSendMsg())
				{
					Iterator<String> it = taskCell.getSendMsgMap().keySet().iterator();
					
					while(it.hasNext())	{
						String cmdId = it.next();
						HiSysMessage sendedMsg = taskCell.getSendMsgMap().get(cmdId);
					
						if(!sendedMsg.isReplied() && sendedMsg.isTimeOut())	{
//								sendedMsg.setSyncSend(false);								
							
							if(sendedMsg.getReTransmittedTimes() < transmittedCounts) {
								sendedMsg.increaseReTransmittedTimes();
								
								// avoid timeout for many times.
								sendedMsg.setBeginTime(null);
								
//								isNeedReTransmitted = true;	
								
								timeoutMsgList.add(sendedMsg);
								
								if(sendedMsg instanceof MsdhMessage) {
									FileTool.write("FrameInfo", "timeout", ((MsdhMessage)sendedMsg).getFrame());
								}else if(sendedMsg instanceof OlpMessage){
									FileTool.writeSimple("FrameInfo", "timeout", ((OlpMessage)sendedMsg).getUserData());
								}else if(sendedMsg instanceof TabsMessage){
									ManagedElement me = this.getManagedElement();
									TabsMessage msg = (TabsMessage)sendedMsg;
									String tabsAddr = msg.getTabsAddr();
									if(me != null && (tabsAddr == null || tabsAddr.isEmpty())){
										tabsAddr = me.getAddress().getTabsAddress() + "-" + me.getAddress().getNodeAddress();
									}
									FileTool.writeTabsFrame("FrameInfo","timeout", msg.getFrame(),  ToolUtil.getHostIp(), msg.getTabsAddr());
								}
							}								
							
							
						}
					}
				}			

 				synchronized(taskCell.getLockRawMsg()) {
					
					if(!timeoutMsgList.isEmpty())
					{	
						taskCell.getMsgList().addAll(0,timeoutMsgList);
					}
				}
			}
		}
	}
}
