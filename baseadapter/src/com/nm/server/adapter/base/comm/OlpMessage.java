package com.nm.server.adapter.base.comm;

import com.nm.server.comm.HiSysMessage;

public class OlpMessage extends BaseMessage {
	private byte[] userData = null;
	private String msgId = "olp";
	public OlpMessage() {
		
	}
	public OlpMessage(String userDataStr) {
		this.userData = userDataStr.getBytes();
	}
	
	public OlpMessage(byte[] src, int offset, int length) {
		userData = new byte[length];
		System.arraycopy(src, offset, userData, 0, length);
	}
	
	public void setUserData(byte[] userData) {
		this.userData = userData;
	}
	
	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}
	
	public byte[] getUserData() {
		return userData;
	}
	
	public byte[] getFrame() {
		return userData;
	}
	
	public String getUserDataStr() {
		return new String(userData);
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
	public String getSynchnizationId(boolean src) {
		return msgId;
	}

	@Override
	public void init(HiSysMessage arg0) {
		
	}	
	
	public boolean hasSameSynchronizationId(HiSysMessage msg) {
		return this.getSynchnizationId().startsWith("olp") &&
			msg.getSynchnizationId() != null &&
			msg.getSynchnizationId().startsWith("olp");
	}
	
}
