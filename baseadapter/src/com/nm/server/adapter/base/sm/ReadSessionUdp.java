package com.nm.server.adapter.base.sm;

import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.adapter.base.comm.UdpMessage;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.util.ToolUtil;

public class ReadSessionUdp extends ReadSession {

	private static final Log log = LogFactory.getLog(ReadSessionUdp.class);
	protected DatagramChannel _dc = null;
	
	public ReadSessionUdp(SocketChannel sc) {
		super(sc);
	}

	public DatagramChannel get_dc() {
		return _dc;
	}

	public void set_dc(DatagramChannel dc) {
		_dc = dc;
	}
	
	@Override
	public void process() {
		this.append();
		this.reply();
	}
	
	@Override
	public void append() {
		DatagramChannel dc = null;
		try
		{
			if(!_key.isValid()){
				_key.cancel();
				log.info("key is not valid!");
				return;
			}
			
			if (_key.isReadable()){
				dc = (DatagramChannel) _key.channel();
				synchronized(this.readBuffer)
				{
					dc.read(readBuffer);
				}
			}
		}
		catch (Exception e)
		{
			log.error("---read udp socket data error: make the socket disconnect!---" + e);
			try{
				this.sm.remove(dc);
				this.sm.doConnect();
			}catch (Exception e1){
				log.error("removDatagramChannel error!");
			}
		}
	}
	
	@Override
	public void reply() {
		try
		{
			List<HiSysMessage> msgList = this.fetch();
			if(msgList != null){
				AdapterMessagePool msgPool = (AdapterMessagePool)this.getMsgListener().getMsgPool();
				msgPool.addRevMsgList(msgList);
			}
		}
		catch (Exception e)
		{
			log.error("ReadSessionUdp reply exception:", e);
		}
	}
	
	@Override
	public List<HiSysMessage> fetch() {		
		List<HiSysMessage> msgs = null;
		synchronized (this.readBuffer) {
			int dataLen = readBuffer.capacity() - readBuffer.remaining();
			byte[] array = readBuffer.array();
			byte[] data = new byte[dataLen];
			System.arraycopy(array, 0, data, 0, dataLen);
			
			int i = 0;
			for (i = 0; i < dataLen; i++) {
//				if (array[i] == (byte) 0xa0) {
//					for (int index = i + 1; index < dataLen; index++) {
//						if (array[index] == 0xa0) {
//							UdpMessage udpMessage = new UdpMessage(array, i, index-i);
//							if (msgs == null) {
//								msgs = new ArrayList<HiSysMessage>();
//							}
//							msgs.add(udpMessage);
//							i = index-1;
//							break;
//						}else if(index>=dataLen-1){
//							UdpMessage udpMessage = new UdpMessage(array, i, dataLen - i);
//							if (msgs == null) {
//								msgs = new ArrayList<HiSysMessage>();
//							}
//							msgs.add(udpMessage);
//							break;
//						}
//					}
//				}
				UdpMessage udpMessage = new UdpMessage(array, 0, dataLen);
				String srcAddr = ToolUtil.bytesToIp(true, _dc.socket().getLocalAddress().getAddress());
				String destAddr = ToolUtil.bytesToIp(true, _dc.socket().getInetAddress().getAddress());
				udpMessage.setMsgId(srcAddr + "_" + destAddr + "_udp");
				if (msgs == null) {
					msgs = new ArrayList<HiSysMessage>();
				}
				msgs.clear();
				msgs.add(udpMessage);
				i = dataLen;
				break;
			}
			if (i < dataLen) {
				byte[] remain = new byte[dataLen - i];
				System.arraycopy(array, i, remain, 0, dataLen - i);
				readBuffer.clear();
				readBuffer.put(remain);
			} else {
				readBuffer.clear();
			}
		}
		return msgs;
	}

}
