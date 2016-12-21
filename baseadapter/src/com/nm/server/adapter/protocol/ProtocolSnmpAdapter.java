package com.nm.server.adapter.protocol;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.message.CsCommand;
import com.nm.nmm.mstp.service.MstpTopoManager;
import com.nm.nmm.res.ManagedElement;
import com.nm.protocolcvt.relations.service.LocalAndRemoteRelationsManager;
import com.nm.protocolcvt.res.LocalAndRemoteNERelations;
import com.nm.server.adapter.base.TaskExecType;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.sm.TaskManager;
import com.nm.server.adapter.snmp.SnmpAdapter;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.TaskResult;

public class ProtocolSnmpAdapter extends SnmpAdapter
{
	protected static ThreadLocal<ManagedElement> threadMe = new ThreadLocal<ManagedElement>();
	private static Log log = LogFactory.getLog(ProtocolSnmpAdapter.class);
	
	protected ClassPathXmlApplicationContext appContext;

	protected LocalAndRemoteRelationsManager relationsManager;
	protected LocalAndRemoteNERelations relation = null;
	protected MstpTopoManager mstpManager = null;
	protected boolean isDownLoad = false;
	
	@Override
	public void init(ClassPathXmlApplicationContext context)
	{
		super.init(context);
		mstpManager = (MstpTopoManager)context.getBean("mstpTopoManager");	
		relationsManager = (LocalAndRemoteRelationsManager)context.getBean("relationsManager");				
	}

	
	@Override
	public int getDownloadTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes)
	{
		int rtn = 0;
		
//		ManagedElement me = topoManager.getManagedElementById((int)this.getNeId());	// refresh ne data			
//		
//		ManagedElement changeIpMe = me.clone();
//		
//		parseDestIpAdress(changeIpMe);	
//		
//		CommandDestination dest = cmd.getDest();
//		if(dest != null){
//			dest.setNeIp(changeIpMe.getAddress().getIpAddressStr());
//		}else{
//			for(CommandParameter cp : cmd.getParamSet()){
//				CommandDestination cpdest = cp.getDest();
//				if(cpdest != null){
//					cpdest.setNeIp(changeIpMe.getAddress().getIpAddressStr());
//				}
//			}
//		}
		
		SyncNotifyManager.getInstance().notifyToClientProgress(
				cmdTask.getConnectionId(), 
				cmdTask.getMsgId(), 
				this.getNeId(), 
				10,
				"Init Data");		
		
			
		 // 需要判断版本与基本配置是否一致
		this.getDownloandEligible(tRes);
		
		SyncNotifyManager.getInstance().notifyToClientProgress(
				cmdTask.getConnectionId(), 
				cmdTask.getMsgId(), 
				this.getNeId(), 
				20,
				"Init Data");		
		
		rtn = tRes.getResult() == 0 ? this.getExtendSyncTask(cmdTask,3,0,tRes) : tRes.getResult();
		

		if(rtn != 0 || tRes.getResult() > 0 || cmdTask.getTaskCellList().size() == 0)
		{
			cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);
			
			
			log.info("TaskExecType.TASK_EXECUTED_DIRECTLY!");
		}
		
		return rtn;
		
		
	}
	
	private void parseDestIpAdress(ManagedElement remoteNe){		
		if(!remoteNe.isRemote()){
			return;
		}
		
		 java.util.List<LocalAndRemoteNERelations> list = relationsManager.getLocalAndRemoteNERelations(remoteNe.getId());
         if (list == null || list.isEmpty()) {
            return;
         }

         LocalAndRemoteNERelations relations = list.get(0);
       
         NeDescriptor nedes = NeDescriptionLoader.getNeDescriptor(remoteNe.getProductName(), remoteNe.getVersion());
         if (nedes == null) {
             return;
         }

         FunctionDescriptor funcdes = nedes.getFuncDesByName("supportCommand");
         if (funcdes != null && funcdes.getSupported()) {               
             AttributeDescriptor att = funcdes.getAttributeByName("snmpSet");
             if (att != null && att.getSupported()) {                   	 
            	 return;
             }else{
            	 ManagedElement localNe = mstpManager.getManagedElementById(relations.getlNeId());
                 if(localNe != null){
                	 remoteNe.setAddress(localNe.getAddress());
                 }
             }
         }         
	}
	
	public int getDownloandEligible(TaskResult tRes)
	{
		isDownLoad = true;
		if(this.getBaseConfMessage() == null) {
			return 0;
		}
		
		HiSysMessage msg = this.getBaseConfMessage();
		
		if(msg == null) {
			return 0;
		}
		
		HiSysMessage msgL = TaskManager.getInstance().sendAndRevMsg(msg, (int)this.getNeId(),30000,tRes);
		
		if(tRes.getResult() == 0)
		{			
			this.isGetFrameValid(neDes, msgL, 0 , tRes);
			
			if(tRes.getResult() == 0) {
				this.parseDownloadEligible(msgL, tRes);	
			}
		}	
		
		return 0;
	}
}