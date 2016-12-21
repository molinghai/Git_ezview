package com.nm.server.adapter.snmp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.descriptor.EquipmentDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.mpls.service.CESService;
import com.nm.mpls.service.EthService;
import com.nm.mpls.tunnel.MPLSTunnel;
import com.nm.mpls.tunnel.TunnelProtectionGroup;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.sync.DifBase;
import com.nm.server.adapter.base.BaseAdapter;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.config.TaskResult;

public class SnmpNewAdapter extends BaseAdapter {
	protected Log log = LogFactory.getLog(this.getClass());
    protected static ThreadLocal<ManagedElement> threadMe = new ThreadLocal<ManagedElement>();

	public HiSysMessage getMsg(Object destData,Object userData,ResFunc resFunc,TaskResult tRes)
	{		
		try{
			String funcName = resFunc.getFuncName();
			List<String> subFuncNames = resFunc.getSubFuncNames();
			SnmpNewMessage msg = new SnmpNewMessage();
			msg.setFuncName(funcName);
			msg.setSubFuncNames(subFuncNames);
			
			List<String> fields = null;
			List<Map<String, Object>> values = null;
			List<Map<String, Object>> conditions = null;
			Map<String,  List<Map<String, Object>>> subValuesMap = null;
			Map<String,  List<Map<String, Object>>> subConditionsMap = null;
			FunctionDescriptor fd = null;
			List<String> clis = null;
			List<Integer> backupSlots = null;//备份盘槽位
			if(userData instanceof Map){
				Map<String, Object> m = (Map<String, Object>)userData;
				Integer opType = (Integer)m.get("opType");
				fields = (List<String>)m.get("guiGenFields");
				values = (List<Map<String, Object>>)m.get("guiGenData");
				conditions = (List<Map<String, Object>>)m.get("guiGenCondition");
				subValuesMap = (Map<String,  List<Map<String, Object>>>)m.get("guiGenSubData");
				subConditionsMap = (Map<String,  List<Map<String, Object>>>)m.get("guiGenSubCondition");
				fd = (FunctionDescriptor)m.get("funcDes");
				clis = (List<String>)m.get("cli");
				backupSlots = (List<Integer>)m.get("backupSlots");
				msg.setOpType(opType);
				msg.setFields(fields);
				msg.setConditions(conditions);
				msg.setValues(values);
				msg.setSubValues(subValuesMap);
				msg.setSubConditions(subConditionsMap);
				msg.setFunctionDescriptor(fd);
				msg.setClis(clis);
				msg.setBackupSlots(backupSlots);
			}
			if(destData instanceof DeviceDest){
			msg.setDeviceDest((DeviceDest)destData);
			}else if(destData instanceof DifBase){
				msg.setDeviceDest(this.getDeviceDest((DifBase)destData));
			}
			return msg;
		}catch(Exception e){
			log.error(e, e);
		}
		return null;
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
					SnmpNewMessage snmpMsg = (SnmpNewMessage)msg;					
					if(snmpMsg.getValues() != null){
						userDataL.add(snmpMsg.getValues());
					}
					if(snmpMsg.getSubValuesMap() != null){
						userDataL.add(snmpMsg.getSubValuesMap());
					}
					if(snmpMsg.getClis() != null){
						userDataL.addAll(snmpMsg.getClis());
					}
					if(snmpMsg.getConditions() != null && !snmpMsg.getConditions().isEmpty()){
						userDataL.add(snmpMsg.getConditions());
					}
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
						&& !CommProtocol.CP_CLI.equals(taskCell.getProtocolType())
						&& !CommProtocol.CP_SNMP_II.equals(taskCell.getProtocolType())
						&& !CommProtocol.CP_SNMP_NEW.equals(taskCell.getProtocolType())){
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
					if(msg instanceof SnmpNewMessage){
						SnmpNewMessage snmpMsg = (SnmpNewMessage)msg;
						
						List<Map<String, Object>> userData = snmpMsg.getValues();
						
						if(snmpMsg.getClis() != null){
							userDataL.addAll(snmpMsg.getClis());
						}else if(userData != null){
							userDataL.add(userData);
						}
					}
				}					
				TaskResult tRes = new TaskResult();

