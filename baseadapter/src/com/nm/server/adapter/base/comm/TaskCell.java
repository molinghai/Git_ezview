package com.nm.server.adapter.base.comm;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.util.ToolUtil;


public class TaskCell implements Cloneable
{
	
	private List<Object> destList = null;
	private String strID;
	private Object obj;
	private List<HiSysMessage> msgList = new ArrayList<HiSysMessage>();
	
	private Date lastFrameSendedTime = null;
	private int timeThreshold = ToolUtil.getEntryConfig().getSingleFrameTimeout() * 1000;

	private Map<String,HiSysMessage> reTransMsgMap = new TreeMap<String,HiSysMessage>();
	private Map<String,HiSysMessage> sendMsgMap = new TreeMap<String,HiSysMessage>();
	private Map<String,HiSysMessage> resMsgMap = new HashMap<String,HiSysMessage>();	
	
	private boolean isNotfify = false;
	private boolean isNotifyToServer = false;
	
	protected byte[] lockreTransMsg = new byte[0];
	protected byte[] locksendMsg = new byte[0];
	protected byte[] lockResMsg = new byte[0];
	protected byte[] lockRawMsg = new byte[0];
	
	private CommProtocol protocolType = null;

	
	
	public TaskCell()
	{
		setStrID("");
		setObj(null);				
	}
	
	public TaskCell(String strID)
	{
		setStrID(strID);
		setObj(null);				
	}	
	
	public TaskCell(String strID,List<Object>destList)
	{
		setStrID(strID);
		setDestList(destList);			
	}	
	
	public TaskCell(String strID,Object obj)
	{
		setStrID(strID);
		setObj(obj);				
	}	
	
	public TaskCell(String strID,Object obj,List<HiSysMessage> msgList)
	{
		this.setStrID(strID);
		this.setObj(obj);
		this.setMsgList(msgList);
	}
	public byte[] getLockReTransMsg()
	{
		return this.lockreTransMsg;
	}	
	public byte[] getLockSendMsg()
	{
		return this.locksendMsg;
	}

	public byte[] getLockResMsg()
	{
		return this.lockResMsg;
	}
	
	public byte[] getLockRawMsg()
	{
		return this.lockRawMsg;
	}	
	
	public void setStrID(String strID)
	{
		this.strID = strID;
	}

	public void setDestList(List<Object> destList)
	{
		if(this.destList == null) {
			this.destList = new ArrayList<Object>();
		}
		
		this.destList.addAll(destList);
	}

	public List<Object> getDestList()
	{
		return destList;
	}

	public String getStrID()
	{
		return strID;
	}

	public void setObj(Object obj)
	{
		this.obj = obj;
	}

	public Object getObj()
	{
		return obj;
	}

	public void setMsgList(List<HiSysMessage> msgList)
	{
		this.msgList = msgList;
	}

	public List<HiSysMessage> getMsgList()
	{
		return msgList;
	}
	
	public void setLastFrameSendedTime(Date lastFrameSendedTime) {
		this.lastFrameSendedTime = lastFrameSendedTime;
	}

	public Date getLastFrameSendedTime() {
		return lastFrameSendedTime;
	}

	public void addMsg(HiSysMessage msg)
	{
		synchronized(this.lockRawMsg)
		{
			this.msgList.add(msg);
		}
		
	}
	
	public void addMsgList(List<HiSysMessage> msgList)
	{
		synchronized(this.lockRawMsg)
		{
			this.msgList.addAll(msgList);
		}		
	}
	
	public void removeMsgList(List<HiSysMessage> msgList)
	{
		synchronized(this.lockRawMsg)
		{
			for(HiSysMessage msg:msgList)
			{
				this.msgList.remove(msg);
			}			
		}
	}
	
	public HiSysMessage getMsg(int index)
	{
		synchronized(this.lockRawMsg)
		{		
			return this.msgList.get(index);
		}
	}

	public void setSendMsgMap(Map<String,HiSysMessage> sendMsgMap)
	{
		this.sendMsgMap = sendMsgMap;
	}

	public Map<String,HiSysMessage> getSendMsgMap()
	{
		return sendMsgMap;
	}
	
	public void addSendMsg(String synchronizedId,HiSysMessage msg)
	{
		synchronized(this.locksendMsg)
		{
			this.sendMsgMap.put(synchronizedId, msg);
		}		
	}
	
