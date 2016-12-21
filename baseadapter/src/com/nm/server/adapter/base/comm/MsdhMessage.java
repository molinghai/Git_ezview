package com.nm.server.adapter.base.comm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.LogFactory;

import com.jcraft.jzlib.ZInputStream;
import com.nm.server.adapter.mstp.MstpAdapter;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class MsdhMessage extends BaseMessage
{
	byte[] head = new byte[18];
	byte[] fixedData = null;
	byte[] variableData = null;
	byte[] checkSum = new byte[4];

	
	public MsdhMessage()
	{
		int index = 0;
		
		head[index++] = (byte)0x96;
		head[index++] = (byte)0x03;
		
		head[index++] = (byte)0x00;
		head[index++] = (byte)0x00;
		head[index++] = (byte)0x00;
		head[index++] = (byte)0x00;
		
		head[index++] = (byte)0x00;
		head[index++] = (byte)0x00;
		head[index++] = (byte)0x00;
		head[index++] = (byte)0x00;	
		
		head[index++] = (byte)0x02;
		head[index++] = (byte)0x00;		
		
		head[index++] = (byte)0x0;
		head[index++] = (byte)0x0;
		head[index++] = (byte)0x0;
		head[index++] = (byte)0x0;
		
		
		head[index++] = (byte)0x0;
		head[index++] = (byte)0x0;				
	}	
	
	public MsdhMessage(byte[] src, int offset, int length)
	{
		this.build(src, offset, length);
	}
	
	public byte[] getFrame()
	{
		
		int userDataLen = getUserDataLen();
		byte[] frame = new byte[22 + userDataLen];		
		System.arraycopy(head, 0, frame, 0, 18);
		System.arraycopy(fixedData, 0, frame, 18, userDataLen);
		System.arraycopy(checkSum, 0, frame, 18 + userDataLen, 4);
		return frame;
	}
	
	public byte[] getFrameEx()
	{
		int userDataLen = getUserDataLen();
		byte[] userData = fixedData;
		if(((this.getHeadByte(2) & 0x0f) == (byte)0x02 || (this.getHeadByte(2) & 0x0f) == (byte)0x22)
				&& ((this.getHeadByte(10) & 0x0f) == (byte)0x03)) {
			userData = this.zlibUnZip(fixedData);
			userDataLen = userData.length;
		} else if(userData.length > 5 && userData[0] == (byte) 0x61 && userData[1] == (byte) 0xF0 && userData[5] == 0) {
			userData = this.zlibUnZip(ArrayUtils.subarray(userData, 6,
					userData.length));
			userDataLen = userData.length;
		} else if(userData.length > 11 && userData[0] == (byte) 0x69 && userData[1] == (byte) 0xF0) {
			userData = this.zlibUnZip(ArrayUtils.subarray(userData, 2,
					userData.length));
			userDataLen = userData.length;
		}
		byte[] frame = new byte[22 + userDataLen];		
		System.arraycopy(head, 0, frame, 0, 18);
		frame[16] = (byte) (userDataLen >>> 8);
		frame[17] = (byte) userDataLen;
		System.arraycopy(userData, 0, frame, 18, userDataLen);
		System.arraycopy(checkSum, 0, frame, 18 + userDataLen, 4);

		return frame;
	}
	protected byte[] zlibUnZip(byte[] object) {
		byte[] data = null;
		try {
			ByteArrayInputStream in = new ByteArrayInputStream(object);
			ZInputStream zIn = new ZInputStream(in);
			byte[] buf = new byte[1024];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = zIn.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			data = baos.toByteArray();
			baos.flush();
			baos.close();
			zIn.close();
			in.close();
		} catch (Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"unjzlib exception:",e);
		}
		return data;
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
	
	public byte getHeadByte(int index)
	{
		if(index >= 18)
		{
			return -1;
		}
		
		return head[index];
	}
	
	public void setHeadCtrlBit(int op,byte value)
	{
		
		if(op == 0)
		{
			head[1] &= value;
		}
		else
		{
			head[1] |= value;
		}
	}		
	

	public void setHeadByte(int index,byte value)
	{
		if(index >= 18)
		{
			return;
		}
		
		head[index] = value;
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

	public byte[] shortToByteArray(short x)
	{
		return null;
	}

	public void setHeadFlag(byte headFlag)
	{
	}

	void setMessageControl(byte messageControl)
	{

	}

	public int getCmdCode()
	{
		if (this.fixedData.length >= 2)
		{

			byte[] byteArray = { fixedData[0], fixedData[1] };
			return ToolUtil.unsignedByteToInt(byteArray);
		}
		return -1;
	}

	public byte buildMessageControl(boolean command, boolean ethChannel,
			boolean includeVarData, boolean tabs, boolean fixedDataIsMd,
			boolean beContinue)
	{
		byte control = 0;
		if (command)
		{
			control = (byte) (control | 0x01);
		}
		if (ethChannel)
		{
			control = (byte) (control | 0x02);
		}
		if (includeVarData)
		{
			control = (byte) (control | 0x04);
		}
		if (beContinue)
		{
			control = (byte) (control | 0x08);
		}
		if (tabs)
		{
			control = (byte) (control | 0x10);
		}
		if (fixedDataIsMd)
		{
			control = (byte) (control | 0x20);
		}
		return control;
	}

	// void setMessageControl(boolean command, boolean ethChannel,
	// boolean includeVarData, boolean tabs,
	// boolean fixedDataIsMd, boolean beContinue) {
	// byte control = MsdhMessage.buildMessageControl(command, ethChannel,
	// includeVarData, tabs,
	// fixedDataIsMd, beContinue);
	// setMessageControl(control);
	// }
	public void setSrcAddress(byte[] srcAddr)
	{
		if (null != srcAddr)
		{
			for (int i = 0; i < 4; i++)
			{
				head[6 + i] = srcAddr[i];
			}
		}
	}

	public void setDstAddress(byte[] destAddr)
	{
		if (null != destAddr)
		{
			for (int i = 0; i < 4; i++)
			{
				head[2 + i] = destAddr[i];
			}
		}
	}

	public void setVersion(byte version)
	{
		// head[10] = (byte) ((byte)(head[10] & 0xf0) | (byte)(version & 0x0f));
		head[10] = (byte) (version & 0x0f);
	}

	public byte getVersion()
	{
		return (byte) (head[10] & 0x0f);
	}

	public void setSynchronizationFlag(short synchFlag)
	{
		head[12] = (byte)(synchFlag >>> 8);
		head[13] = (byte)synchFlag;
	}

	public int getSynchronizationFlag()
	{
		byte ctrl = (byte) (head[1] & ((byte)0x10));
		
		/*tabs use command*/
		if( ctrl  == ((byte)0x10)) {
			 int syncflag = ToolUtil.byteToUnsignedInt(fixedData[3]);
             if(syncflag == 0x80 || syncflag == 0x21 || syncflag == 0xa7){
                       if(ToolUtil.byteToUnsignedInt(fixedData[0]) == 0xdb){
                                 /*发送帧:链路地址在1，节点地址在4*/
                                 syncflag += ToolUtil.byteToUnsignedInt(fixedData[4]);
                       }else if(ToolUtil.byteToUnsignedInt(fixedData[0]) == 0xe7){
                                 /*应答帧:链路地址在1，节点地址在5*/
                                 syncflag += ToolUtil.byteToUnsignedInt(fixedData[5]);
                       }
              }
              return syncflag;
		} else {
			byte[] b = { head[12], head[13] };
			return ToolUtil.unsignedByteToInt(b);
		}
	}

	public void setOrderFlag(int orderFlag)
	{
		head[14] = (byte) (orderFlag >>> 8);
		head[15] = (byte) orderFlag;
	}

	public void setUserDataLen(int userDataLen)
	{
		head[16] = (byte) (userDataLen >>> 8);
		head[17] = (byte) userDataLen;
	}

	public void setFixedData(byte[] fixedData, int length) throws Exception
	{
		if ((head[1] & 0x04) != 0)
		{
			throw new Exception("Current version can not support variable-user-data.");
		}
		setUserDataLen(length);
		this.fixedData = new byte[length];
		System.arraycopy(fixedData, 0, this.fixedData, 0, length);
	}
	
	public void setFixedData(byte[] fixedData, int pos,int length) throws Exception
	{
		if ((head[1] & 0x04) != 0)
		{
			throw new Exception("Current version can not support variable-user-data.");
		}
		setUserDataLen(length);
		this.fixedData = new byte[length];
		System.arraycopy(fixedData, pos, this.fixedData, 0, length);
	}	

	public byte[] getFixedData()
	{
		return this.fixedData;
	}

	public void setMsdhHead(byte headFlag, byte messageControl, byte[] dstAddr,
			byte[] srcAddr, byte version, short synchFlag, short orderFlag,
			short userDataLen)
	{
		head[0] = headFlag;
		head[1] = messageControl;
		if (null != dstAddr)
		{
			for (int i = 0; i < 4; i++)
			{
				head[2 + i] = dstAddr[i];
			}
		}
		if (null != srcAddr)
		{
			for (int i = 0; i < 4; i++)
			{
				head[6 + i] = srcAddr[i];
			}
		}
		head[10] = (byte) (0x0f & version);
		head[11] = 0;
		head[12] = (byte) (synchFlag >>> 8);
		head[13] = (byte) synchFlag;
		head[14] = (byte) (orderFlag >>> 8);
		head[15] = (byte) orderFlag;
	}

	public short getUserDataLen()
	{
		
		short high = (short) (head[16] << 8);
		short low = (short) head[17];
		
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

	public byte[] buildFrame(int orderFlag)
	{
		this.setSynchronizationFlag((short)orderFlag);
		this.setOrderFlag(orderFlag);
		
		int userDataLen = getUserDataLen();
		byte[] frame = new byte[22 + userDataLen];
		System.arraycopy(head, 0, frame, 0, 18);
		System.arraycopy(fixedData, 0, frame, 18, userDataLen);
		checkSum = this.calcCheckSum(frame, 0, 18 + userDataLen);
		System.arraycopy(checkSum, 0, frame, 18 + userDataLen, 4);
		return frame;
	}

	public void build(byte[] src, int offset, int length)
	{
		if (src[offset] == (byte) 0x96)
		{
			
			byte[] checkSum = this.calcCheckSum(src, offset, length - 4);
			int checkSumIndex = offset + length - 4;
			boolean valid = true;
			for (int i = 0; i < 4; i++)
			{
				if (src[checkSumIndex] != checkSum[i])
				{
					valid = false;
					break;
				}
				checkSumIndex++;
			}
			if (valid)
			{		
				this.buildFrame(src, offset, length);				
			}
			else
			{
				byte[] err = new byte[length];
				System.arraycopy(src, offset, err, 0, length);
				
//				FileTool.write("FrameInfo", "frameErr.txt", err);				
				
							
				byte[] temp = {err[err.length - 4],
						err[err.length - 3],
						err[err.length - 2],
						err[err.length - 1]};			
				
				FileTool.writeSimple("FrameInfo", "frameCrcSrc.txt", temp);
				FileTool.writeSimple("FrameInfo", "frameCrc.txt", checkSum);	
			}	
			
		}		
		
	}

	@Override
	public byte[] getSrcAddress()
	{
		byte[] src = { head[6], head[7], head[8], head[9] };
		return src;
	}

	@Override
	public byte[] getDestAddress()
	{
		byte[] dest = { head[2], head[3], head[4], head[5] };
		return dest;
	}	
	
	@Override
	public String getSynchnizationId()
	{
//		if (this.getVersion() >= 2)
		{
			return Integer.toString(this.getSynchronizationFlag());
		}
//		return null;
	}

	
	@Override
	public String toString()
	{
		return super.toString();
	}



	@Override
	public int getCommadSerial()
	{
		if (this.head.length >= 18)
		{
			byte ctrl = (byte) (head[1] & ((byte)0x10));
			
			if(ctrl == 0) {
				byte[] byteArray = { head[12], head[13] };
				return ToolUtil.unsignedByteToInt(byteArray);
			} else {
				return this.getSynchronizationFlag();
			}
		}
		return 0;
	}

	@Override
	public String getCommandId()
	{
		if (this.fixedData.length >= 2)
		{
			int dataIndex = 0;
			
			if((this.head[1] & (byte)0x20) == (byte)0x20)
			{
				dataIndex = 1;
			}
			
			
			byte[] byteArray = { fixedData[dataIndex], fixedData[dataIndex + 1] };
			int ret = ToolUtil.unsignedByteToInt(byteArray);
			
			return ToolUtil.intToFormatString(ret, 0);
		}
		return "";
	}

	@Override
	public int getFrameSerial()
	{
		if (this.head.length >= 18)
		{

			byte[] byteArray = { head[14], head[15] };
			return ToolUtil.unsignedByteToInt(byteArray);
		}
		return 0;
	}

	@Override
	public String getSynchnizationId(boolean isSrcMsg)
	{
		String synchronizedId = null;
		String strIp = null;
		
		if(isSrcMsg)
		{
			strIp = ToolUtil.bytesToIp(true, this.getDestAddress());
		}
		
		else
		{
			strIp = ToolUtil.bytesToIp(true, this.getSrcAddress());
		}
		// Ip + synchronizedId Ψһȷ��һ������
		synchronizedId = strIp + this.getSynchnizationId();
		
		return synchronizedId;
	}
	
	@Override
	public MsdhMessage clone()
	{
		return (MsdhMessage)super.clone();
	}

	@Override
	public void init(HiSysMessage msg)
	{
		if(msg != null)
		{
			MsdhMessage msgL = (MsdhMessage)msg;
			
			this.setHeadData(msgL.getHeadData(), msgL.getHeadData().length);			
			
			try
			{
				this.setFixedData(msgL.getFixedData(), msgL.getFixedData().length);
			}
			catch (Exception e)
			{
				LogFactory.getLog(BaseMessage.class).error(
						"MsdhMessage init exception:", e);
			}	
			
		}
	}
	
	public boolean equals(Object o)
	{
		if(o instanceof MsdhMessage){
			MsdhMessage msdh = (MsdhMessage)o;
			
			if(this.fixedData != null && msdh.fixedData != null && 
					this.fixedData.length == msdh.fixedData.length)
			{
				return this.isFixedDataEqual(this.fixedData, msdh.fixedData);
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
		this.variableData = null;
		this.checkSum = null;
	}
}
