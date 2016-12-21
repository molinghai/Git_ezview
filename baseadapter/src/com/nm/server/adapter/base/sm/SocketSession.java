package com.nm.server.adapter.base.sm;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.nm.nmm.res.EthGate;
import com.nm.nmm.service.EthGateManager;
import com.nm.server.comm.ClientMessageListener;

public class SocketSession
{
	
	protected ClientMessageListener msgListener;	
	protected SocketMgrServer sm = null;	
	
	private EthGateManager ethGateManager;  
	
	protected SocketChannel _sc = null;
	protected SelectionKey _key = null;

	protected EthGate gw = null;

	public SocketSession(SocketChannel sc)
	{
		this._sc = sc;
	}
	
	
	public void setMsgListener(ClientMessageListener msgListener)
	{
		this.msgListener = msgListener;
	}


	public ClientMessageListener getMsgListener()
	{
		return msgListener;
	}

    public void setSm(SocketMgrServer sm)
	{
		this.sm = sm;
	}


	public SocketMgrServer getSm()
	{
		return sm;
	}
	
	public void setEthGateManager(EthGateManager ethGateManager)
	{
		this.ethGateManager = ethGateManager;
	}


	public EthGateManager getEthGateManager()
	{
		return ethGateManager;
	}


	public SocketChannel channel()
	{
		return this._sc;
	}

	public SelectionKey key()
	{
		return this._key;
	}
	public int interestOps()
	{
		return 0;
	}

	public void process()
	{
	
	}

	public void registered(SelectionKey key)
	{
		this._key = key;
	}


	public void setEthGate(EthGate gw) {
		this.gw = gw;
	}


	public EthGate getEthGate() {
		return this.gw;
	}

}