				if(resFunc == null){
					log.error("Not found resFunc ：" + taskCell.getStrID());
				}else{
					resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);				
				}
				

				List<Object> objList = resFunc.getResult();
				
				if(objList.size() > 0)
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
	
	public int getDownloandEligible(TaskResult tRes)
	{
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
		taskCell.setProtocolType(CommProtocol.CP_SNMP_NEW);
//		ResFunc resFunc = this.getResFunc(clsName);		
		if(destList != null && !destList.isEmpty()
				&& destList.get(0) instanceof DeviceDest
				&& funcName != null){
			DeviceDest dest = (DeviceDest) destList.get(0);
			if(this.getEquipName() == null){
				this.setEquipName(dest.getEquipName());
			}
			int slot = dest.getSlot();
			if (dest.getNeId() != -10000) {
				ManagedElement me = (ManagedElement)this.getExtendObj();
				NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
				NeDescriptionLoader.getNeDescriptor(me.getProductName());
				FunctionDescriptor funcDes = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, isSync, funcName);
				if(funcDes == null && dest.getEquipName() != null){
					EquipmentDescriptor eqDes = neDes.getEquipmentDescriptor(dest.getEquipName());
					funcDes = eqDes.getFuncDesByName(funcName);
				}
				if(funcDes != null){
					domain = funcDes.getDomain();
					CommProtocol pt = getProtocolType(dest, me, funcName);
					if(pt != null){
						taskCell.setProtocolType(pt);
					}
				}
			} else {
				NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(dest.getNeType(), dest.getNeVersion());
				FunctionDescriptor funcDes = neDes.getFuncDesByName(funcName);
				if (funcDes == null && dest.getEquipName() != null) {
					EquipmentDescriptor eqDes = neDes.getEquipmentDescriptor(dest.getEquipName());
					funcDes = eqDes.getFuncDesByName(funcName);
				}
				if(funcDes != null){
					domain = funcDes.getDomain();
					CommProtocol pt = getProtocolType(dest, null, funcName);
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
			resFunc.setAppContext(this.appContext);	
			resFunc.setSync(isSync);
			if(funcName != null){
				resFunc.setFuncName(funcName);
			}
			resFunc.setExtendObj(this.getExtendObj());
			resFunc.init(objList);  	
			if (isSync != 0) {
				if (destList.get(0) instanceof DeviceDest) {
					DeviceDest dest = (DeviceDest) destList.get(0);
					CommandDestination cd = getDestData(dest);
					List<Object> destListEx = new ArrayList<Object>();
					destListEx.add(cd);
					resFunc.delete(destListEx, isSync, tRes);
				}
			}

    		
    		taskCell.setNotfify(resFunc.isNotify());
    		taskCell.setNotifyToServer(resFunc.isNotifyToServer());
    		
    		taskCell.setObj(resFunc);
    		taskCell.setStrID(clsName);
    		
    		List<HiSysMessage> msgList = this.getTaskMsgList(cmdType,destList,resFunc,tRes, taskCell);
    		
    		taskCell.addMsgList(msgList);	
		}
		
		return taskCell;
		
	}
	@SuppressWarnings("unchecked")
	public DeviceDest getDeviceDest(DifBase difBase) {
		
		DeviceDest deviceDest = new DeviceDest();
		if (difBase == null) {
			return deviceDest;
		}
		List<DifBase> subDifList = difBase.getSubDbList();
		if (subDifList == null || subDifList.isEmpty()) {
			return deviceDest;
		}
		DifBase subDifBase = subDifList.get(0);
		Object objNe = subDifBase.getObjNe();
		Object objNm = subDifBase.getObjNm();
		if (objNe == null && objNm == null) {
			return deviceDest;
		}
		int neId = 0;
		if (objNe != null) {
			List<Object> objList = (List<Object>) objNe;
			Object obj = objList.get(0);
			if (obj instanceof MPLSTunnel) {
				neId = (int) ((MPLSTunnel) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			} else if (obj instanceof TunnelProtectionGroup) {
				neId = (int) ((TunnelProtectionGroup) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			} else if (obj instanceof EthService) {
				neId = ((EthService) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			}else if (obj instanceof CESService) {
				neId = ((CESService) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			}

		}
		if (objNm != null) {
			List<Object> objList = (List<Object>) objNm;
			Object obj = objList.get(0);
			if (obj instanceof MPLSTunnel) {
				neId = (int) ((MPLSTunnel) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			} else if (obj instanceof TunnelProtectionGroup) {
				neId = (int) ((TunnelProtectionGroup) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			} else if (obj instanceof EthService) {
				neId = ((EthService) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			}else if (obj instanceof CESService) {
				neId = ((CESService) obj).getNeId();
				deviceDest.setNeId(neId);
				this.setNeTypeAndVersion(neId, deviceDest);
				return deviceDest;
			}

		}

		return deviceDest;
	}
	private void setNeTypeAndVersion(int neId,DeviceDest dest){
		ManagedElement ne = this.topoManager.getManagedElementById(neId);
		if(ne!=null){
			dest.setNeType(ne.getProductName());
			dest.setNeVersion(ne.getVersion());
		}		
	}
}
