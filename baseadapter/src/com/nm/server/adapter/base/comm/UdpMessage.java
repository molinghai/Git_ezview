package com.nm.server.adapter.base.comm;

import com.nm.server.comm.HiSysMessage;

public class UdpMessage extends BaseMessage{

	private byte[] userData = null;
	private String msgId = "_udp";

	public UdpMessage(){
		
	}

	public UdpMessage(String userDataStr){
		this.userData = userDataStr.getBytes();
	}
	
	public UdpMessage(byte[] src, int offset, int length){
		userData = new byte[length];
		System.arraycopy(src, offset, userData, 0, length);
	}
	
	public byte[] getUserData() {
		return userData;
	}

	public void setUserData(byte[] userData) {
		this.userData = userData;
	}
	
	public Object getUserDataStr() {
		return new String(userData);
	}
	
	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}
	
	@Override
	public int getCommadSerial() {
		return 1;
	}

	@Override
	public String getCommandId() {
		return null;
	}

	@Override
	public byte[] getDestAddress() {
		return null;
	}

	@Override
	public int getFrameSerial() {
		return 0;
	}

	@Override
	public byte[] getSrcAddress() {
		return null;
	}

	@Override
	public String getSynchnizationId() {
		return msgId;
	}

	@Override
	public String getSynchnizationId(boolean paramBoolean) {
		return msgId;
	}

	@Override
	public void init(HiSysMessage paramHiSysMessage) {
		
	}
	
	public boolean hasSameSynchronizationId(HiSysMessage msg) {
		return this.getSynchnizationId() != null 
		&& msg.getSynchnizationId() != null 
		&& msg.getSynchnizationId().startsWith(this.getSynchnizationId());
	}

}
