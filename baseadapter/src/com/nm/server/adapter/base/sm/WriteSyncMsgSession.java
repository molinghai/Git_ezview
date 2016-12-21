package com.nm.server.adapter.base.sm;

import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.service.EthGateManager;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.MessagePool;

public class WriteSyncMsgSession extends WriteSession
{
	public WriteSyncMsgSession()
	{
		
	}
	
	public WriteSyncMsgSession(ClientMessageListener msgListener,
			TopoManager topoManager, EthGateManager ethGateManager,
			SocketMgrServer socketMgr)
	{
		super(msgListener,topoManager,ethGateManager,socketMgr);
	}

	public void processMessage(MessagePool msgPool) throws Exception
	{
		AdapterMessagePair mp = (AdapterMessagePair)msgPool.getUnSendSyncMsg();
		
		if(mp.getProtocol() == CommProtocol.CP_MSDH)
		{
			WriteSessionTask writeTask = new WriteSessionTask(
					this.msgListener.getMsgPool(),mp);
			writeTask.setTopoManager(topoManager);
			writeTask.setSocketMgr(socketMgr);
			writeTask.setEthGateManager(ethGateManager);
			writeTask.setNeMap(neMap);
			
			this.threadPool.execute(writeTask);
		}
		else if(mp.getProtocol() == CommProtocol.CP_SNMP )
		{
			WriteSessionTaskSnmp writeTaskSnmp = new WriteSessionTaskSnmp(
					this.msgListener.getMsgPool(),mp);
			writeTaskSnmp.setTopoManager(topoManager);
			
			this.threadPool.execute(writeTaskSnmp);
		}
		else if(mp.getProtocol() == CommProtocol.CP_SNMP_NEW)
		{
			WriteSessionTaskSnmpNew writeTaskSnmpNew = new WriteSessionTaskSnmpNew(
					this.msgListener.getMsgPool(),mp);
//			writeTaskSnmpNew.setTopoManager(topoManager);
			
			this.threadPool.execute(writeTaskSnmpNew);
		}
		else if(mp.getProtocol() == CommProtocol.CP_TABS){
			
		}else if(mp.getProtocol() == CommProtocol.CP_UDP){
			WriteSessionTaskUdp writeTask = new WriteSessionTaskUdp(
					this.msgListener.getMsgPool(),mp);
			writeTask.setTopoManager(topoManager);
			writeTask.setSocketMgr(socketMgr);
			writeTask.setEthGateManager(ethGateManager);
			writeTask.setNeMap(neMap);
			this.threadPool.execute(writeTask);
		}
	}

}
