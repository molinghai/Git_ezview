package com.nm.server.adapter.base.sm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.MessagePool;

public class MatchSession extends Thread
{
	private static final Log log = LogFactory.getLog(MatchSession.class);
	
	private ClientMessageListener msgListener;		
	
	private boolean run = true;

	public MatchSession(ClientMessageListener msgListener)
	{
		this.setMsgListener(msgListener);
	}

	public void setMsgListener(ClientMessageListener msgListener)
	{
		this.msgListener = msgListener;
	}

	public ClientMessageListener getMsgListener()
	{
		return msgListener;
	}

	
	@Override
	public void run()
	{
		MessagePool msgPool = this.msgListener.getMsgPool();
		
		
		while(run)
		{
			try
			{
				Thread.sleep(10);
				
				
				msgPool.reply();
				
			}
			catch (Exception e)
			{
				log.error("match session exception",e);
			}
		}
	}
	
	public void stopThread() {
		this.run = false;
	}
	
}
