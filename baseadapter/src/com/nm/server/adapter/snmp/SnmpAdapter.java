package com.nm.server.adapter.snmp;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.PDU;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.descriptor.EquipmentDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.adapter.base.AdapterFactory;
import com.nm.server.adapter.base.BaseAdapter;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.SnmpMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.config.TaskResult;

public class SnmpAdapter extends BaseAdapter
{
	protected static ThreadLocal<ManagedElement> threadMe = new ThreadLocal<ManagedElement>();
	private static Log log = LogFactory.getLog(SnmpAdapter.class);
	
	protected ClassPathXmlApplicationContext appContext;

	public HiSysMessage getMsg(Object destData,Object userData,final ResFunc resFunc,TaskResult tRes)
	{		
		SnmpMessage msg = new SnmpMessage(){
			public String getCommandId(){
				return resFunc.getCmdId();
			}
		};
		msg.setPdu((PDU)userData);
		return msg;
	}
	
	public List<Object> getReturnUserDataL(TaskCell taskCell)
	{
		Date date = new Date();
		List<Object> userDataL = null;
		
		if(taskCell != null)
		{
			List<HiSysMessage> msgList = taskCell.getResMsgList();
			
			if(msgList != null && msgList.size() > 0)
			{
				userDataL = new ArrayList<Object>();
				
				for(HiSysMessage msg:msgList)
				{
					SnmpMessage snmpMsg = (SnmpMessage)msg;					
					
					userDataL.add(snmpMsg.getPdu());
				}	
			}			

		}		
		
		return userDataL;
	}	
	
	@SuppressWarnings("unchecked")
	@Override
	public int processSyncResult(CsCommand cmd, AsynTask resTask,Response rs, AsynTask cmdTask)
	{		
		if(resTask == null)
		{
			return -1;
		}		
		if(resTask.getTaskCellList() != null && !resTask.getTaskCellList().isEmpty())
		{
			for(TaskCell taskCell:resTask.getTaskCellList())
			{				
				if(!CommProtocol.CP_SNMP.equals(taskCell.getProtocolType())
						&& !CommProtocol.CP_SNMP_I.equals(taskCell.getProtocolType())){
					continue;
				}
				List<HiSysMessage> msgList = taskCell.getResMsgList();				
				
				ResFunc resFunc = null;
				
				// Trap
				if(cmd == null)
				{
					rs.setcmdId(taskCell.getStrID());
					resFunc = this.getResFunc(taskCell.getStrID());
				}
				else
				{
					resFunc = (ResFunc)taskCell.getObj();
				}
				
				List<Object> userDataL = new ArrayList<Object>();
				
				for(HiSysMessage msg:msgList)
				{
					SnmpMessage snmpMsg = (SnmpMessage)msg;
					
					PDU userData = snmpMsg.getPdu();
					
					userDataL.add(userData);
				}					
				TaskResult tRes = new TaskResult();
				
				resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);				
				
				List<Object> objList = resFunc.getResult();

				if(objList != null && objList.size() > 0)
				{
					for(int i = 0; i< objList.size(); i++)
					{					
						CommandParameter cp = new CommandParameter();

						cp.setStrID(taskCell.getStrID());
						
						Object obj = objList.get(i);
						
						if(obj instanceof Map)
						{						
							cp.setParaMap((Map<String,String>)obj);
						}
						else
						{								
							cp.setParamter(objList.get(i));
						}				
					}
				}		
			}
		}
		return 0;		
	}
	
	@Override
	public TaskCell getTaskCell(
			int cmdType,
			List<Object> destList,
			String clsName,
			List<Object> objList,
			int isSync,
			TaskResult tRes, String funcName)
	{		
		
		
		
		TaskCell taskCell = new TaskCell(clsName,destList);		
		taskCell.setProtocolType(CommProtocol.CP_SNMP);
//		ResFunc resFunc = this.getResFunc(clsName);		
		if(destList != null && !destList.isEmpty()
				&& destList.get(0) instanceof DeviceDest
				&& funcName != null){
			DeviceDest dest = (DeviceDest) destList.get(0);
			if(this.getEquipName() == null){
				this.setEquipName(dest.getEquipName());
			}
			int slot = dest.getSlot();
			ManagedElement me = (ManagedElement)this.getExtendObj();
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			FunctionDescriptor funcDes = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, isSync, funcName);
			if(funcDes == null && dest.getEquipName() != null){
				EquipmentDescriptor eqDes = neDes.getEquipmentDescriptor(dest.getEquipName());
				funcDes = eqDes.getFuncDesByName(funcName);
				log.info("FUnctionDes second,funcDes =" + funcDes+"funcName = " + funcName);
			}
			if(funcDes != null){
				this.domain = funcDes.getDomain();
				if(funcDes.getProtocol() != null && !funcDes.getProtocol().isEmpty()){
					CommProtocol pt = getProtocolType(dest, me, funcName);
					if(pt != null){
						taskCell.setProtocolType(pt);
					}
				}
			}
		}
		if(taskCell.getProtocolType() == null){
			taskCell.setProtocolType(this.getProtocol());
		}
		ResFunc resFunc = getResFunc(clsName);		
		if(resFunc != null)
		{	
			resFunc.setSync(isSync);
			if(funcName != null){
				resFunc.setFuncName(funcName);
			}
			appContext=this.getAppContext();
			resFunc.setAppContext(this.appContext);	
			resFunc.setExtendObj(this.getExtendObj());
    		resFunc.init(objList);  	
    		
    		taskCell.setNotfify(resFunc.isNotify());
    		taskCell.setNotifyToServer(resFunc.isNotifyToServer());
    		
    		taskCell.setObj(resFunc);
    		taskCell.setStrID(clsName);
    		
    		List<HiSysMessage> msgList = this.getTaskMsgList(cmdType,destList,resFunc,tRes, taskCell);
    		
    		taskCell.addMsgList(msgList);	
		}
		
		return taskCell;
		
	}
	
}