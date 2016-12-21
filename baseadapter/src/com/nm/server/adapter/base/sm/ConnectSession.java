package com.nm.server.adapter.base.sm;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.EthGate.GateState;
import com.nm.server.common.util.ToolUtil;



public class ConnectSession extends SocketSession
{
	private static final Log log = LogFactory.getLog(ConnectSession.class);   	
	
	
	public EthGate gw = null;
	
    public ConnectSession(SocketChannel sc)
    {
    	super(sc);
    }  



	public void setEthGate(EthGate gw)
    {
    	this.gw = gw;
    }
    
    public EthGate getEthGate()
    {
    	return this.gw;
    }
    
 

	public void process()
	{
//		log.info("##### ConnectionSession.hashCode == " + this.hashCode());
		if(_key == null) {
			
			log.info("key is null!");
			return;
		}
//		log.info("##### ConnectSession---begin---process.hashCode---" + _key.hashCode());
		if(!_key.isValid())
		{
			_key.cancel();
			
			log.info("key is not valid!");
			
			return;
		}
		
		if (_key.isConnectable())
		{
			SocketChannel sc = (SocketChannel) _key.channel();
			if (sc.isConnectionPending())
			{
				boolean isfinished = true;
				try
				{
					sc.finishConnect();
				}
				catch(Exception e)
				{
					isfinished = false;
					this.updateGwState(GateState.NOT_CONNECT,false);
					
					_key.cancel();
					
					try
					{
						sc.close();
					}
					catch (IOException eL)
					{
						log.error("sokect close exception:",e);
					}
					
					log.info("********* can not finish connection with the gateway: " + ToolUtil.longToIp(gw.getIpAddress())+ ":" +gw.getPort() + " ******");
				}
										
				
				if (sc.isConnected())
				{										
					
					this.sm.addReadSession(sc,gw);
					
					this.updateGwState(GateState.CONNECTED,true);
					
				}
				else if(isfinished)
				{
					_key.cancel();
					
					try
					{
						sc.close();
					}
					catch (IOException e)
					{
						log.error("sokect close exception:",e);
					}
					this.updateGwState(GateState.NOT_CONNECT,true);
					log.info("----ConnectSession---  sc.isConnected() == false");
				}
				
//				log.info("********* remove a object of ConnectSession _added.size()=" + sm._added.size() + "****");
			}else{
				this.updateGwState(GateState.NOT_CONNECT,true);
				log.info("----ConnectSession---  sc.isConnectionPending() == false");
			}
		}else{
			this.updateGwState(GateState.NOT_CONNECT,true);
			log.info("----ConnectSession---  _key.isConnectable() == false");
		}
		sm.removeConnectSession(this);
		this.gw = null;
//		log.info("##### ConnectSession---end---process.hashCode---" + _key.hashCode());
	}


	public int interestOps()
	{
		return SelectionKey.OP_CONNECT;
	}
	
	public void updateGwState(GateState gwState,boolean isUpdateDB)
	{
		synchronized(this.sm.getGateLock())
		{
			if(gw != null)
			{
				gw.setGateState(gwState);
				
				if(isUpdateDB)
				{
					this.getEthGateManager().updateEthGate(gw);
				}				
			}
		}
	}
	
}

