package com.nm.server.adapter.base.sm;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.nmm.res.ManagedElement;
import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.com.CommandFilter;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class ReadSession extends SocketSession
{
	private static final Log log = LogFactory.getLog(ReadSession.class);	
	
	public ByteBuffer readBuffer = ByteBuffer.allocate(4096);	
	
	public ReadSession(SocketChannel sc)
	{
		super(sc);
	}

	public void setReadBuffer(ByteBuffer readBuffer)
	{
		this.readBuffer = readBuffer;
	}

	public ByteBuffer getReadBuffer()
	{
		return readBuffer;
	}

	
	public int interestOps()
	{
		return SelectionKey.OP_READ;
	}

	public void process()
	{
//		log.info("-----readsession process---begin");
		
		this.append();
		this.reply();
		
//		log.info("-----readsession process---end");
	
	}

	public void append()
	{
		SocketChannel sc = null;
		
		try
		{
			
			if(!_key.isValid())
			{
				_key.cancel();
				
				log.info("key is not valid!");
				
				return;
			}			
			
			
			if (_key.isReadable())
			{
				sc = (SocketChannel) _key.channel();
				
				synchronized(this.readBuffer)
				{
					sc.read(readBuffer);
				}				
			}				
			
//			try
//			{
//				Thread.sleep(2);
//			}
//			catch (InterruptedException e)
//			{
//				log.error("Sleep error!");
//			}		
			
			
			
		}
		catch (Exception e)
		{
			log.error("---read socket data error: make the socket disconnect!---");
//			log.error("read socket data error:",e);
			
			try
			{
				this.sm.remove(sc);
				this.sm.doConnect();
			}
			catch (Exception e1)
			{
				log.error("removeSocketChannel error!");
			}
		}
	}
	
	public List<HiSysMessage> fetch()
	{
	
		List<HiSysMessage> msdhs = null;
		
		synchronized(this.readBuffer)
		{		
    		int dataLen = readBuffer.capacity() - readBuffer.remaining();
    		byte[] array = readBuffer.array();
    		
    		int i = 0;
    		
    		while(i + 17 < dataLen)
    		{			
    			if ((array[i] == (byte) 0x96) 
    					&& (array[i + 1] == (byte) 0x02 
    							||	array[i + 1] == (byte) 0x22 
    							||	array[i + 1] == (byte) 0x00 
    							|| array[i + 1] == (byte) 0x10
    							|| array[i + 1] == (byte) 0x12)) {
    				
    				int length = (ToolUtil.byteToUnsignedInt(array[i + 16]) << 8) + 
					ToolUtil.byteToUnsignedInt(array[i + 17]);
    				
    				if(i + 22 + length > dataLen)
    				{
    					break;
    				}
    				
    				byte[] dstAddr = new byte[4];
    				System.arraycopy(array, i + 2, dstAddr, 0, 4);
    				
    				/* 不属于该适配器处理的帧 */
					if(!ToolUtil.isIpAmongHostIps(dstAddr)) {
						i += (18 + 4 + length);
						continue;
					}

    								
    				MsdhMessage msdh = new MsdhMessage(array, i, 18 + length + 4);
    				if (null != msdh.getFixedData())
    				{
    					if (null == msdhs)
    					{
    						msdhs = new ArrayList<HiSysMessage>();
    					}
    					msdhs.add(msdh);
    					i += (18 + 4 + length);
    				}
    				else
    				{
    					byte[] err = new byte[22 + length];
    					System.arraycopy(array, i, err, 0, 22 + length);
    					
    					FileTool.writeSimple("FrameInfo", "frameErr.txt", err);
    					
    					i++;
    				}
    			}
    			else
    			{
    				i++;
    			}
    			
    			
    		}
    		
    		if (i < dataLen)
    		{			
    			
    			byte[] remain = new byte[dataLen - i];
    			System.arraycopy(array, i, remain, 0, dataLen - i);
    			readBuffer.clear();
    			readBuffer.put(remain);
    			
    //			FileTool.write("FrameInfo", "frameR.txt", remain);
    		}
    		else
    		{
    			readBuffer.clear();
    		}
		}
		return msdhs;

	}	
	
	/*avoid traps generate from many gateways*/
	private boolean isTrapBelongtoGw(String cmdId,byte[] neAddr, byte[] gwAddr,int port) {
		
		ManagedElement srcNe = CommandFilter.getBelogedNe(
				ToolUtil.bytesToLong(true, neAddr), this.sm.getNeMap());
		
		ManagedElement gwNe = CommandFilter.getBelogedGw(
				ToolUtil.bytesToLong(true, gwAddr),port, this.sm.getNeMap());
		
		if(srcNe != null && gwNe != null 
				&& (this.getEthGwId(srcNe) == gwNe.getId()
						||srcNe.getNatIpEx().equals(ToolUtil.bytesToIp(true, gwAddr)))) {

			return true;
		}
		
		String srcNeName = srcNe == null ? ToolUtil.bytesToIp(true, neAddr) : srcNe.getName();
		String dstNeName = gwNe == null ? ToolUtil.bytesToIp(true, gwAddr) : gwNe.getName();
		
		log.info("filter a trap(" + cmdId +")" + ": the ne(" +  srcNeName
				+ ") does not belong to gw(" + dstNeName + ")");
		
		return false;
	}
	
	protected int getEthGwId(ManagedElement me) {
		int ethId = 0;
		
		if(!me.isServeAsEthGateway()) {
			if(me.getEthGatewayInfo() != null) {
				ethId = me.getEthGatewayInfo().getWorkGatewayNeId();
			} else if(me.getMonitorProxyInfo() != null) {
				ethId = this.getGwNeId(me.getMonitorProxyInfo().getProxyNeId());
			} else {
				log.error("------ ne: " + me.getName() + " do not have a gateway info.------");
			}			
		} else {
			ethId = me.getId();
		}
		
		return ethId;
	}
	
	private int getGwNeId(int proxyNeId) {
		if(proxyNeId > 0) {
			synchronized(this.sm.getNeMap()) {
				ManagedElement me = this.sm.getNeMap().get(proxyNeId);
				
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
	
	protected void filterTrapCmds(List<HiSysMessage> msgList) {
		
		if(msgList == null || msgList.isEmpty()) {
			return;
		}
		
		Iterator<HiSysMessage> it = msgList.iterator();
		while(it.hasNext()) {
			MsdhMessage msdh = (MsdhMessage)it.next();
			
			byte[] srcAddr = msdh.getSrcAddress();
			byte[] gwAddr = this._sc.socket().getInetAddress().getAddress();
			int port = this._sc.socket().getPort();
			
			if(msdh.getSynchronizationFlag()== 0 
					&& !this.isTrapBelongtoGw(msdh.getCommandId(),srcAddr, gwAddr,port)) {
				it.remove();
			}
		}
	}
	
	public void reply()
	{
		
		try
		{
			
			List<HiSysMessage> msgList = this.fetch();
			
			if(msgList != null)
			{
//				log.info("msgList.size()=" + msgList.size());
				this.filterTrapCmds(msgList);
				
				AdapterMessagePool msgPool = (AdapterMessagePool)this.getMsgListener().getMsgPool();
				msgPool.addRevMsgList(msgList);
			}
			

		}
		catch (Exception e)
		{
			log.error("ReadSession reply exception:", e);
		}		
	}

}
