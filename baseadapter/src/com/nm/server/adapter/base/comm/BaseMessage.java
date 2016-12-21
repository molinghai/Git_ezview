package com.nm.server.adapter.base.comm;

import java.util.Date;

import org.apache.commons.logging.LogFactory;

import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.util.ToolUtil;


public abstract class BaseMessage implements HiSysMessage
{
	
	private HiMsgType msgType = null;
	private Date beginTime = null;
	private int timeThreshold = ToolUtil.getEntryConfig().getSingleFrameTimeout() * 1000;
	private int reTransmittedTimes = 0;
	private boolean isSend = false;
	private boolean isSyncSend = false;
	private boolean isReplied = false;
	protected String neType;
	private String neVersion;
	protected int frameInterval = 0;
	
	public void setMsgType(HiMsgType msgType) {
		this.msgType = msgType;
	}

	public HiMsgType getMessageType() {
		return msgType;
	}

	public boolean isTimeOut()
	{
		if(this.beginTime != null)
		{
			java.util.Date now = new java.util.Date();		
			
			return (now.getTime() - this.beginTime.getTime() > timeThreshold ? true:false);		
		}
		
		
		return false;
	}
	
	public void setBeginTime(Date time)
	{
		this.beginTime = time;
	}
	
	public Date getBeginTime()
	{
		return this.beginTime;
	}
	
	public void setTimeThreshold(int time) {
		this.timeThreshold = time;
	}
	
	public int getTimeThreshold() {
		return this.timeThreshold;
	}
	
	public int getReTransmittedTimes()
	{
		return this.reTransmittedTimes;
	}
	public void increaseReTransmittedTimes()
	{
		this.reTransmittedTimes++;
	}
	
	public boolean isSend()
	{
		return this.isSend;
	}
	
	public void setSend(boolean isSend)
	{
		this.isSend = isSend;
	}
	
	public boolean isSyncSend()
	{
		return this.isSyncSend;
	}
	
	public void setSyncSend(boolean isSyncSend)
	{
		this.isSyncSend = isSyncSend;
	}	

	public boolean isReplied()
	{
		return this.isReplied;
	}
	
	public void setReplied(boolean isReplied)
	{
		this.isReplied = isReplied;
	}	
	
	@Override
	public BaseMessage clone()
	{
		BaseMessage o = null;
		try
		{
			o = (BaseMessage) super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			LogFactory.getLog(BaseMessage.class).error(
					"BaseMessage clone exception:", e);
		}
		return o;
	}
	
	public void clear() {
		
	}

	public String getNeType() {
		return neType;
	}

	public void setNeType(String neType) {
		this.neType = neType;
	}

	public void setNeVersion(String neVersion) {
		this.neVersion = neVersion;
	}

	public String getNeVersion() {
		return neVersion;
	}
	
	public boolean hasSameSynchronizationId(HiSysMessage msg) {
		return false;
	}
	public int getFrameInterval() {
		return frameInterval;
	}

	public void setFrameInterval(int frameInterval) {
		this.frameInterval = frameInterval;
	}
}
