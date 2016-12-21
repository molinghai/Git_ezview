package com.nm.server.adapter.base.sm;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.comm.HiSysMessage;

public class SSLReadSession extends ReadSession{
	private static final Log log = LogFactory.getLog(SSLReadSession.class);	
	SSLConnectSession sslcs;
	
	public SSLReadSession(SocketChannel sc) {
		super(sc);
		// TODO Auto-generated constructor stub
	}

	public SSLReadSession(SocketChannel sc,SSLConnectSession sslcs) {
		super(sc);
		// TODO Auto-generated constructor stub
		this.sslcs = sslcs;

	}
	
	

	@Override
	public void append() {
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
				
				synchronized(super.readBuffer)
				{
//					readBuffer.clear();
					synchronized(sslcs){
							sslcs.read(readBuffer);
					}

				}				
			}				
			
		}
		catch (Exception e)
		{
			log.error("read socket data error!",e);
			
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
			e.printStackTrace();
		}		
	}
	
	public String decode(ByteBuffer buffer) {

		Charset charset = null;
		CharsetDecoder decoder = null;
		CharBuffer charBuffer = null;
		try {
			charset = Charset.forName("gb2312");
			decoder = charset.newDecoder();
			charBuffer = decoder.decode(buffer);
//			System.out.println(" charBuffer= " + charBuffer);
    		System.out.println(charBuffer.toString());
			return charBuffer.toString();
		} catch (Exception ex) {
			ex.printStackTrace();
			return "";
		}
	}
}