	public void removeSendMsg(String synchronizedId)
	{
		synchronized(this.locksendMsg)
		{
			this.sendMsgMap.remove(synchronizedId);
		}	
		
	}
	
	public Map<String,HiSysMessage> getReTransMsgMap()
	{
		return reTransMsgMap;
	}
	
	public void addReTransMsg(String synchronizedId,HiSysMessage msg)
	{
		synchronized(this.lockreTransMsg)
		{
			this.reTransMsgMap.put(synchronizedId, msg);
		}		
	}
	
	public void removeReTransMsg(String synchronizedId)
	{
		synchronized(this.lockreTransMsg)
		{
			this.reTransMsgMap.remove(synchronizedId);
		}	
		
	}	
	
	public HiSysMessage getReTransMsg(String synchronizedId)
	{
		synchronized(this.lockreTransMsg)
		{		
			return this.reTransMsgMap.get(synchronizedId);
		}
	}	
	
	
	public void setResMsgMap(Map<String,HiSysMessage> resMsgMap)
	{
		this.resMsgMap = resMsgMap;		
	}

	public Map<String,HiSysMessage> getResMsgMap()
	{		
		return resMsgMap;
	}
	
	public void addResMsg(String synchronizedId,HiSysMessage msg)
	{
		synchronized(this.lockResMsg)
		{		
			this.resMsgMap.put(synchronizedId, msg);
		}
	}	
	
	public HiSysMessage getResMsg(String synchronizedId)
	{
		synchronized(this.lockResMsg)
		{		
			return this.resMsgMap.get(synchronizedId);
		}
	}
	
	
	public List<HiSysMessage> getResMsgList()
	{
		List<HiSysMessage> resList = new ArrayList<HiSysMessage>();
		
		Iterator<String> it = this.resMsgMap.keySet().iterator();
		
		while(it.hasNext())
		{
			String key = it.next();
			
			HiSysMessage msg = this.resMsgMap.get(key);
			
			if(msg != null)
			{
				resList.add(msg);
			}
		}
		
		
		return resList;
	}

	public void setNotfify(boolean isNotfify)
	{
		this.isNotfify = isNotfify;
	}

	public boolean isNotfify()
	{
		if(obj != null && obj instanceof ResFunc){
			return ((ResFunc)obj).isNotify();
		}
		return this.isNotfify;
	}
		
	/**
	 * @param isNotifyToServer the isNotifyToServer to set
	 */
	public void setNotifyToServer(boolean isNotifyToServer)
	{
		this.isNotifyToServer = isNotifyToServer;
	}

	/**
	 * @return the isNotifyToServer
	 */
	public boolean isNotifyToServer()
	{
		if(obj != null && obj instanceof ResFunc){
			return ((ResFunc)obj).isNotifyToServer();
		}
		return isNotifyToServer;
	}

	public boolean isResponsed()
	{
		return this.sendMsgMap.size() == this.resMsgMap.size();
	}
	
