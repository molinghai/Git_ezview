package com.nm.server.adapter.protocol;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.nmm.mstp.service.MstpTopoManager;
import com.nm.nmm.res.ManagedElement;
import com.nm.protocolcvt.relations.service.LocalAndRemoteRelationsManager;
import com.nm.protocolcvt.res.LocalAndRemoteNERelations;
import com.nm.server.adapter.base.sm.TaskManager;
import com.nm.server.adapter.mstp.MstpAdapter;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.TaskResult;

public class ProtocolAdapter extends MstpAdapter {
	
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
	
	public int getBaseSyncTask(TaskResult tRes)
	{		
		ManagedElement me = topoManager.getManagedElementById((int)this.getNeId());	// refresh ne data			
		
		ManagedElement changeIpMe = me.clone();
		NeDescriptor nedes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
   
        FunctionDescriptor funcdes = nedes.getFuncDesByName("neAttribute");
        if(funcdes != null && funcdes.isSupported()){	
        	parseDestIpAdress(changeIpMe);			
			HiSysMessage msgConf = this.getBaseConfMessage();
			if(msgConf != null){
				HiSysMessage msgL = TaskManager.getInstance().sendAndRevMsg(msgConf, changeIpMe, 30000,tRes);
				
				if(tRes.getResult() == 0)
				{
					this.isGetFrameValid(neDes, msgL, 0 ,tRes);
					
					if(tRes.getResult() == 0) {
						this.parserBaseConfMessage(me, msgL, tRes);
						
						if(tRes.getResult() == 0)
						{
							this.initManager.addSyncData(me);
							
						}
					}
				}
			}else{
				parseDestIpAdress(changeIpMe);
				me = initManager.getTopoManager().getManagedElementById(me.getId());
				this.parserBaseConfMessage(me, null, tRes);
				
				if(tRes.getResult() == 0)
				{
					this.initManager.addSyncData(me);
					
				}
			}
        }else{
			me = initManager.getTopoManager().getManagedElementById(me.getId());
			this.parserBaseConfMessage(me, null, tRes);
			
			if(tRes.getResult() == 0)
			{
				this.initManager.addSyncData(me);
				
			}
        }
		return tRes.getResult();
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
         
         if (relations.getHopCount() == 1) {
             FunctionDescriptor funcdes = nedes.getFuncDesByName("supportCommand");
             if (funcdes != null && funcdes.getSupported()) {               
                 AttributeDescriptor att = funcdes.getAttributeByName("query");
                 if (att.getSupported()) {                   	 
                	 return;
                 }else{
                	 ManagedElement localNe = mstpManager.getManagedElementById(relations.getlNeId());
                     if(localNe != null){
                    	 remoteNe.setAddress(localNe.getAddress());
                     }
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
			this.isGetFrameValid(neDes, msgL, 0, tRes);
			
			if(tRes.getResult() == 0) {
				this.parseDownloadEligible(msgL, tRes);	
			}
		}	
		
		return 0;
	}
}
