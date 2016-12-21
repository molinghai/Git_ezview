package com.nm.server.adapter.base.sm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.error.ErrorMessageTable;
import com.nm.message.CsCommandCollection;
import com.nm.message.CsMessageConverter;
import com.nm.message.Response;
import com.nm.message.ResponseCollection;
import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.MonitorLinkType;
import com.nm.nmm.service.EthGateManager;
import com.nm.nmm.service.TopoManager;
import com.nm.nmm.util.Helper;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.AsynTaskList;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.base.comm.OlpMessage;
import com.nm.server.adapter.base.comm.SnmpMessage;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.adapter.base.comm.TabsMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.base.comm.UdpMessage;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePair;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.TaskResult;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class TaskManager
{
	private static final Log log = LogFactory.getLog(TaskManager.class);	
	
	private static TaskManager instance = new TaskManager();
	
	private ClientMessageListener msgListener;
	
	private SocketMgrServer socketMgr;
	private TopoManager topoManager;
	private EthGateManager ethGateManager;
	
	java.util.Date startTime = null;
	int timethreshold = 0;
	
	private String adapterName;
	
	private Map<Integer,ManagedElement> neMap;	

	public static TaskManager getInstance()
	{
		if(instance == null)
		{
			instance = new TaskManager();
		}
		
		return instance;
	}
	
	public void setMsgListener(ClientMessageListener msgListener)
	{
		this.msgListener = msgListener;
	}

	public void setSocketMgr(SocketMgrServer socketMgr)
	{
		this.socketMgr = socketMgr;
	}
	
	public void setTopoManager(TopoManager topoManager)
	{
		this.topoManager = topoManager;
	}

	
	public void setEthGateManager(EthGateManager ethGateManager)
	{
		this.ethGateManager = ethGateManager;
	}
	
	public void setAdapterName(String adapterName) {
		this.adapterName = adapterName;
	}

	public String getAdapterName() {
		return adapterName;
	}

	public void setNeMap(Map<Integer,ManagedElement> neMap) {
		this.neMap = neMap;
	}

	public ManagedElement getMangedElement(int neId) {
		synchronized(this.neMap) {
			ManagedElement me =  this.neMap.get(neId);
			
			return me != null ? me.clone() : null;
		}
	}
	
	private boolean isTimeout()
	{
		java.util.Date now = new java.util.Date();		
		
		return (now.getTime() - this.startTime.getTime() > timethreshold ? true:false);		
	}
	
	private SocketChannel connet(String strIp,int iPort)
	{			
		
		try
		{
			
			SocketChannel sc = SocketChannel.open();
			sc.configureBlocking(false);
			sc.connect(new InetSocketAddress(InetAddress.getByName(strIp), iPort));			
			
			while(!sc.finishConnect())
			{
				if(this.isTimeout())
				{
					return null;
				}
			}
			
			return sc;
		}
		catch (IOException e)
		{
			log.error("socket connect exception:",e);
		}		
		
		return null;
	}
	
	private void sendMsg(SocketChannel sc,AdapterMessagePair mp)
	{
		AsynTaskList asynTaskList = mp.getAsynTaskList();
		
		if(asynTaskList != null)
		{
			int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
			
			for(int i = 0; i < asynTaskList.getTaskList().size(); i++)
			{
				AsynTask task = asynTaskList.getTaskList().get(i);
				
				for(int j = 0; j < task.getTaskCellList().size(); j++)
				{
					TaskCell taskCell = task.getTaskCellList().get(j);
					
					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();
					
					for(int k = 0; k < taskCell.getMsgList().size(); k++)
					{
						HiSysMessage msg = taskCell.getMsg(k);
						
						MsdhMessage frame = (MsdhMessage)msg;
						
						sendedMsgList.add(frame);
						
						frame.buildFrame(EthGate.getNextMsgSequenceNumber());
						
						// add sent message into sendMsgMap
						taskCell.addSendMsg(frame.getSynchnizationId(true), frame);

						byte[] frameB = frame.getFrame();
						
						ByteBuffer bb = ByteBuffer.wrap(frameB);
						try
						{
							sc.write(bb);
						}
						catch (IOException e)
						{
							log.error("TaskManager sendMessage exception:", e);
						}
						
						if(frameOn == 1)
						{
							FileTool.write("FrameInfo", "Command.txt", frameB);
						}
						
													
					}
					taskCell.getMsgList().removeAll(sendedMsgList);
				}
				
			
			}
		}			
	}
	private void sendMsg(SocketChannel sc,AdapterMessagePair mp, byte[] srcAddr,byte[] destAddr)
	{
		AsynTaskList asynTaskList = mp.getAsynTaskList();
		
		if(asynTaskList != null)
		{
			int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
			
			for(int i = 0; i < asynTaskList.getTaskList().size(); i++)
			{
				AsynTask task = asynTaskList.getTaskList().get(i);
				
				for(int j = 0; j < task.getTaskCellList().size(); j++)
				{
					TaskCell taskCell = task.getTaskCellList().get(j);
					
					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();
					
					for(int k = 0; k < taskCell.getMsgList().size(); k++)
					{
						HiSysMessage msg = taskCell.getMsg(k);
						
						MsdhMessage frame = (MsdhMessage)msg;
						
						sendedMsgList.add(frame);
						
						frame.setSrcAddress(srcAddr);
						frame.setDstAddress(destAddr);	
						
						frame.buildFrame(EthGate.getNextMsgSequenceNumber());
						
						// add sent message into sendMsgMap
						taskCell.addSendMsg(frame.getSynchnizationId(true), frame);

						byte[] frameB = frame.getFrame();
						
						ByteBuffer bb = ByteBuffer.wrap(frameB);
						try {
							sc.write(bb);
							frame.setBeginTime(new Date());
							frame.increaseReTransmittedTimes();
							log.info("Send Time : " + frame.getBeginTime());
						} catch (IOException e) {
							log.error("TaskManager sendMessage exception:", e);
						}
						
						if (frameOn == 1) {
							FileTool.write("FrameInfo", "Command", frameB);
						}
						
													
					}
					taskCell.getMsgList().removeAll(sendedMsgList);
				}
				
			
			}
		}			
	}
	
	private int reply(SocketChannel sc,AdapterMessagePair mp)
	{
		ByteBuffer readBuffer = ByteBuffer.allocate(4096000);
		
		int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();

		try
		{
			while(true)
			{
				if(this.isTimeout())
				{
					log.info("reply time out!");
					
					return 105;
				}				
				
				sc.read(readBuffer);
			

			
				int dataLen = readBuffer.capacity() - readBuffer.remaining();
				
				byte[] array = readBuffer.array();

				int i = 0;
				for (; i < dataLen; i++)
				{
					if (array[i] == (byte) 0x96 && (i + 17 < dataLen))
					{
						byte[] len = { array[16], array[17] };
						short length = Helper.unsignedByteToShort(len);
						MsdhMessage msdh = new MsdhMessage(array, i, i + 18 + length + 4);
						if (null != msdh.getFixedData()) {
							mp.reply(msdh);
							i += 18 + length - 1;
							
							if (frameOn == 1) {
								FileTool.write("FrameInfo", "Response", msdh.getFrame());
							}
						}
					}
				}
				if (i < dataLen)
				{
					byte[] remain = new byte[dataLen - i];
					System.arraycopy(array, i, remain, 0, dataLen - i);
					readBuffer.clear();
					readBuffer.put(remain);
				}
				else
				{
					readBuffer.clear();
				}
				
				if(mp.isRepliedAllFromNe())
				{
					break;
				}
			}
			
			sc.close();			
		}		

		catch (IOException e)
		{
			log.error("read socket data or close socket error!");
		}	
		
		return 0;
		
	}
	
	private int reply(SocketChannel sc, AdapterMessagePair mp, TaskResult tRes) {
		ByteBuffer readBuffer = ByteBuffer.allocate(4096000);

		int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();

		try {
			while (true) {
				if (this.isTimeout()) {
					log.info("reply time out!");
					log.info("Time out : " + new Date());
					tRes.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
					return tRes.getResult();
				}

				sc.read(readBuffer);

				int dataLen = readBuffer.capacity() - readBuffer.remaining();

				byte[] array = readBuffer.array();

				int i = 0;
				for (; i < dataLen; i++) {
					if (array[i] == (byte) 0x96 && (i + 17 < dataLen)) {
						byte[] len = { array[16], array[17] };
						short length = Helper.unsignedByteToShort(len);
						MsdhMessage msdh = new MsdhMessage(array, i, i + 18 + length + 4);
						if (null != msdh.getFixedData()) {
							mp.reply(msdh);
							i += 18 + length - 1;

							if (frameOn == 1) {
								FileTool.write("FrameInfo", "Response", msdh.getFrame());
							}
						}
					}
				}
				if (i < dataLen) {
					byte[] remain = new byte[dataLen - i];
					System.arraycopy(array, i, remain, 0, dataLen - i);
					readBuffer.clear();
					readBuffer.put(remain);
				} else {
					readBuffer.clear();
				}

				if (mp.isRepliedAllFromNe()) {
					break;
				}
				tRes.setResult(mp.getErrCode());
			}
			sc.close();
		}

		catch (IOException e) {
			log.error("read socket data or close socket error!");
		}

		return tRes.getResult();

	}
	
	
	protected int sendAndRevMsg(String strIp,int iPort,AdapterMessagePair mp)
	{
		
		if(mp == null)
		{
			return -1;
		}
				
		this.timethreshold = mp.getTimeThreshold();	
		
		this.startTime = new java.util.Date();
		
		SocketChannel sc = this.connet(strIp,iPort);
		
		if(sc == null)
		{				
			log.error("socket connect failure!");
			
			return this.sendAndRevMsg(mp);
		}
		
		this.sendMsg(sc, mp);
		
		return this.reply(sc, mp);
		
	}
	
	public int sendAndRevMsgThenClose(HiSysMessage msg, int neId, int timeout, TaskResult tRes) {
		
		this.setMsgExInfo(msg, neId);
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);	
		
		ManagedElement me = this.getMangedElement(neId);
		mp.setManagedElement(me);
		
		mp.setTotal();

		this.timethreshold = mp.getTimeThreshold();

		this.startTime = new java.util.Date();

		int gwId = me.getEthGatewayInfo().getWorkGatewayNeId();
		ManagedElement gwMe = this.getMangedElement(gwId);
		
		String scIp = gwMe.getAddress().getIpAddressStr();

		SocketChannel sc = this.connet(scIp, 3000);

		if (sc == null) {
			log.error("socket connect failure!");
			return -1;
		}
		
		String destIP = ToolUtil.longToIp(me.getAddress().getIpAddress());			
		
		byte [] srcAddr = sc.socket().getLocalAddress().getAddress();
		byte [] destAddr = ToolUtil.ipStringToBytes(destIP);

		this.sendMsg(sc, mp, srcAddr, destAddr);

		return this.reply(sc, mp, tRes);
	}
	
	protected void sendMsg(AdapterMessagePair mp)
	{
		mp.setIsSend(true);			
		
		AsynTaskList asynTaskList = mp.getAsynTaskList();
		
		for(AsynTask asynTask:asynTaskList.getTaskList())
		{			
//			CommandDestination dest = asynTask.getCommand().getDest();
//			
//			if(dest == null)
//			{			
//    			for(CommandParameter cp:asynTask.getCommand().getParamSet())
//    			{    				
//    				dest = cp.getDest();
//    				break;
//    			}
//			}
			
			DeviceDest dest = asynTask.getDeviceDest();			
			
			int neId = dest.getNeId();
		
    		ManagedElement me = topoManager.getManagedElementById(neId);       		
    		
    		
    		// TCP
    		if (me.getMonitorLinkType() == MonitorLinkType.MLT_ETH)
    		{    			
    			
    			
    			int ethId = neId;
    			
    			if(!me.isServeAsEthGateway())
    			{
    				ethId = me.getEthGatewayInfo().getWorkGatewayNeId();
    			}
    			
    			EthGate ethGate = this.ethGateManager.getEthGate(ethId);
    			
    			String strIp = ToolUtil.longToIp(ethGate.getIpAddress());
    
    			SocketChannel sc = null;
				sc = this.socketMgr.getSocketChannel(ethGate);

				
				
				if (null == sc)
				{
					log.error("Can not find socket channel for " + strIp);
					
					mp.setErrCode(ErrorMessageTable.COMM_PORT_NOEXISTS);				
					
					return;
				}
				
				int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
				
				
				byte[] srcAddr = ToolUtil.ipStringToBytes(ToolUtil.getHostIp());
				byte[] destAddr = ToolUtil.intToByteArray(true, (int)me.getAddress().getIpAddress());    		
		
    			    
    			for(TaskCell taskCell:asynTask.getTaskCellList())
				{
					List<HiSysMessage> msgList = taskCell.getMsgList();
				
					Iterator<HiSysMessage> msgIt = msgList.iterator();
					
					while(msgIt.hasNext())  
    				{
    					MsdhMessage frame = (MsdhMessage)msgIt.next();
    					
    					frame.setSrcAddress(srcAddr);
    					frame.setDstAddress(destAddr);
    					

    					synchronized (ethGate)
    					{
    						try 
    						{
								Thread.sleep(3);
							} 
    						catch (InterruptedException e1) 
    						{
								e1.printStackTrace();
							}
    						
    						
    						
    						byte[] frameB = frame.buildFrame(EthGate.getNextMsgSequenceNumber());
    						
    						// add sent message into sendMsgMap
    						taskCell.addSendMsg(frame.getSynchnizationId(true), frame);   						
    						
    						ByteBuffer bb = ByteBuffer.wrap(frameB);
    						try
    						{
    							sc.write(bb);
    							
        						// remove message from msgList
        						msgIt.remove();
    						}
    						catch (IOException e)
    						{        						
        						// remove sent message from sendMsgMap
        						taskCell.removeSendMsg(frame.getSynchnizationId(true));            						
        						
    							log.error("write data to socket error!");
    							
    							
    							mp.setErrCode(ErrorMessageTable.COMM_WIRTE_DATA_ERROR); 							
    							
    							
    							try
								{
									this.socketMgr.remove(sc);
								}
								catch (Exception e1)
								{
									log.error("remove sockectChannel error!");
								}
    							
    						}
    						
    						if(frameOn == 1)
    						{
    							FileTool.write("FrameInfo", "Command.txt", frameB);
    						}
    						
    						
    						
    					}

    				}
				}
			
    		}

		}		
	
	}
	
	
	private int reply(MessagePool msgPool,AdapterMessagePair mp)
	{
		int rtn = 0;
		
		
		while(true)
		{
			
			try
			{
				Thread.sleep(1);
			}
			catch (InterruptedException e)
			{
				log.error("TaskManager reply exception:", e);
			}			
			
			if(msgPool.getSyncMessagePair(mp)!= null)
			{
				log.info("reply return");
				
				
				if(mp.isTimeout())
				{
					log.info("response timeout!");
					
					rtn = 105;
				}
				else if(mp.isErr())
				{
					log.info("communication error!");
					rtn = mp.getErrCode();
				}
				
				
				break;				
			}
		}		
		
		return rtn;
		
	}
	
	private int sendAndRevMsg(AdapterMessagePair mp)
	{		
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null && mp != null)
		{
			msgPool.insertMessagePair(mp);
		}
		else
		{
			return -1;
		}
		
		this.sendMsg(mp);		
		return this.reply(msgPool,mp);
		
		
	}
	
	protected List<HiSysMessage> sendAndRevMsg(List<HiSysMessage> msgList,int neId,int timeout)
	{
		TaskCell taskCell = new TaskCell();
		taskCell.addMsgList(msgList);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);	
		
		mp.setManagedElement(this.getMangedElement(neId));
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.insertMessagePair(mp);
		}
		else
		{
			return null;
		}
		
		this.sendMsg(mp);		
		if(this.reply(msgPool,mp) == 0)
		{			
			return mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList();	

		}
		
		
		return null;
		
	}
	public HiSysMessage sendAndRevMsg(HiSysMessage msg,int neId,int timeout)
	{
		this.setMsgExInfo(msg, neId);
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
			taskCell.setProtocolType(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);		
		
		mp.setManagedElement(this.getMangedElement(neId));
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.addUnSendSyncMsg(mp);
		}
		else
		{
			return null;
		}
		
		mp = this.getSyncMessagePair(mp);
		
		if(mp != null)
		{			
			List<HiSysMessage> resMsgList = mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList();
			
			if(resMsgList.size() > 0)
			{
				HiSysMessage rtnMsg =  resMsgList.get(0);
				
				resMsgList.clear();
				mp.clear();
				mp = null;
				
				return rtnMsg;
				
			}
			
			

		}
		
		
		return null;			
		
	}
	
	public HiSysMessage sendAndRevMsg(HiSysMessage msg,int neId,String ipAddr,int timeout)
	{
		this.setMsgExInfo(msg, neId);
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);		
		
		ManagedElement me = this.getMangedElement(neId);
		if(me != null) {
			me.getAddress().setIpAddress(ToolUtil.ipToLong(ipAddr));
		}
		mp.setManagedElement(me);
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.addUnSendSyncMsg(mp);
		}
		else
		{
			return null;
		}
		
		mp = this.getSyncMessagePair(mp);
		
		if(mp != null)
		{			
			List<HiSysMessage> resMsgList = mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList();
			
			if(resMsgList.size() > 0)
			{
				HiSysMessage rtnMsg =  resMsgList.get(0);
				
				resMsgList.clear();
				mp.clear();
				mp = null;
				
				return rtnMsg;
				
			}
			
			

		}
		
		
		return null;			
		
	}	
	
	public HiSysMessage sendAndRevMsg(HiSysMessage msg,ManagedElement me,int timeout)
	{
		this.setMsgExInfo(msg, me.getId());
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(me.getId());
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);		
		
		mp.setManagedElement(me);
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.addUnSendSyncMsg(mp);
		}
		else
		{
			return null;
		}
		
		mp = this.getSyncMessagePair(mp);
		
		if(mp != null)
		{			
			List<HiSysMessage> resMsgList = mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList();
			
			if(resMsgList.size() > 0)
			{
				HiSysMessage rtnMsg =  resMsgList.get(0);
				
				resMsgList.clear();
				mp.clear();
				mp = null;
				
				return rtnMsg;
				
			}
			
			

		}
		
		
		return null;			
		
	}	
	
	public HiSysMessage sendAndRevMsg(HiSysMessage msg,int neId,int timeout,TaskResult tRes)
	{
		
		
		this.setMsgExInfo(msg, neId);
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof TabsMessage){
			mp.setProtocol(CommProtocol.CP_TABS);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);	
		
		mp.setManagedElement(this.getMangedElement(neId));
		
		mp.setTotal();
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.addUnSendMsg(mp);
		}
		else
		{
			
			log.error("message pool is null.");
			
			tRes.setResult(ErrorMessageTable.BADPARAM);
			
			return null;
		}
		

		mp = this.getSyncMessagePair(mp);
			
		if(mp != null && !mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().isEmpty()&& !mp.isErr() && !mp.isTimeout())
		{			
			return mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().get(0);
		}
		else
		{
			
			if(mp.isErr())
			{
				tRes.setResult(mp.getErrCode());
			}
			else
			{
				tRes.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
		}
		
		
		return null;		
		
	}	
	
	public HiSysMessage sendAndRevMsg(
			HiSysMessage msg,int neId, String ipAddr, int timeout,TaskResult tRes)
	{
		
		
		this.setMsgExInfo(msg, neId);
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);	
		
		ManagedElement me = this.getMangedElement(neId);
		if(me != null) {
			me.getAddress().setIpAddress(ToolUtil.ipToLong(ipAddr));
		}
		mp.setManagedElement(me);
		
		mp.setTotal();
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.addUnSendMsg(mp);
		}
		else
		{
			
			log.error("message pool is null.");
			
			tRes.setResult(ErrorMessageTable.BADPARAM);
			
			return null;
		}
		

		mp = this.getSyncMessagePair(mp);
			
		if(mp != null && !mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().isEmpty()&& !mp.isErr() && !mp.isTimeout())
		{			
			return mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().get(0);
		}
		else
		{
			
			if(mp.isErr())
			{
				tRes.setResult(mp.getErrCode());
			}
			else
			{
				tRes.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
		}
		
		
		return null;		
		
	}		
	
	public HiSysMessage sendAndRevMsg(HiSysMessage msg,ManagedElement me,int timeout,TaskResult tRes)
	{
		
		
		this.setMsgExInfo(msg, me.getId());
		
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(me.getId());
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		
		if(msg instanceof SnmpMessage)
		{
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof TabsMessage){
			mp.setProtocol(CommProtocol.CP_TABS);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);	
		
		mp.setManagedElement(me);
		
		mp.setTotal();
		
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)
		{
			msgPool.addUnSendMsg(mp);
		}
		else
		{
			
			log.error("message pool is null.");
			
			tRes.setResult(ErrorMessageTable.BADPARAM);
			
			return null;
		}
		

		mp = this.getSyncMessagePair(mp);
			
		if(mp != null && !mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().isEmpty()&& !mp.isErr() && !mp.isTimeout())
		{			
			return mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().get(0);
		}
		else
		{
			
			if(mp.isErr())
			{
				tRes.setResult(mp.getErrCode());
			}
			else
			{
				tRes.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
		}
		
		
		return null;		
		
	}	
	
	private void setMsgExInfo(HiSysMessage msg,long neId)
	{
		if(msg != null && msg instanceof MsdhMessage)
		{
			MsdhMessage msgL = (MsdhMessage)msg;
			
			byte[] userDataB = msgL.getFixedData();
			
				
			
			if(userDataB != null)
			{
//				ManagedElement me = this.topoManager.getManagedElementById((int)neId);
				
//				String destIP = ToolUtil.longToIp(me.getAddress().getIpAddress());			
//				
//				byte [] srcAddr = ToolUtil.ipStringToBytes(ToolUtil.getHostIp());
//				byte [] destAddr = ToolUtil.ipStringToBytes(destIP);
				
				
				if(userDataB[0] == (byte)0xFF)
				{
					msgL.setHeadCtrlBit(1, (byte)0x20);
					
					if(userDataB[0] == (byte)0xFF)
					{
						byte[] userDataT = userDataB;
						userDataB = new byte[userDataT.length - 1];
						
						System.arraycopy(userDataT, 1, userDataB, 0, userDataB.length);
					}
				}
				
//				msgL.setSrcAddress(srcAddr);
//				msgL.setDstAddress(destAddr);
				
	    		try
	    		{
	    			msgL.setFixedData(userDataB,userDataB.length);
	    		}
	    		catch (Exception e)
	    		{
	    			log.error("set fixed data exception:", e);
	    		}

				/*support tabs*/
				ManagedElement me = this.getMangedElement((int)neId);
				int tabs = me.getAddress().getTabsAddress();
				
				if(tabs > 0) {
					byte headCtrl = (byte)0x10;
					msgL.setHeadByte(1, (byte)(headCtrl | msgL.getHeadByte(1)));
				}
			}
		}
		

	}
	
	public HiSysMessage sendAndRevMsg(
			HiSysMessage msg,int neId,int timeout,boolean transmit,TaskResult tRes)	{
		
		this.setMsgExInfo(msg, neId);
		AdapterMessagePair msgPair = this.getMsgPair(msg, neId, timeout, transmit);
		this.sendMsgPair(msgPair, tRes);
		return this.getResMsg(msgPair, tRes);
	}	
	
	
	private AdapterMessagePair getMsgPair(HiSysMessage msg,int neId,int timeout,boolean transmit) {
		TaskCell taskCell = new TaskCell();
		taskCell.addMsg(msg);
		
		DeviceDest deviceDest = new DeviceDest();
		deviceDest.setNeId(neId);
		
		AsynTask task = new AsynTask(deviceDest);
		task.addTaskCell(taskCell);
		
		AsynTaskList taskList = new AsynTaskList();
		taskList.addTask(task);
		
		AdapterMessagePair mp = new AdapterMessagePair();
		mp.setSupportTransmit(transmit);
		
		if(msg instanceof SnmpMessage) {
			mp.setProtocol(CommProtocol.CP_SNMP);
		}else if(msg instanceof SnmpNewMessage){
			mp.setProtocol(CommProtocol.CP_SNMP_NEW);
		}else if(msg instanceof OlpMessage){
			mp.setProtocol(CommProtocol.CP_OLP);
		}else if(msg instanceof UdpMessage){
			mp.setProtocol(CommProtocol.CP_UDP);
		}
		
		mp.setSync(true);
		mp.setTimeThreshold(timeout);
		mp.setAsynTaskList(taskList);	
		
		mp.setManagedElement(this.getMangedElement(neId));
		mp.setTotal();
		return mp;
	}
	
	private void sendMsgPair(AdapterMessagePair mpL,TaskResult tRes) {
		MessagePool msgPool  = this.msgListener.getMsgPool();
		
		if(msgPool != null)	{
			msgPool.addUnSendMsg(mpL);
		} else {
			log.error("message pool is null.");
			tRes.setResult(ErrorMessageTable.BADPARAM);
		}
	}
	
	private HiSysMessage getResMsg(AdapterMessagePair mpL,TaskResult tRes) {
		
		if(tRes.getResult() != 0) {
			return null;
		}
		
		AdapterMessagePair mp = this.getSyncMessagePair(mpL);
		
		if(mp != null 
				&& !mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().isEmpty()
				&& !mp.isErr() && !mp.isTimeout())	{			
			return mp.getAsynTaskList().getTask(0).getTaskCell(0).getResMsgList().get(0);
		} else {
			if(mp.isErr()) {
				tRes.setResult(mp.getErrCode());
			} else {
				tRes.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
			return null;
		}
	}
	
	
	private AdapterMessagePair getSyncMessagePair(AdapterMessagePair mp)
	{
		AdapterMessagePair mpL = null;
		
		while(true && this.msgListener.getMsgPool() != null)
		{
			mpL = (AdapterMessagePair) this.msgListener.getMsgPool().getSyncMsg(mp);
			
						
			if(mpL != null)
			{
				break;
			}	
			
			try
			{
				Thread.sleep(10);
			}
			catch (Exception e)
			{
				log.error("sleep exception:", e);
			}
			
		}
		
		return mpL;
	}
	
	public ResponseCollection sendAndRcvLocMsg(CsCommandCollection ccc,boolean isNotify)
	{
		
		try
		{
			if(this.msgListener != null)
			{				
				
				ResponseCollection rc = CsMessageConverter.cccToRc(ccc);
				
				AdapterMessagePair mp = new AdapterMessagePair();
				
				mp.setCmds(ccc);
				mp.setResponseCollection(rc);
				mp.setSync(true);
				
				// send
				this.msgListener.getMsgPool().newRawMessage(mp);
				
				
				MessagePair mpL = this.getSyncMessagePair(mp);
				
					
				if(mpL != null)
				{
					
					int rtn = 0;
					if (mp.isErr() ||  ErrorMessageTable.PART_SUCCESS == mp.getErrCode()) {
						rtn = mp.getErrCode();
					} else if (mp.isCancel()) {
						rtn = 2;
					} else if (mp.isTimeout()) {
						rtn = 111;
					}
					
					if (rtn != 0) {
						List<Response> resList = mpL.getResponseCollection().getResponseList();
						for (int i = 0; resList != null && i < resList.size(); i++) {
							resList.get(i).setResult(rtn);
						}
					}
					
					if (isNotify) {
						SyncNotifyManager.getInstance().notifyToAll(
								mpL.getResponseCollection());
					}
					
					return mpL.getResponseCollection();
				}
				
			}
		}
		catch(Exception e)
		{
			log.error("get sync message exception:",e);
		}		
		
		return null;
	}	
}
