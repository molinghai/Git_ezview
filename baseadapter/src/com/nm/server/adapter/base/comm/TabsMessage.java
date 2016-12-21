package com.nm.server.adapter.base.comm;

import org.apache.commons.logging.LogFactory;

import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class TabsMessage extends BaseMessage
{
	private byte[] head = new byte[3];
	private byte[] fixedData = null;
	private byte[] checkSum = new byte[2];
	private String tabsAddr = "";
	private String srcAddr = "";

	
	public TabsMessage()
	{
		int index = 0;
		head[index++] = (byte)0xDB;
	}	
	
	public byte[] getFrame()
	{
		int userDataLen = fixedData.length;
		this.setCmdLength(userDataLen);
		this.getCheckSum();
		byte[] frame = new byte[5 + userDataLen];		
		System.arraycopy(head, 0, frame, 0, 3);
		System.arraycopy(fixedData, 0, frame, 3, userDataLen);
		System.arraycopy(checkSum, 0, frame, 3 + userDataLen, 2);
//		for(byte b : fixedData){
//			b = (byte) getPositive(b);
//		}
		return frame;
	}
	
	private void getCheckSum() {
		// TODO Auto-generated method stub
		int cs = 0;
		
		for(int i = 0; i < head.length; i++){
			cs += getPositive(head[i]);
		}
		for(int i = 0; i < fixedData.length; i++){
			cs += getPositive(fixedData[i]);
		}
		checkSum[0] = (byte)(cs & 0xff);
		checkSum[1] = (byte)((cs >> 8) & 0xff);
	}

	private int getPositive(byte btVal) {
		return btVal >= 0 ? btVal : 256 + btVal;
	}
	
	public byte[] getHeadData()
	{
		return this.head;
	}
	
	public void setHeadData(byte[] head,int length)
	{
		if(this.head == null)
		{
			this.head = new byte[length];
		}
		
		System.arraycopy(head, 0, this.head, 0, length);		
	}	
	
	
	public void setCheckSum(byte[] source, int pos, int length) {
		if(this.checkSum == null)
		{
			this.checkSum = new byte[length];
		}
		
		System.arraycopy(source, pos, this.checkSum, 0, length);		
	}

	public byte[] intToByteArray(int x)
	{
		byte[] res = new byte[4];
		res[0] = (byte) (x >>> 24);
		res[1] = (byte) (x >>> 16);
		res[2] = (byte) (x >>> 8);
		res[3] = (byte) x;
		return res;
	}


	public int getCmdCode()
	{
		if (this.fixedData.length >= 1)
		{
			return ToolUtil.byteToUnsignedInt(fixedData[3]);
		}
		return -1;
	}


	public void setDstAddress(int tabs)
	{
		head[1] = (byte) ((tabs << 3) | (head[1] & 0x7));
	}
	
	public void setLevel2Command(int cmd){
		head[1] = (byte) ((head[1] & 0x0) | cmd);
	}
	
	public int getDstAddress()
	{
		return head[1] & 0xF8;
	}
	
	public void setCmdLength(int len){
		head[2] = (byte) (len & 0xFF);
	}

	public void setFixedData(byte[] fixedData, int length) throws Exception
	{
		this.fixedData = new byte[length];
		System.arraycopy(fixedData, 0, this.fixedData, 0, length);
	}
	
	public void setFixedData(byte[] fixedData, int pos,int length) throws Exception
	{
		this.fixedData = new byte[length];
		System.arraycopy(fixedData, pos, this.fixedData, 0, length);
	}	

	public byte[] getFixedData()
	{
		return this.fixedData;
	}

	public short getUserDataLen()
	{
		
		short high = (short) (checkSum[0] << 8);
		short low = (short) checkSum[1];
		
		if(low < 0)
		{
			low += 256;
		}		
		
		return (short)(high | low);
	}

	public byte[] calcCheckSum(byte[] src, int offset, int length)
	{
		int checkSum = 0;
		for (int i = offset; i < offset + length; i++)
		{
			checkSum += ToolUtil.byteToUnsignedInt(src[i]);
		}
		return intToByteArray(checkSum);

	}

	public void buildFrame(byte[] src, int offset, int length)
	{
		System.arraycopy(src, offset, head, 0, 18);
		int len = this.getUserDataLen();
		fixedData = new byte[len];
		System.arraycopy(src, offset + 18, fixedData, 0, length - 18 - 4);
		System.arraycopy(src, offset + length - 4, this.checkSum, 0, 4);
	}


	
	@Override
	public String toString()
	{
		return super.toString();
	}

	@Override
	public String getCommandId()
	{
		if (this.fixedData.length >= 1)
		{
			int ret = ToolUtil.byteToUnsignedInt(fixedData[3]);
			return ToolUtil.intToFormatString(ret, 0);
		}
		return "";
	}

	
	@Override
	public TabsMessage clone()
	{
		return (TabsMessage)super.clone();
	}

	@Override
	public void init(HiSysMessage msg)
	{
		if(msg != null)
		{
			TabsMessage msgL = (TabsMessage)msg;
			
			this.setHeadData(msgL.getHeadData(), msgL.getHeadData().length);			
			
			try
			{
				this.setFixedData(msgL.getFixedData(), msgL.getFixedData().length);
			}
			catch (Exception e)
			{
				LogFactory.getLog(BaseMessage.class).error(
						"TabsMessage init exception:", e);
			}	
			
		}
	}
	
	public String getTabsAddr() {
		return tabsAddr;
	}

	public void setTabsAddr(String tabsAddr) {
		this.tabsAddr = tabsAddr;
	}

	public String getSrcAddr() {
		return srcAddr;
	}

	public void setSrcAddr(String srcAddr) {
		this.srcAddr = srcAddr;
	}

	public boolean equals(Object o)
	{
		if(o instanceof TabsMessage){
			TabsMessage tabs = (TabsMessage)o;
			
			if(this.fixedData != null && tabs.fixedData != null && 
					this.fixedData.length == tabs.fixedData.length)
			{
				return this.isFixedDataEqual(this.fixedData, tabs.fixedData);
			}
		}
		return false;
	}
	
	private boolean isFixedDataEqual(byte[] userData1,byte[] userData2)
	{
		for(int i = 0; i < userData1.length; i++)
		{
			if(userData1[i] != userData2[i])
			{
				return false;
			}
		}	
		
		return true;
	}
	
	public void clear() {
		this.head = null;
		this.fixedData = null;
		this.checkSum = null;
	}

	@Override
	public int getCommadSerial() {
		return -1;
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
		int ret = fixedData[0];
		return Integer.toString(ret);
	}

	@Override
	public String getSynchnizationId(boolean arg0) {
		int ret = fixedData[0];
		return Integer.toString(ret);
	}
}