	public TaskCell clone()
	{
		TaskCell o = null;
		
		try
		{
			o = (TaskCell)super.clone();
			
			
			if(this.destList != null)
			{
				o.destList = new ArrayList<Object>();
				
				for(Object obj:destList)
				{
					if(obj instanceof DeviceDest)
					{
						DeviceDest destClone = ((DeviceDest)obj).clone();
						o.getDestList().add(destClone);
					}
				}
			}
			
			
			
			if(this.msgList != null)
			{
				o.msgList = new ArrayList<HiSysMessage>();
				
				for(HiSysMessage msg:msgList)
				{
					HiSysMessage msgClone = msg.clone();
					o.addMsg(msgClone);
				}
			}
			
			if(this.sendMsgMap != null)
			{
				o.sendMsgMap = new HashMap<String,HiSysMessage>();
				
				Iterator<String> it = this.sendMsgMap.keySet().iterator();
				
				while(it.hasNext())
				{
					String key = it.next();
					HiSysMessage msg = this.sendMsgMap.get(key);
					
					o.sendMsgMap.put(key, msg.clone());
				}
			}
			
			if(this.resMsgMap != null)
			{
				o.resMsgMap = new HashMap<String,HiSysMessage>();
				
				Iterator<String> it = this.resMsgMap.keySet().iterator();
				
				while(it.hasNext())
				{
					String key = it.next();
					HiSysMessage msg = this.resMsgMap.get(key);
					
					o.resMsgMap.put(key, msg.clone());
				}
			}
			
			if(this.obj != null)
			{
				ResFunc resFunc = (ResFunc) ((ResFunc)this.obj).clone();
				
				o.setObj(resFunc);
			}
			
		}
		catch (CloneNotSupportedException e)
		{
			System.out.println("TaskCell clone exception.");
		}
		
		
		return o;
		
	}
	public void clear()
	{
		if(this.destList != null)
		{
			this.destList.clear();
			this.destList = null;
		}
		
		if(this.obj != null)
		{
			this.obj = null;
		}
		
		synchronized(this.lockRawMsg)
		{
			if(this.msgList != null)
			{
				for(HiSysMessage msg : msgList) {
					msg.clear();
				}
				
				this.msgList.clear();
				this.msgList = null;
			}
		}		

		synchronized(this.locksendMsg)
		{		
			if(this.sendMsgMap != null)
			{
				Iterator<String> it = this.sendMsgMap.keySet().iterator();
				while(it.hasNext()) {
					String key = it.next();
					this.sendMsgMap.get(key).clear();
					it.remove();
				}
				this.sendMsgMap.clear();
				this.sendMsgMap = null;
			}
		}
		
		synchronized(this.lockreTransMsg)
		{
			if(this.reTransMsgMap != null)
			{
				Iterator<String> it = this.reTransMsgMap.keySet().iterator();
				while(it.hasNext()) {
					String key = it.next();
					this.reTransMsgMap.get(key).clear();
					it.remove();
				}
				this.reTransMsgMap.clear();
				this.reTransMsgMap = null;
			}
		}
		
		synchronized(this.lockResMsg)
		{		
			if(this.resMsgMap != null)
			{
//				Iterator<String> it = this.resMsgMap.keySet().iterator();
//				while(it.hasNext()) {
//					String key = it.next();
//					this.resMsgMap.get(key).clear();
//					it.remove();
//				}
				this.resMsgMap.clear();
				this.resMsgMap = null;
			}		
		}

	}

	public boolean equals(Object o)
	{
		if(o == null)
		{
			return false;
		}
		
		TaskCell tc = (TaskCell)o;
		
		if(this.strID.equals(tc.strID) && 
				this.isNotfify == tc.isNotfify && 
				this.isNotifyToServer == tc.isNotifyToServer &&
				this.msgListEquals(tc))
		{
			return true;
		}
		
		
		return false;
	}
	
	private boolean msgListEquals(TaskCell tc)
	{
		List<HiSysMessage> lList = this.getMsgList();
		List<HiSysMessage> tcList = tc.getMsgList();
	
		if(lList.size() == tcList.size())
		{
			for(HiSysMessage msg:lList)
			{
				if(!this.msgInList(msg, tcList))
				{
					return false;
				}
			}
			
			return true;
		}		
		
		return false;
	}
	
	private boolean msgInList(HiSysMessage msg,List<HiSysMessage> msgList)
	{
		
		if(msg == null || msgList == null)
		{
			return false;
		}
		
		
		for(HiSysMessage tcMsg:msgList)
		{
			if(msg.equals(tcMsg))
			{
				return true;
			}
		}
		
		
		return false;
	}
	
	public boolean isLastFrameSendedTimeOut()
	{
		if(this.lastFrameSendedTime != null)
		{
			java.util.Date now = new java.util.Date();		
			
			return (now.getTime() - this.lastFrameSendedTime.getTime() - 10 > timeThreshold ? true:false);		
		}
		
		
		return false;
	}
	
	public int getRawMsgSize() {
//		synchronized(this.getLockRawMsg()) {
			return this.getMsgList().size();
//		}
	}
	
	public int getSendMsgSize() {
		synchronized(this.getLockSendMsg()) {
			return this.getSendMsgMap().size();
		}
	}
	
	public int getResMsgSize() {
		synchronized(this.getLockResMsg()) {
			return this.getResMsgMap().size();
		}
	}
	
	public int getReTransMsgSize() {
		synchronized(this.getLockReTransMsg()) {
			return this.getReTransMsgMap().size();
		}
	}

	public CommProtocol getProtocolType() {
		return protocolType;
	}

	public void setProtocolType(CommProtocol protocolType) {
		this.protocolType = protocolType;
	}



	
	
}
