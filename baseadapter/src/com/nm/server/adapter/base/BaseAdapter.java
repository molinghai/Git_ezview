package com.nm.server.adapter.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import com.nm.descriptor.EquipmentDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.descriptor.TPDescriptor;
import com.nm.descriptor.enumdefine.SubTpType;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CommandDestination;
import com.nm.message.CommandDestination.ChannelLevel;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.nmm.mstp.service.MstpTopoManager;
import com.nm.nmm.res.Equipment;
import com.nm.nmm.res.EquipmentHolder;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.TerminationPoint;
import com.nm.nmm.res.TopologicalLink;
import com.nm.nmm.service.TopoManager;
import com.nm.nmm.service.impl.TopologicalLinkManager;
import com.nm.nmm.sync.DifBase;
import com.nm.nmm.sync.EqExtendDif;
import com.nm.nmm.sync.TpExtendDif;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.base.sm.TaskManager;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.DestUtil;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.config.TaskResult;
import com.nm.server.common.util.ToolUtil;
import com.nm.server.config.service.NeConfigManager;
import com.nm.server.config.service.NeInitManager;

public class BaseAdapter implements Adapter
{
	private static final Log log = LogFactory.getLog(BaseAdapter.class);
	
	protected ClassPathXmlApplicationContext appContext;
	
	protected Object extendObj;
	
	private long neId;
	private String strNetype;
	private String sVersion;
	private CommProtocol 	protocol = CommProtocol.CP_MSDH;
	protected TopologicalLinkManager tlManager;
	protected TopoManager topoManager;
	protected NeInitManager initManager;
	protected NeDescriptor neDes;
	private String equipName;
	private String functionName;
	private int isSync = 0;
	protected String domain;
	
	
	@Override
	public Object getExtendObj()
	{
		return this.extendObj;
	}

	@Override
	public void setExtendObj(Object obj)
	{
		this.extendObj = obj;
	}
	
	/**
	 * @param neId the neId to set
	 */
	public void setNeId(long neId)
	{
		this.neId = neId;
	}

	/**
	 * @return the neId
	 */
	public long getNeId()
	{
		return neId;
	}
	
	@Override
	public CommProtocol getProtocol()
	{
		return this.protocol;
	}
	
	@Override
	public void setProtocol(CommProtocol protocol)
	{
		this.protocol = protocol;
	}

	@Override
	public String getStrNetype()
	{
		return this.strNetype;
	}

	@Override
	public void setStrNetype(String strNetype)
	{
		this.strNetype = strNetype;
	}		

	/**
	 * @param sVersion the sVersion to set
	 */
	public void setNeVersion(String sVersion)
	{
		this.sVersion = sVersion;
	}

	/**
	 * @return the sVersion
	 */
	public String getNeVersion()
	{
		return sVersion;
	}

	public void setAppContext(ClassPathXmlApplicationContext context)
	{
		this.appContext = context;
	}

	public ClassPathXmlApplicationContext getAppContext()
	{
		return this.appContext;
	}	

	public void setTopoManager(TopoManager topoManager)
	{
		this.topoManager = topoManager;
	}

	public TopoManager getTopoManager()
	{
		return topoManager;
	}

	public void setInitManager(NeInitManager initManager)
	{
		this.initManager = initManager;
	}

	public NeConfigManager getConfigManager()
	{
		return initManager;
	}

	public void setNeDes(NeDescriptor neDes)
	{
		this.neDes = neDes;
	}

	public NeDescriptor getNeDes()
	{
		return neDes;
	}

	@Override
	public void init(ClassPathXmlApplicationContext context)
	{
		this.setAppContext(context);
		
		if(context != null)
		{
			if(topoManager == null)
			{
				this.topoManager = (MstpTopoManager)this.appContext.getBean("mstpTopoManager");
			}
			
			if(this.initManager == null)
			{
				this.initManager = (NeInitManager) this.appContext.getBean("initManager");		
				this.initManager.setAppContext(context);
				
			}
			
			if(tlManager == null) {
				tlManager = (TopologicalLinkManager) this.appContext.getBean("tlManager");
			}
					
			this.neDes = NeDescriptionLoader.getNeDescriptor(this.getStrNetype(),this.getNeVersion());			
			
		}
	}	
	
	@Override
	public int encode(CsCommand cmd,AsynTask cmdTask,TaskResult tRes)
	{		
		if(ToolUtil.getEntryConfig().getSetOnOff() == 0 
				&& (cmd.getCategory().getValue() == 10002 
						|| cmd.getCategory().getValue() == 10003 
						|| 	cmd.getCategory().getValue() == 10004)) {
			tRes.setResult(ErrorMessageTable.NM_NOT_SUPPORT_SET);
			cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);
		}
		
		int rtn = 0;
		switch(cmd.getCategory().getValue())
		{
		case 10000:
			this.isSync = 1;
			rtn = this.getSyncTask(cmd, cmdTask,tRes);
			break;			
		case 10001:
			rtn = this.getSendTask(0,cmd,cmdTask,tRes);
			break;				
		case 10002:
			rtn = this.getSendTask(1,cmd,cmdTask,tRes);
			break;			
		case 10003:
			rtn = this.getDownloadTask(cmd,cmdTask,tRes);
			break;	
		case 10004:
			rtn = this.getConfirmNMTask(cmd,cmdTask,tRes);
			break;		
		case 10006:
			rtn = this.getSendTask(6,cmd,cmdTask,tRes);
			break;	
		case 40000:
			rtn = this.getAlarmTask(cmd, cmdTask,tRes);
		default:
				break;
		}			

		if(rtn != 0) {
			tRes.setResult(rtn);
			cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);
		}
		
		return rtn;
	}
	
	private CommandDestination getDest(CsCommand cmd) {
		CommandDestination dest = cmd.getDest();
		if (dest == null) {
			log.info("cmd.getParamSet() == null ? " + cmd.getParamSet() == null);
			for (CommandParameter cp : cmd.getParamSet()) {
				dest = cp.getDest();
				break;
			}
		}
		return dest;
	}

	@Override
	public int decode(CsCommand cmd, AsynTask resTask,Response rs,AsynTask cmdTask)
	{		
		int rtn = -1;
		
		
		if(cmd != null)
		{    		
    		
			rs.setCategory(cmd.getCategory());
			rs.setcmdId(cmd.getId());			
			
			switch(cmd.getCategory().getValue())
			{
			case 10000:
				rtn = this.processSyncResult(cmd, resTask,rs,cmdTask);
				break;
				
			case 10001:
			case 10006:
				rtn = this.processQueryResult(cmd,resTask,rs,cmdTask);
				break;			
			case 10002:
				rtn = this.processSetResult(cmd,resTask,rs,cmdTask);
				break;
			case 10003:
				rtn = this.processDownloadResult(cmd,resTask,rs,cmdTask);
				break;	
			case 10004:
				rtn = this.processConfirmNMResult(cmd, resTask, rs, cmdTask);
				break;
			case 40000:
				rtn = this.processAlarmResult(cmd, resTask, rs,cmdTask);
			
				
			default:
				break;
			}			
		}
		else
		{
			return this.processQueryResult(cmd,resTask,rs,cmdTask);
		}
		
		
		return rtn;
	}
	
	public List<Object> getReturnUserDataL(TaskCell taskCell)
	{
		return null;
	}
	
	@Transactional
	@SuppressWarnings("unchecked")
	@Override
	public int processQueryResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask)
	{
		// 1.使用msgList 初始化objList
		// 2.使用objList中的对应资源类更新DB
		// 3.返回数据
		
		
		for(TaskCell taskCell:resTask.getTaskCellList())
		{			
			//如果taskCell协议和adapter协议不一致，则跳过
			if(taskCell.getProtocolType() != null && !this.getProtocol().equals(taskCell.getProtocolType())){
				continue;
			}
			ResFunc resFunc = null;
			
			if(cmd == null)					// Trap command
			{				
				
				List<Object> destListL = new ArrayList<Object>();				
				destListL.add(this.getDestData());			
				
				taskCell.setDestList(destListL);
				if (rs.getCmdId() == null || "".equals(rs.getCmdId())) {
					rs.setcmdId(taskCell.getStrID());
				}
				resFunc = this.getResFunc(taskCell.getStrID());
				resFunc.setAppContext(appContext);				
				resFunc.init(null);
				
				
				if(resFunc.getCmdId() != null && !"".equals(rs.getCmdId())) {
					rs.setcmdId(resFunc.getCmdId());
				}

			}
			else
			{
				// due to the transactional problem,the function of ne polling is considered specially.
				if(taskCell.getStrID().equalsIgnoreCase("neConnStateFunc"))
				{
					resFunc = this.getResFunc(taskCell.getStrID());
					resFunc.setAppContext(this.appContext);	
				}
				else
				{
					resFunc = (ResFunc)taskCell.getObj();
				}		
				resFunc.setFuncName(cmd.getFuncName());
			}
			
			if(resFunc == null)
			{
				log.info("No ResFunc!");
				continue;
			}
			
			resFunc.setExtendObj(this.extendObj);
			
			// 解析用户数据字节
			// 一个对象对应多个帧的情况，需要组合多个帧
			List<Object> userDataL = this.getReturnUserDataL(taskCell);			
			TaskResult tRes = new TaskResult();
			
			this.isGetFramesValid(neDes, taskCell.getResMsgList(), 0, tRes);
			
			if(tRes.getResult() != 0) {
				rs.setResult(tRes.getResult());
				return tRes.getResult();
			}
			
			if(userDataL != null && userDataL.size() > 0)
			{
				
				// 更新数据库
				resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);
				
				
				// 设置返回结果
				List<Object> objList = resFunc.getResult();
				
				if(objList != null)
				{
					for(int i = 0; i< objList.size(); i++)
					{					
						CommandParameter cp = null;
						
						CommandDestination dest = null;
						
						if(	taskCell.getDestList() != null && 
								taskCell.getDestList().size() > i)
						{
							
							DeviceDest deviceDest = (DeviceDest)taskCell.getDestList().get(i);
							
							dest = this.getDestData(deviceDest);
							
						}
						
						cp = rs.getCommandParameter(dest);
						
						
						Object obj = objList.get(i);
						
						if(obj instanceof Map)
						{		
							boolean isMapString = true; 
							
							Map<String,?> objMap = (Map<String,?>)obj;
							
							Iterator<String> it = objMap.keySet().iterator();
							
							while(it.hasNext())
							{
								Object objVal = objMap.get(it.next());
								
								if(!(objVal instanceof String))
								{
									cp.setObjMap((Map<String,Object>)obj);
									
									isMapString = false;
									
									break;
								}
							}
							
							if(isMapString)
							{
								cp.setParaMap((Map<String,String>)obj);
							}							
							
						}
						else
						{					
							cp.setParamter(obj);
						}
					}
					
					objList.clear();
					objList = null;
				}
				
				rs.setResult(tRes.getResult());
			}
			else
			{
				rs.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
			resFunc.clear();
			resFunc = null;
			taskCell.clear();
			taskCell = null;
		}						

		
		
		return 0;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public int processSetResult(CsCommand cmd, AsynTask resTask,Response rs,AsynTask cmdTask)
	{
		// 1.使用msgList 初始化objList
		// 2.使用objList中的对应资源类更新DB
		// 3.返回数据
		
		assert(resTask != null);
		
		
		
		for(TaskCell taskCell:resTask.getTaskCellList())
		{
			//如果taskCell协议和adapter协议不一致，则跳过
			if(taskCell.getProtocolType() != null && !this.getProtocol().equals(taskCell.getProtocolType())){
				continue;
			}
			ResFunc resFunc = (ResFunc)taskCell.getObj();
			resFunc.setFuncName(cmd.getFuncName());
			
			
			// 解析用户数据字节
			// 一个对象对应多个帧的情况，需要组合多个帧
			List<Object> userDataL = this.getReturnUserDataL(taskCell);	
			TaskResult tRes = new TaskResult();
			
			this.isGetFramesValid(neDes, taskCell.getResMsgList(), 0, tRes);
			
			if(tRes.getResult() != 0) {
				rs.setResult(tRes.getResult());				
				return tRes.getResult();
			}
			
			if(userDataL != null && userDataL.size() > 0)
			{
				
				resFunc.updateSet(taskCell.getDestList(),userDataL,tRes);
				
				
				// 设置返回结果
				List<Object> objList = resFunc.getSetResult();
				
				if(objList != null)
				{
					for(int i = 0; i< objList.size(); i++)
					{		
						CommandDestination dest = null;
						
						if(taskCell.getDestList() != null && taskCell.getDestList().size() > i)
						{
							DeviceDest deviceDest = (DeviceDest)taskCell.getDestList().get(i);
							dest = this.getDestData(deviceDest);
						}													
						
						CommandParameter cp = rs.getCommandParameter(dest);
						
						
						Object obj = objList.get(i);
						
						if(obj instanceof Map)
						{		
							boolean isMapString = true; 
							
							Map<String,?> objMap = (Map<String,?>)obj;
							
							Iterator<String> it = objMap.keySet().iterator();
							
							while(it.hasNext())
							{
								Object objVal = objMap.get(it.next());
								
								if(!(objVal instanceof String))
								{
									cp.setParamter(obj);
									
									isMapString = false;
									
									break;
								}
							}
							
							if(isMapString)
							{
								cp.setParaMap((Map<String,String>)obj);
							}							
							
						}
						else
						{					
							cp.setParamter(obj);
						}
						
					}
					
					objList.clear();
					objList = null;
				}
				
				resFunc.clear();
				resFunc = null;
				
				rs.setResult(tRes.getResult());
			}
			else
			{
				rs.setResult(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
			
			taskCell.clear();
			taskCell = null;
		
		}
		
		return 0;
	}	
	
	public HiSysMessage getMsg(Object destData,Object userData,ResFunc resFunc,TaskResult tRes)
	{				
		return null;
	}
	
	public List<HiSysMessage> getTaskMsgList(
			int cmdType,
			List<Object>destList,
			ResFunc resFunc,
			TaskResult tRes,
			TaskCell taskCell)
	{
		
		List<HiSysMessage> msgList = new ArrayList<HiSysMessage>();		
		
		List<Object> userDataL = null;
		String opType = "";
		switch(cmdType)
		{
		case 0:
		case 6:
			
			log.debug("-------------------command:" + resFunc.getCmdId()+ " get------------begin-------");			
			
			userDataL = resFunc.get(destList,tRes);
			
			log.debug("-------------------command:" + resFunc.getCmdId()+ " get------------end-------");
			opType = "get";
			break;
		case 1:
			userDataL = resFunc.set(destList,tRes);
			opType = "set";
			break;
		case 2:
			userDataL = resFunc.confirmByNm(destList,tRes);
			opType = "confirmByNm";
			break;	
		case 3:
			userDataL = resFunc.download(destList,tRes);
			opType = "download";
			break;		
		default:
			break;
		}
		
		if(tRes.getResult() > 0) {
			log.info("*******" + opType + " operation:" + resFunc.getCmdId() 
					+ "'s ErrorCode="+ tRes.getResult() + " ******");
		}
		
		if(userDataL == null || userDataL.isEmpty()) {
			log.info("*******" + opType + " operation:" + resFunc.getCmdId() 
					+ "'s userData is null! ******");
		}
		
		if(userDataL != null)
		{
			for(int i = 0; i < userDataL.size(); i++)
			{			
				HiSysMessage msg = this.getMsg(destList.get(0),userDataL.get(i),resFunc,tRes);				
				
				if(msg != null)
				{
					msgList.add(msg);
				}				
				
			}
			
			userDataL.clear();
		}
		
	
		
		return msgList;			
	}
	
	public TaskCell getTaskCell(
			int cmdType,
			List<Object>destList,
			String clsName,
			List<Object>objList,
			int isSync,
			TaskResult tRes)
	{		
		
       return getTaskCell(cmdType, destList, clsName, objList, isSync, tRes, null);
		
	}
	
	public TaskCell getTaskCell(
			int cmdType,
			List<Object> destList,
			String clsName,
			List<Object> objList,
			int isSync,
			TaskResult tRes, String funcName)
	{		
		
		TaskCell taskCell = new TaskCell(clsName,destList);		
//		taskCell.setProtocolType(ProtocolType.PT_MSDH);
//		ResFunc resFunc = this.getResFunc(clsName);		
		if(destList.isEmpty()){
			log.info("Error : destList.isEmpty()!!! Command = " + clsName);
		}
		if(destList != null && !destList.isEmpty()
				&& destList.get(0) instanceof DeviceDest){
			DeviceDest dest = (DeviceDest) destList.get(0);
			if(this.getEquipName() == null){
				this.setEquipName(dest.getEquipName());
			}
			if(funcName != null){
				int slot = dest.getSlot();
				ManagedElement me = (ManagedElement)this.getExtendObj();
				FunctionDescriptor funcDes = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, isSync, funcName);
				if(funcDes != null){
					this.domain = funcDes.getDomain();
					CommProtocol pt = getProtocolType(dest, me, funcName);
					taskCell.setProtocolType(pt);
				}
			}
			
		}
		if(taskCell.getProtocolType() == null){
			taskCell.setProtocolType(this.getProtocol());
		}
//		ResFunc resFunc = (ResFunc)this.getBean(clsName);
		ResFunc resFunc = this.getResFunc(clsName);		
		
		if(resFunc != null)
		{	
			resFunc.setSync(isSync);
			if(funcName != null){
				resFunc.setFuncName(funcName);
			}

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
	
	/**
	 * 首先按板卡类型+命令字（NE51_Card_173_ getXXX）、网元类型+命令字（NE51 _getXXX）、
	 * 系列+命令字（PTN_ getXXX）、命令字（getXXX）、的顺序查询处理类
	 * @param domain 系列
	 * @param command 命令字
	 * @return 命令字对应的处理类
	 * */
	protected ResFunc getResFunc(String domain, String command) {
		String neType  = this.getStrNetype();			
		String version = this.getNeVersion();		
		DeviceDest dest = (DeviceDest)this.getDeviceDest();
		String equipName = null;
		if(dest != null){
			equipName = dest.getEquipName();
		}else{
//			log.error("getResFunc：dest == null, equipName == null");
		}
		if(equipName == null){
			equipName = this.getEquipName();
		}
		if(domain == null || domain.isEmpty()){
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, version);
			if (neDes != null) {
				domain = neDes.getDomain();
			}
		}
		ResFunc resFunc = null;
		
		if(command == null || neType == null || neType.isEmpty()) {
			log.error("getResFunc : command == null || neType == null || neType.isEmpty()!");
			return null;
		}
			
		if(equipName != null){
			//按板卡类型+命令字
			String funcNameL = equipName + "_" + command;
			try	{
				resFunc = (ResFunc) this.getBean(funcNameL);
			} catch (NoSuchBeanDefinitionException ee) {
//	    		log.info("no bean:" + funcNameL);
			}							
		}		
		if(resFunc == null){
			List<String> verList = NeDescriptionLoader.getVersionList(neType);
			
			boolean startTag = false;
			
			for(int verIndex = verList.size() - 1; verIndex > 0; verIndex--)
			{
				if(sVersion.equals(verList.get(verIndex)) && !startTag)
				{
					startTag = true;
				}
				
				if(startTag)
				{
					
					String funcNameL = neType + "_" + verList.get(verIndex) + "_" + command;
					
					
	    			try
	    			{
	    				resFunc = (ResFunc) this.getBean(funcNameL);
	    			}
	    			catch (NoSuchBeanDefinitionException ee)
	    			{
//	    				log.info("no bean:" + funcNameL);
	    			}		
	    			
				}
				
				if(resFunc != null) {
					break;
				}
			}
			if(resFunc == null){
				String funcNameL = neType + "_" + command;
				try	{
					resFunc = (ResFunc) this.getBean(funcNameL);
				} catch (NoSuchBeanDefinitionException eee) {
//					log.info("no bean:" + funcNameL);
				}	
			}
		}
		if(resFunc == null && domain != null && !domain.isEmpty()){
			// 系列+命令字
			String funcNameL = domain.toUpperCase() + "_" + command;
			try	{
				resFunc = (ResFunc) this.getBean(funcNameL);
			} catch (NoSuchBeanDefinitionException ee) {
//	    		log.info("no bean:" + funcNameL);
			}	
		}
		if(resFunc == null){
			//命令字
			try	{
				resFunc = (ResFunc) this.getBean(command);
			} catch (NoSuchBeanDefinitionException eee) {
//				log.info("no bean:" + command);
//				log.error(command + " is error!");
			}	
		}
		if(resFunc != null && 
				(resFunc.getCmdId() == null || resFunc.getCmdId().equals("")))
		{
			resFunc.setCmdId(command);
		}
		
		return resFunc;
	}

	public void getDestObjListFromCmd(CsCommand cmd,List<Object>destList,List<Object>objList)
	{
		if(cmd != null)
		{
			Set<CommandParameter> paramSet = cmd.getParamSet();			
			
			// 1个操作涉及多个对象
			if(cmd.getId() != null && cmd.getId()!="")
			{							
				
				if(paramSet != null)
				{
					for(CommandParameter cp:paramSet)
					{				
						// 目的参数
						
						if(cp.getDest() != null)
						{				
		    				DeviceDest destData = (DeviceDest)this.getDestData(cp.getDest());
		    				destList.add(destData);
						}
						
						// 对象参数					
						if(cp.getParameter() != null)
						{
							objList.add(cp.getParameter());
						}
											
						// 键值参数
						if(cp.getObjMap() != null || cp.getParaMap() != null)
						{
							Object objMap = cp.getObjMap() != null ? cp.getObjMap() : cp.getParaMap();
							objList.add(objMap);	
						}
				
					}			
				}
			}
		}	

	}
	
	/*
	 * set operation
	 * @see com.nm.server.adapter.base.Adapter#getSendTask(int, com.huahuan.message.CsCommand, com.nm.server.adapter.base.comm.AsynTask)
	 */
	public int getSendTask(int cmdType,CsCommand cmd,AsynTask cmdTask,TaskResult tRes)
	{
		int rtn = 0;
		
		List<Object> destList = new ArrayList<Object>();
		
		CommandDestination dest = cmd.getDest();
		
		if(dest != null)
		{
			DeviceDest destData = (DeviceDest)this.getDestData(dest);
			destList.add(destData);
		}		
		
		
		Set<CommandParameter> paramSet = cmd.getParamSet();			
		
		// 1个操作涉及多个对象
		if(cmd.getId() != null && cmd.getId()!="")
		{
			
			List<Object> objList = new ArrayList<Object>();
			
			String clsName = cmd.getId();
			
			if(paramSet != null)
			{
				for(CommandParameter cp:paramSet)
				{				
					// 目的参数
					
					if(cp.getDest() != null)
					{				
	    				DeviceDest destData = (DeviceDest)this.getDestData(cp.getDest());
	    				destList.add(destData);
					}
					
					// 对象参数					
					if(cp.getParameter() != null)
					{
						objList.add(cp.getParameter());
					}
										
					// 键值参数
					if(cp.getObjMap() != null || cp.getParaMap() != null)
					{
						Object objMap = cp.getObjMap() != null ? cp.getObjMap() : cp.getParaMap();
						objList.add(objMap);	
					}
			
				}			
			}
		
			TaskCell taskCell = this.getTaskCell(cmdType,destList,clsName, objList,0,tRes, cmd.getFuncName());
			
			if(taskCell.getMsgList().isEmpty())
			{
//				tRes.setResult(ErrorMessageTable.FRAME_GET_ERROR);
				cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);
			}			
			
			cmdTask.addTaskCell(taskCell);
			
		}
		// 1个操作对应一个对象，支持多对多
		else
		{		
    		
    		for(CommandParameter cp:paramSet)
    		{			
    			String clsName = cp.getStrID();
    			
    			List<Object> objList = new ArrayList<Object>();    			
				
    			// 目的参数
    			List<Object> destListL = new ArrayList<Object>();
				if(cp.getDest() != null)
				{				
    				DeviceDest destData = (DeviceDest)this.getDestData(cp.getDest());
    				destListL.add(destData);
				}
				
				// 对象参数					
				if(cp.getParameter() != null)
				{
					objList.add(cp.getParameter());
				}
									
				// 键值参数
				if(cp.getObjMap() != null || cp.getParaMap() != null)
				{
					Object objMap = cp.getObjMap() != null ? cp.getObjMap() : cp.getParaMap();
					objList.add(objMap);	
				}
    			
    			
    			TaskCell taskCell = this.getTaskCell(cmdType,destListL, clsName, objList,0,tRes, cmd.getFuncName());
    			
    			cmdTask.addTaskCell(taskCell);
    			
    		}	
		}
		
		return rtn;
	}
	
	@Override
	public int getSyncTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes)
	{	
		
		log.info("-------------------getSyncTask------------begin-------");
		
		SyncNotifyManager.getInstance().notifyToClientProgress(
				cmdTask.getConnectionId(), 
				cmdTask.getMsgId(), 
				this.getNeId(), 
				10,
				"Init Data");
		
		int rtn = 0;
		
		// 标识上载（upload）、同步（sync）
//		String cmdId = cmd.getId();
		
		// 直接执行
//		cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);		
		
		
//		DsObserver.putDs("ds_sync");			// switch data source
		
		log.info("--------------db process ,please wait...... begin---------------");
		
		
		ManagedElement me = topoManager.getManagedElementByIdFromDB((int)this.getNeId());
		
		log.info("-------------init ne data ,please wait...... begin--------------");
		
		this.initManager.neInitConfig(me, 1,tRes);
		
		log.info("-------------get command data ,please wait......-----------");
		
		rtn = tRes.getResult() == 0 ? this.getBaseSyncTask(tRes):tRes.getResult();
		
		if(rtn == 0)
		{
			SyncNotifyManager.getInstance().notifyToClientProgress(
					cmdTask.getConnectionId(), 
					cmdTask.getMsgId(), 
					this.getNeId(), 
					20,
					"");
			
			rtn = this.getExtendSyncTask(cmdTask, 0,1,tRes);
		}
			
				
		
		if(rtn != 0 || tRes.getResult() > 0 || cmdTask.getTaskCellList().size() == 0)
		{
			cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);				
			
			log.info("TaskExecType.TASK_EXECUTED_DIRECTLY!");
		}
		

		
//		DsObserver.putDs("ds_hisys");		// switch data source
		
		log.info("-------------------db process -------end--------------");
		
		log.info("-------------------getSyncTask------------end-------");
		
		return rtn;
	}
	
	
	public int getBaseSyncTask(
//			ManagedElement me,
//			NeDescriptor neDes,
			TaskResult tRes)
	{
		
//		NeConfigManager neConfigManager = (NeConfigManager) this.appContext.getBean("neConfigManager");		

//		neConfigManager.removeSyncData(me, 1);	// remove sync data.
//		neConfigManager.setAppContext(this.appContext);
//		neConfigManager.neInitConfig(me, 1,tRes);	// remove all sync data
		
//		this.configManager.neInitConfig(me, 1,tRes);
		
		ManagedElement me = topoManager.getManagedElementById((int)this.getNeId());	// refresh ne data			
		
		HiSysMessage msgDel = this.getDelConfMessage();
		
		if(msgDel != null)
		{
			HiSysMessage msgL = TaskManager.getInstance().sendAndRevMsg(msgDel,me, 30000,tRes);
			
			if(tRes.getResult() != 0)
			{				
				return tRes.getResult();
			} else {
				this.isGetFrameValid(neDes, msgL, 1, tRes);
				
				if(tRes.getResult() != 0) {
					return tRes.getResult();
				}
			}
		}
		
		HiSysMessage msgConf = this.getBaseConfMessage();
		
		if(msgConf != null) {
			HiSysMessage msgL = TaskManager.getInstance().sendAndRevMsg(msgConf, me, 30000,tRes);
			
			if(tRes.getResult() == 0)
			{
				this.isGetFrameValid(neDes, msgL, 1, tRes);
				
				if(tRes.getResult() == 0) {
					this.parserBaseConfMessage(me, msgL, tRes);
					
					if(tRes.getResult() == 0)
					{
						this.initManager.addSyncData(me);
						
					}
				}
			}
		} else {
			this.parserBaseConfMessage(me, null, tRes);
			
			if(tRes.getResult() == 0)
			{
				this.initManager.addSyncData(me);
				
			}
		}
		
	
		return tRes.getResult();
	}
	
	protected boolean isLostEquipentsHaveLinks(int neId,List<Integer> equipIdList) {
		if(equipIdList == null || equipIdList.isEmpty()) {
			return false;
		}
		
		List<TopologicalLink> tlList = tlManager.getAllTopologicalLinks();
		
		if(tlList == null || tlList.isEmpty()) {
			return false;
		}
		
		for(int equipId : equipIdList) {
			
			Iterator<TopologicalLink> it = tlList.iterator();
			while(it.hasNext()) {
				TopologicalLink tl = it.next();
				
				if(tl.getType() == TopologicalLink.LinkType.LT_SubNeVirtual){
					continue;
				}
				
				if(tl.getFromPort().getNeId() != neId && tl.getToPort().getNeId() != neId) {
					it.remove();
					continue;
				}
				
				if(tl.getFromPort().getEquipId() == equipId || tl.getToPort().getEquipId()== equipId) {
					return true;
				}
			}
		}
		return false;
	}
	
	protected void getEquipmentHolders() {
		
	}
	
	
	public void parserBaseConfMessage(ManagedElement me ,HiSysMessage msg,TaskResult tRes)
	{
		this.parserBaseConfMessageEx(me, msg, tRes);
	}
	
	public void parserBaseConfMessageEx(ManagedElement me ,HiSysMessage msg,TaskResult tRes)
	{
		List<EquipmentHolder> neEhList = new ArrayList<EquipmentHolder>();
		List<Integer> conflictEquipIds = new ArrayList<Integer>();
		
		this.getEquipmentHolders(me, msg, neEhList, conflictEquipIds, tRes);
		
		if(tRes.getResult() == 0) {
			if(this.isLostEquipentsHaveLinks(me.getId(), conflictEquipIds)) {
				tRes.setResult(ErrorMessageTable.CFG_LOST_EQ_HAS_LINK);
			} else {
				
				if(!neEhList.isEmpty()) {
					me.getEquipmentHoldersEx().addAll(neEhList);
				}
			}
		}
	}
	
	protected void getEquipmentHolders(
			ManagedElement me ,HiSysMessage msg,
			List<EquipmentHolder> neEhList,
			List<Integer> conflictEquipIds,
			TaskResult tRes) {
		
	}
	
	protected void getConflictEquipIds(
			ManagedElement me,int iSlot, 
			String eqName,
			List<Integer> conflictEquipIds) {
		
		if(me.getEquipmentHolder(iSlot) == null) {
			log.error("slot:" + iSlot + "is lost.");
			return;
		}
		
		/* ignore slot-0*/
		if(iSlot == 0 || me.getEquipmentHolder(iSlot).getInstalledEquipment() == null) {
			return;
		}
		
		Equipment nmEq = me.getEquipmentHolder(iSlot).getInstalledEquipment();
		EquipmentDescriptor nmEqDes = neDes.getEquipmentDescriptor(nmEq.getName());
		if(nmEq != null && !nmEq.getName().equalsIgnoreCase(eqName) 
				&& (nmEqDes == null || !nmEqDes.isInSubstituteTypes(eqName))) {
			conflictEquipIds.add(nmEq.getId());
		}
	}
	
	public int getExtendSyncTask(
			AsynTask cmdTask,
//			ManagedElement me,
//			NeDescriptor neDes,
			int cmdType,
			int isSync,
			TaskResult tRes)
	{
		
		ManagedElement me = topoManager.getManagedElementById((int)this.getNeId());	// refresh ne data	
		
		/* 上载或同步时，更新网元数据*/
		this.setExtendObj(me);
		
		
		DeviceDest dest = (DeviceDest)this.getDestData();		
		
		// ne extend data		
		
		log.info("--------NE Extend data ----------------begin-------------------");

		this.getNeFunctionTask(dest,neDes.getFunctionDesList(),cmdTask, cmdType, isSync,tRes);

		log.info("--------NE Extend data ----------------end--------------------");
		
		
		SyncNotifyManager.getInstance().notifyToClientProgress(
				cmdTask.getConnectionId(), 
				cmdTask.getMsgId(), 
				this.getNeId(), 
				30,
				"Init Data");
		
		List<EquipmentHolder> ehList = new ArrayList<EquipmentHolder>();
		
		
		
		Set<EquipmentHolder> ehSet = me.getEquipmentHoldersEx();
		
		Iterator<EquipmentHolder> it = ehSet.iterator();
		
		while(it.hasNext())
		{
			
			EquipmentHolder eh = it.next();
			
			if(eh.getStorageType() == isSync)
			{
				ehList.add(eh);
			}
			
			
		}
		
		Collections.sort(ehList);
		
		// eq extend data
		
		log.info("--------EQ Extend data ----------------begin--------------------");
		
		this.getEqExtendSyncTask(dest, ehList, cmdTask, cmdType, isSync,tRes);
		
		log.info("--------EQ Extend data ----------------end--------------------");
		
		SyncNotifyManager.getInstance().notifyToClientProgress(
				cmdTask.getConnectionId(), 
				cmdTask.getMsgId(), 
				this.getNeId(), 
				40,
				"Init Data");
		
		
		return 0;
	}
	
	public void getNeFunctionTask(DeviceDest dest,List<FunctionDescriptor> funcList,
			AsynTask cmdTask,int cmdType,int isSync,TaskResult tRes)
	{
		if(tRes.getResult() > 0) {
			return;
		}
		
		if(funcList != null)
		{
			for(int funcIndex = 0; funcIndex < funcList.size(); funcIndex++)
			{
				FunctionDescriptor func = funcList.get(funcIndex);
				
				if(func.isSupported())
				{
					if(func.isSync())
					{
						String command = func.getCommand();
						
						DeviceDest destData = (DeviceDest)dest.clone();
						
						List<Object> destList = new ArrayList<Object>();
						destList.add(destData);
						
						TaskCell taskCellL = this.getTaskCell(cmdType,destList, command, null,isSync,tRes,func.getName());		
						
						if(taskCellL.getMsgList().isEmpty() || tRes.getResult() > 0)
						{
							continue;
						}
						
						cmdTask.addTaskCell(taskCellL);						
					}else{
						log.info("not supporting the synchronous function:" + func.getName());
					}
					
					
					this.getNeFunctionTask(dest, func.getSubFunctions(), cmdTask, cmdType, isSync,tRes);
				}
				
	
				
			}			
		}			
	}
		
	public int getEqExtendSyncTask(DeviceDest dest,List<EquipmentHolder> ehList,
//			NeDescriptor neDes,
			AsynTask cmdTask,int cmdType,int isSync,TaskResult tRes)
	{
		
		if(tRes.getResult() > 0) {
			return 0;
		}
		
		
		for(int ehIndex = 0; ehIndex < ehList.size(); ehIndex++)
		{
			
			EquipmentHolder eh = ehList.get(ehIndex);
			
			if(eh.getStorageType() == isSync)
			{
				Equipment eq = eh.getInstalledEquipment();				
				
				
				if(eq != null)
				{
					log.info("-------------------EQ:" + eq.getExternalName() + " getSyncTask------------begin-------");
					
					EquipmentDescriptor eqDes = neDes.getEquipmentDescriptor(
							this.initManager.getEqActualName(eq,isSync));
					
					if(eqDes != null)
					{
						dest.setSlot(eh.getLocalIndex());
						dest.setEquipType(eqDes.getEquipmentType());							
						dest.setEquipName(eqDes.getName());
						int iModule = neDes.getEquipmentMoudleType(eqDes.getName());							
						dest.setModule(iModule);
						dest.setEquipId(eq.getId());
						

						this.getNeFunctionTask(dest, eqDes.getFunctionDesList(), 
								cmdTask, cmdType, isSync,tRes);
						

						
						// 端口
						
						this.getTpExtendSyncTask(dest, eqDes.getTpSet(), cmdTask, cmdType, isSync,tRes);
						
						//递归		
						
						this.getEqExtendSyncTask(dest, eq.getSubSlotList(),cmdTask, cmdType, isSync,tRes);

					}
					
					log.info("-------------------EQ:" + eq.getExternalName() + " getSyncTask------------end-------");
					
					
				}
				
			}			
			
		}		
		
		return 0;
	}
	
	public int getTpExtendSyncTask(DeviceDest dest,
			Set<TPDescriptor> tpDesSet,
			AsynTask cmdTask,int cmdType,int isSync,TaskResult tRes) {
		
		if(tpDesSet == null || tRes.getResult() > 0) {
			return 0;
		}
		
		Iterator<TPDescriptor> it = tpDesSet.iterator();
		
		while(it.hasNext())
		{
			List<Object> destList = new ArrayList<Object>();
			
			TPDescriptor tpDes = it.next();
			
			int tpCount = tpDes.getCount();
			
			for(int i = 1; i <= tpCount; i++) {
				
				DeviceDest tpDest = dest.clone();
				
				DestUtil.setDestTpInfo(tpDest, tpDes.getSubType(), i);
				
				destList.add(tpDest);			
				
				/* 递归 */
				this.getTpExtendSyncTask(tpDest, tpDes.getTpSet(), cmdTask,cmdType,isSync, tRes);
				
			}
			
			/* 以端口为单位调用 */
			this.getTpFunctionTask(destList, tpDes.getFunctionDesList(),cmdTask, cmdType, isSync, tRes);
			
			
		}			
		
		return 0;
	}
	
	
	public void getTpFunctionTask(
			List<Object> destList,List<FunctionDescriptor> funcList,
			AsynTask cmdTask,int cmdType,int isSync,TaskResult tRes)	{
		
		if(tRes.getResult() > 0) {
			return;
		}
		
		if(funcList != null)
		{
			for(int funcIndex = 0; funcIndex < funcList.size(); funcIndex++)
			{
				FunctionDescriptor func = funcList.get(funcIndex);
				
				if(func.isSupported())
				{
					if(func.isSync())
					{
						String command = func.getCommand();
						
						
						TaskCell taskCellL = this.getTaskCell(cmdType,destList, command, null,isSync,tRes, func.getName());		
						
						if(taskCellL.getMsgList().isEmpty() || tRes.getResult() > 0)
						{
							continue;
						}
						
						cmdTask.addTaskCell(taskCellL);						
					}else{
						log.info("not supporting the synchronous functon:" + func.getName());
					}	
					
					
					this.getTpFunctionTask(destList, func.getSubFunctions(), cmdTask, cmdType, isSync,tRes);
				}
				
	
				
			}			
		}			
	}	
	
	@Override
	public int processSyncResult(CsCommand cmd, AsynTask resTask,Response rs, AsynTask cmdTask)
	{
		log.info("-------------------processSyncResult------------begin-------");
		SyncNotifyManager.getInstance().notifyToClientProgress(
				resTask.getConnectionId(), 
				resTask.getMsgId(), 
				this.getNeId(), 
				30,
				"processing data begin");		
		
		if(resTask == null || rs.getResult() != 0)
		{
			return -1;
		}
		
		if(resTask.getTaskCellList().size() != 0)
		{
			for(TaskCell taskCell:resTask.getTaskCellList())
			{
				//如果taskCell协议和adapter协议不一致，则跳过
				if(taskCell.getProtocolType() != null && !this.getProtocol().equals(taskCell.getProtocolType())){
					continue;
				}
				// 一个对象对应多个帧的情况，需要组合多个帧
				
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
				
				if(resFunc == null){
					log.info("**********************error,can not find resFunc, cmdID = " + cmd.getId() + "--" + taskCell.getStrID());
					continue;
				}
				resFunc.setFuncName(cmd.getFuncName());
				// 解析用户数据字节
				List<Object> userDataL = this.getReturnUserDataL(taskCell);	
				
//				for(HiSysMessage msg:msgList)
//				{
//					MsdhMessage msdhMsg = (MsdhMessage)msg;
//					
//					byte[] userData = msdhMsg.getFixedData();
//					
//					userDataL.add(userData);
//				}	
				
				if(userDataL != null && !userDataL.isEmpty())
				{
					TaskResult tRes = new TaskResult();
					log.info("-------------------command:" + resFunc.getCmdId()+ " updateQuery------------begin-------");
					// 更新数据库
					resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);
					log.info("-------------------command:" + resFunc.getCmdId()+ " updateQuery------------end-------");
				}
				taskCell.clear();
				taskCell = null;
		
			}
			
		}
		SyncNotifyManager.getInstance().notifyToClientProgress(
				resTask.getConnectionId(), 
				resTask.getMsgId(), 
				this.getNeId(), 
				40,
				"processing data end");				
		log.info("-------------------processSyncResult------------end-------");
		return 0;
	}
	
	@Override
	public int getAlarmTask(CsCommand cmd, AsynTask cmdTask,TaskResult tRes)
	{
		return 0;
	}


	@SuppressWarnings("unchecked")
	@Override
	public int processAlarmResult(CsCommand cmd, AsynTask resTask,
			Response rs, AsynTask cmdTask)
	{
		// 1.使用msgList 初始化objList
		// 2.使用objList中的对应资源类更新DB
		// 3.返回数据
		
		
		for(TaskCell taskCell:resTask.getTaskCellList())
		{
			//如果taskCell协议和adapter协议不一致，则跳过
			if(taskCell.getProtocolType() != null && !this.getProtocol().equals(taskCell.getProtocolType())){
				continue;
			}
			// 一个对象对应多个帧的情况，需要组合多个帧
			List<HiSysMessage> msgList = taskCell.getMsgList();				
			
			ResFunc resFunc = null;
			
			if(cmd == null)					// Trap command
			{				
				
				List<Object> destListL = new ArrayList<Object>();				
				destListL.add(this.getDestData());			
				
				taskCell.setDestList(destListL);
				
				rs.setcmdId(taskCell.getStrID());
				resFunc = this.getResFunc(taskCell.getStrID());				
			}
			else
			{
				resFunc = (ResFunc)taskCell.getObj();
			}
			
			if(resFunc == null)
			{
				log.info("No ResFunc!");
				continue;
			}
			
			// 解析用户数据字节
			List<Object> userDataL = new ArrayList<Object>();
			
			for(HiSysMessage msg:msgList)
			{
				MsdhMessage msdhMsg = (MsdhMessage)msg;
				
				byte[] userData = msdhMsg.getFixedData();
				
				userDataL.add(userData);
			}	
			
			TaskResult tRes = new TaskResult();
			
			// 更新数据库
			resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);
			
			
			// 设置返回结果
			List<Object> objList = resFunc.getResult();
			
			if(objList.size() > 0)
			{
				for(int i = 0; i< objList.size(); i++)
				{					
					CommandParameter cp = new CommandParameter();
					// 对于1:n情况，commandparameter中的strID被置为cmd的strID.
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
					
					rs.addParam(cp);					
				}
			}
	
		}		
		return 0;
	}
	
	
	public Object getBean(String name)
	{
		return this.appContext.getBean(name);
	}	
	
	public Object getDestData()	
	{	
		DeviceDest dest = new DeviceDest();
		String neType = this.getStrNetype();
		String version = this.getNeVersion();
		NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, version);		

		dest.setNeId((int)this.getNeId());
		dest.setDeviceSerial(neDes.getSeriesID());
		dest.setDeviceType(neDes.getModelID());
		dest.setNeVersion(version);
		dest.setNeType(neType);
		dest.setEquipName(equipName);
		return dest;
	}

	public Object getDestData(CommandDestination dest)
	{
		
		DeviceDest destData = new DeviceDest();
		
		//将目的数据写入destData
		destData.setUser(dest.getUser());
		/*
		 * 网元级数据
		 */
		int neId = dest.getNeId();			
		String neType = this.getStrNetype();
		String version = this.getNeVersion();
		boolean neAutoDiscovery = dest.isNeAutoDiscovery();

		NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, version);		
				
		destData.setNeId(neId);		
		destData.setNeType(neType);
		destData.setNeIp(dest.getNeIp());
		destData.setNeAutoDiscovery(neAutoDiscovery);
		if(neDes != null){
			destData.setDeviceSerial(neDes.getSeriesID());
			destData.setDeviceType(neDes.getModelID());
		}
		
		if(dest.getNeVersion() != null && !dest.getNeVersion().equals(""))
		{
			destData.setNeVersion(dest.getNeVersion());
		}
		else
		{
			destData.setNeVersion(version);
		}
		//单盘级数据
		int equipId = dest.getEquipId();
		int iSlot = dest.getSlot();
		int iPackType = dest.getEquipmentType();
		
		destData.setSlot(iSlot);
		destData.setEquipId(equipId);
		destData.setEquipType(iPackType);		
		destData.setEquipName(dest.getEquipName());
		/*
		 * 端口级数据		
		 */
		int tpId = dest.getTpId();		
		SubTpType tpType = SubTpType.values()[dest.getTpType()];
		destData.setTpId(tpId);
		destData.setTpType(tpType);
		destData.setTpIndex(dest.getTpIndex());			
		
		/*
		 * 通道级数据
		 */
		destData.setChLevel(dest.getChLevel());	
		
		destData.setVc4Id(dest.getVc4Id());
		destData.setVc4(dest.getVc4());
		
		destData.setVc3Id(dest.getVc3Id());
		destData.setVc3(dest.getVc3());
		
		destData.setVc12Id(dest.getVc12Id());
		destData.setVc12(dest.getVc12());
		
		destData.setPdhChannelId(dest.getPdhChannelId());	
		destData.setPdhChannel(dest.getPdhChannel());
		
		/*
		 * 计算模块id
		 */
		int iModule = 0;
		if(dest.getChLevel() == ChannelLevel.CL_UNKNOWN)
		{
			//单盘级模块或者端口级模块
			iModule = NeDescriptionLoader.getSpeicyModuleType(neType, iPackType, tpType);
		}
		else
		{
			//通道级模块
			if(dest.getChLevel() == ChannelLevel.CL_VC12)
			{
				iModule = NeDescriptionLoader.getSpeicyModuleType(neType, iPackType, SubTpType.VC12);
			}
			else if(dest.getChLevel() == ChannelLevel.CL_VC3)
			{
				iModule = NeDescriptionLoader.getSpeicyModuleType(neType, iPackType, SubTpType.VC3);
			}
			else if(dest.getChLevel() == ChannelLevel.CL_VC4)
			{
				iModule = NeDescriptionLoader.getSpeicyModuleType(neType, iPackType, SubTpType.VC4);
			}
			else if(dest.getChLevel() == ChannelLevel.CL_PDHCHANNEL)
			{
				iModule = NeDescriptionLoader.getSpeicyModuleType(neType, iPackType, SubTpType.PDHChannel);
			}
		}
		destData.setModule(iModule);				
		return destData;
	}	
	
	public CommandDestination getDestData(DeviceDest dest)
	{
		CommandDestination destData = null;
		
		if(dest != null)
		{
			//将目的数据写入destData
			/*
			 * 网元级数据
			 */
			
			destData = new CommandDestination();
				
			destData.setNeId(dest.getNeId());		
			destData.setNeType(dest.getNeType());
			destData.setNeVersion(dest.getNeVersion());
			destData.setNeAutoDiscovery(dest.isNeAutoDiscovery());
			
			//单盘级数据
			
			destData.setSlot(dest.getSlot());
			destData.setEquipId(dest.getEquipId());
			destData.setEquipmentType(dest.getEquipType());	
			
			/*
			 * 端口级数据		
			 */
			destData.setTpId(dest.getTpId());
			
			if(dest.getTpType() != null)
			{
				destData.setTpType(dest.getTpType().ordinal());	
			}			
					
			destData.setTpIndex(dest.getTpIndex());			
			
			/*
			 * 通道级数据
			 */
			destData.setChLevel(dest.getChLevel());	
			
			destData.setVc4Id(dest.getVc4Id());
			destData.setVc4(dest.getVc4());
			
			destData.setVc3Id(dest.getVc3Id());
			destData.setVc3(dest.getVc3());
			
			destData.setVc12Id(dest.getVc12Id());
			destData.setVc12(dest.getVc12());
			
			destData.setPdhChannelId(dest.getPdhChannelId());	
			destData.setPdhChannel(dest.getPdhChannel());			

		}
		
		
		return destData;
	}
	
	public Object getDeviceDest()
	{
		DeviceDest dest = (DeviceDest)this.getDestData();
		DeviceDest destData = (DeviceDest)dest.clone();
		
		return destData;
		
	}
	
	public Object getDeviceDest(Equipment eq)
	{
		DeviceDest dest = (DeviceDest)this.getDestData();
		DeviceDest destData = (DeviceDest)dest.clone();
		
		return destData;
		
	}	
	
	public Object getDeviceDest(TerminationPoint tp)
	{
		DeviceDest dest = (DeviceDest)this.getDestData();
		DeviceDest destData = (DeviceDest)dest.clone();
		
		return destData;
		
	}	
	
	/**
	 * 此方法的功能为获取对应的Bean
	 * Bean Id 有四种情况：
	 * (1)neType + "_" + version + "_" + cmdId
	 * (2)neType + "_" + version(最近版本) + cmdId
	 * (3)neType + "_" + cmdId
	 * (4)cmdId
	 * 查找顺序为(1)(2)(3)(4)
	 * 此种实现考虑支持设备同一功能不同版本的实现。
	 * @param cmd
	 * @return
	 */
	public ResFunc getResFunc(String clsName)
	{		
		
		String neType  = this.getStrNetype();			
			
		String version = this.getNeVersion();		
		NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, version);
		if(this.domain == null && neDes != null){
			domain = neDes.getDomain();
		}
		ResFunc resFunc = this.getResFunc(
				this.getProtocol().toString(),clsName, neType, version);
//		
//		if(resFunc == null) {
//			return this.getResFunc(clsName, neType, version);
//		}
//		if(resFunc == null && getProtocol() == CommProtocol.CP_SNMP_NEW) {
//			return this.getResFunc(clsName, "GPN", version);
//		}
		if(resFunc == null) {
			return this.getResFunc(domain, clsName);
		}
		return resFunc;
		
	}
	
	
	public ResFunc getResFunc(String protocol,String sCommand,String sNeType,String sVersion)
	{
		ResFunc resFunc = null;
		
		if(sCommand == null || sNeType == null || sNeType.equals("")) {
			return null;
		}
			
		// special implements
		List<String> verList = NeDescriptionLoader.getVersionList(sNeType);
		
		boolean startTag = false;
		
		for(int verIndex = verList.size() - 1; verIndex > 0; verIndex--) {
			if(sVersion.equals(verList.get(verIndex)) && !startTag) {
				startTag = true;
			}
			
			if(startTag) {
				
				String funcNameL = protocol + "_" + sNeType + "_" + verList.get(verIndex) + "_" + sCommand;
				
    			try	{
    				resFunc = (ResFunc) this.getBean(funcNameL);
    			} catch (NoSuchBeanDefinitionException ee) {
//		    		log.info("no bean:" + funcNameL);
    			}		
			}
			
			if(resFunc != null) {
				break;
			}
		}
		
		// common implements
		if(resFunc == null)	{
			String funcNameL = protocol + "_" + sNeType + "_" + sCommand;
			
			try	{
				resFunc = (ResFunc) this.getBean(funcNameL);
			} catch (NoSuchBeanDefinitionException ee) {
//	    		log.info("no bean:" + funcNameL);
				
				try	{
					resFunc = (ResFunc) this.getBean(protocol + "_" + sCommand);
				} catch (NoSuchBeanDefinitionException eee) {
					log.debug("no bean:" + protocol + "_" + sCommand);	
				}	
			}							
		}				
		
		if(resFunc != null && 
				(resFunc.getCmdId() == null || resFunc.getCmdId().equals("")))
		{
			resFunc.setCmdId(sCommand);
		}
		
		return resFunc;
	}
	
	public ResFunc getResFunc(String sCommand,String sNeType,String sVersion)
	{
		ResFunc resFunc = null;
		
	
		if(sCommand != null)
		{			
			
			if(sNeType != null && sNeType != "")
			{				
				
				// special implements
				List<String> verList = NeDescriptionLoader.getVersionList(sNeType);
				
				boolean startTag = false;
				
				for(int verIndex = verList.size() - 1; verIndex > 0; verIndex--)
				{
					if(sVersion.equals(verList.get(verIndex)) && !startTag)
					{
						startTag = true;
					}
					
					if(startTag)
					{
						
						String funcNameL = sNeType + "_" + verList.get(verIndex) + "_" + sCommand;
						
						
		    			try
		    			{
		    				resFunc = (ResFunc) this.getBean(funcNameL);
		    			}
		    			catch (NoSuchBeanDefinitionException ee)
		    			{
//		    				log.info("no bean:" + funcNameL);
		    				
		    			}		
		    			
					}
					
					if(resFunc != null) {
						break;
					}
				}
				
				// common implements
				if(resFunc == null)
				{
					String funcNameL = sNeType + "_" + sCommand;
					
					
	    			try
	    			{
	    				resFunc = (ResFunc) this.getBean(funcNameL);
	    			}
	    			catch (NoSuchBeanDefinitionException ee)
	    			{
//	    				log.info("no bean:" + funcNameL);
	    				
	    				try
	    				{
	    					resFunc = (ResFunc) this.getBean(sCommand);
	    				}
	    				catch (NoSuchBeanDefinitionException eee)
	    				{
	    					log.info("no bean:" + sCommand);	
	    					log.error(sCommand + " is error!");
	    				}	
	    				
	    			}							
				}				
				
			}
			else
			{
				try
				{
					resFunc = (ResFunc) this.getBean(sCommand);
				}
				catch (NoSuchBeanDefinitionException eee)
				{
					log.info("no bean:" + sCommand);	
					log.error(sCommand + " is error!");
				}	
			}
	
		}
		
		if(resFunc != null && 
				(resFunc.getCmdId() == null || resFunc.getCmdId().equals("")))
		{
			resFunc.setCmdId(sCommand);
		}
		
		return resFunc;
	}	
	
	
	public void neExtendDifConfirm(DifBase dif, AsynTask cmdTask,TaskResult tRes)
	{		
		String command = dif.getsCommand();
		
		List<Object> destListL = new ArrayList<Object>();
		destListL.add(dif);

		List<Object> objList = new ArrayList<Object>();
		
		objList.add(this.getDestData(cmdTask.getCommand().getDest()));
		
		TaskCell taskCell= this.getTaskCell(2, destListL, command, objList, 0,tRes);
		
		cmdTask.addTaskCell(taskCell);	
		
		this.neExtendDifListConfirm(dif.getSubDbList(), cmdTask,tRes);
	}
	
	public void neExtendDifListConfirm(List<DifBase>difList, AsynTask cmdTask,TaskResult tRes)
	{
		
		if(difList != null)
		{
			for(int difIndex = 0; difIndex < difList.size(); difIndex++)
			{
				this.neExtendDifConfirm(difList.get(difIndex),cmdTask,tRes);
			}
		}
	}
	
	public void eqExtendDifConfirm(EqExtendDif eqExtendDif, AsynTask cmdTask,TaskResult tRes)
	{
		if(eqExtendDif != null)
		{
			cmdTask.getCommand().getDest().setSlot(eqExtendDif.getSlot());
			cmdTask.getCommand().getDest().setEquipName(eqExtendDif.getsEqType());
			cmdTask.getCommand().getDest().setEquipmentType(eqExtendDif.getiEqType());
			
			this.neExtendDifListConfirm(eqExtendDif.getEqExtendList(),cmdTask,tRes);
			this.tpExtendDifListConfirm(eqExtendDif.getTpExtendList(),cmdTask,tRes);
			this.eqExtendDifListConfirm(eqExtendDif.getSubEqExtendList(),cmdTask,tRes);
		}
	}
	
	public void eqExtendDifListConfirm(List<EqExtendDif>eqExtendDifList, AsynTask cmdTask,TaskResult tRes)
	{
		if(eqExtendDifList != null)
		{
			for(int eedIndex = 0; eedIndex < eqExtendDifList.size(); eedIndex++)
			{
				this.eqExtendDifConfirm(eqExtendDifList.get(eedIndex),cmdTask,tRes);
			}
		}
	}
	
	public void tpExtendDifConfirm(TpExtendDif tpExtend, AsynTask cmdTask,TaskResult tRes)
	{
		if(tpExtend != null)
		{			
			
			DestUtil.setDestTpInfo(
					cmdTask.getCommand().getDest(), 
					tpExtend.getSubTpType(), tpExtend.getTpIndex());
			
			this.neExtendDifListConfirm(tpExtend.getTpExtendList(),cmdTask,tRes);
			this.tpExtendDifListConfirm(tpExtend.getSubTpExtendList(),cmdTask,tRes);
		}
		
	}
	
	public void tpExtendDifListConfirm(List<TpExtendDif>tpExtendList, AsynTask cmdTask,TaskResult tRes)
	{
		if(tpExtendList != null)
		{
			for(int tpDifIndex = 0; tpDifIndex < tpExtendList.size();tpDifIndex++)
			{
				this.tpExtendDifConfirm(tpExtendList.get(tpDifIndex),cmdTask,tRes);
			}
		}
	}	
	

	@Override
	public int getConfirmNETask(CsCommand cmd, AsynTask cmdTask,TaskResult tRes)
	{
		// ems实现
		
		List<Object> destList = new ArrayList<Object>();
		
		CommandDestination dest = cmd.getDest();
		
		if(dest != null)
		{
			DeviceDest destData = (DeviceDest)this.getDestData(dest);
			destList.add(destData);
		}		
		
		
		Set<CommandParameter> paramSet = cmd.getParamSet();			
		
		// 1个操作涉及多个对象
		if(cmd.getId() != null && cmd.getId()!="")
		{
			
			List<Object> objList = new ArrayList<Object>();
			
//			String clsName = cmd.getId();
			
			if(paramSet != null)
			{
				for(CommandParameter cp:paramSet)
				{				
					// 目的参数
					
					if(cp.getDest() != null)
					{				
	    				DeviceDest destData = (DeviceDest)this.getDestData(cp.getDest());
	    				destList.add(destData);
					}
					
					// 对象参数					
					if(cp.getParameter() != null)
					{
						objList.add(cp.getParameter());
					}
										
					// 键值参数
					if(cp.getObjMap() != null || cp.getParaMap() != null)
					{
						Object objMap = cp.getObjMap() != null ? cp.getObjMap() : cp.getParaMap();
						objList.add(objMap);	
					}			
				}			
			}
		
//			ResFunc resFunc = this.getResFunc(clsName);
			
//			resFunc.replace(destList,0);
			
			
		}
		// 1个操作对应一个对象，支持多对多
		else
		{		
    		
    		for(CommandParameter cp:paramSet)
    		{			
//    			String clsName = cp.getStrID();
    			
    			List<Object> objList = new ArrayList<Object>();    			
				
    			// 目的参数
    			List<Object> destListL = new ArrayList<Object>();
				if(cp.getDest() != null)
				{				
    				DeviceDest destData = (DeviceDest)this.getDestData(cp.getDest());
    				destListL.add(destData);
				}
				
				// 对象参数					
				if(cp.getParameter() != null)
				{
					objList.add(cp.getParameter());
				}
									
				// 键值参数
				if(cp.getObjMap() != null || cp.getParaMap() != null)
				{
					Object objMap = cp.getObjMap() != null ? cp.getObjMap() : cp.getParaMap();
					objList.add(objMap);	
				}
    			
//				ResFunc resFunc = this.getResFunc(clsName);
				
//				resFunc.replace(destList,0);    			
    			
    			
    		}	
		}		
		return 0;
	}


	@SuppressWarnings("unchecked")
	@Override
	public int getConfirmNMTask(
			CsCommand cmd, AsynTask cmdTask,TaskResult tRes) {		
		
		
		if(cmd.getDest() == null)
		{
			log.error("dest is null!");
			
			tRes.setResult(ErrorMessageTable.BADPARAM);
			
			return ErrorMessageTable.BADPARAM;
		}

		Set<CommandParameter> paramSet = cmd.getParamSet();	
		if(paramSet != null)
		{
			for(CommandParameter cp:paramSet)
			{				
				
				// 对象参数
				Object obj = cp.getParameter();
				
				if(obj != null)
				{
					List<Object> difList= 	(List<Object>)obj;
					
					
					for(int difIndex = 0; difIndex < difList.size(); difIndex++)
					{
						Object dif = difList.get(difIndex);
						
						if(dif instanceof DifBase)
						{
							this.neExtendDifConfirm((DifBase)dif, cmdTask,tRes);
							
					
						}
						else if(dif instanceof EqExtendDif)
						{
							this.eqExtendDifConfirm((EqExtendDif)dif, cmdTask,tRes);
						}					
						
					}					
				}

					
				
				
				
			}			
		}		
		
		
		return 0;
	}


	@Override
	public int processConfirmNEResult(CsCommand cmd, AsynTask resTask,
			Response rs, AsynTask cmdTask)
	{
		// 不需要执行。
		return 0;
	}


	@Override
	public int processConfirmNMResult(CsCommand cmd, AsynTask resTask,
			Response rs, AsynTask cmdTask)
	{
		// 删除同步数据
		
		Set<CommandParameter> paramSet = cmd.getParamSet();	
		
		if(paramSet.size() > 0)
		{
		
    		
			for(TaskCell taskCell:resTask.getTaskCellList())
			{				
				//如果taskCell协议和adapter协议不一致，则跳过
				if(taskCell.getProtocolType() != null && !this.getProtocol().equals(taskCell.getProtocolType())){
					continue;
				}
				ResFunc resFunc = (ResFunc)taskCell.getObj();
				
				if(resFunc == null)
				{
					log.info("No ResFunc!");
					continue;
				}				
				
				TaskResult rRes = new TaskResult();
				
				// 更新数据库
				resFunc.updateConfirmByNm(taskCell.getDestList(),null, rRes);	
		
			}	    		
		}
		
		
		
		return 0;
	}
		
	@Override
	public int getDownloadTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes)
	{
		int rtn = 0;
		
		// 直接执行
//		cmdTask.setExecType(TaskExecType.TASK_EXECUTED_DIRECTLY);	
		
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

	/*
	 * 是否满足下载条件：版本与基本配置一致？
	 */
	public int getDownloandEligible(TaskResult tRes)
	{
		if(this.getBaseConfMessage() == null) {
			return 0;
		}
		
		HiSysMessage msg = this.getBaseConfMessage();
		
		if(msg == null) {
			this.parseDownloadEligible(null, tRes);	
			return 0;
		}
		
		HiSysMessage msgL = TaskManager.getInstance().sendAndRevMsg(msg, (int)this.getNeId(),30000,tRes);
		
		if(tRes.getResult() == 0)
		{			
			this.isGetFrameValid(neDes, msgL, 0 ,tRes);
			
			if(tRes.getResult() == 0) {
				this.parseDownloadEligible(msgL, tRes);	
			}
		}	
		
		return 0;
	}
	
	public void parseDownloadEligible(HiSysMessage msg,TaskResult tRes)
	{

	}

	@Override
	public int processDownloadResult(CsCommand cmd,
			AsynTask resTask, Response rs,AsynTask cmdTask)
	{		
		for(TaskCell taskCell:resTask.getTaskCellList())
		{
			//如果taskCell协议和adapter协议不一致，则跳过
			if(taskCell.getProtocolType() != null && !this.getProtocol().equals(taskCell.getProtocolType())){
				continue;
			}
			ResFunc resFunc = (ResFunc)taskCell.getObj();
			if(resFunc ==null){
				log.error("#############taskCell.getStrID() : " + taskCell.getStrID());
				continue;
			}
			resFunc.setFuncName(cmd.getFuncName());
			
			TaskResult tRes = new TaskResult();
			List<Object> userDataL = this.getReturnUserDataL(taskCell);	
			resFunc.updateDownload(taskCell.getDestList(), userDataL, tRes);
		}
		
		
		SyncNotifyManager.getInstance().notifyToClientProgress(
				cmdTask.getConnectionId(), 
				cmdTask.getMsgId(), 
				this.getNeId(), 
				70,
				"Init Data");
		
		return 0;
	}	
	
	@Override
	public HiSysMessage getDelConfMessage()
	{
		return null;
	}	

	@Override
	public HiSysMessage getBaseConfMessage()
	{
		return null;
	}

	public void isGetFramesValid(NeDescriptor neDes,List<HiSysMessage> msgList, int isSync,TaskResult tRes) {
	
	}
	
	public void isGetFrameValid(
			NeDescriptor neDes,HiSysMessage hiMsg, int isSync,TaskResult tRes) {
	}

	public String getEquipName() {
		return equipName;
	}
	
	@Override
	public void setEquipName(String equipName) {
		this.equipName = equipName;
	}

	public String getFunctionName() {
		return functionName;
	}
	@Override
	public void setFunctionName(String funcName) {
		this.functionName = funcName;
	}
	
	
	/**
	 * 根据dest信息 获取配置中的协议类型
	 * */
	protected CommProtocol getProtocolType(DeviceDest dest, ManagedElement me, String funcName) {
		CommProtocol pt = null;
		String neType = dest.getNeType();
		String neVersion = dest.getNeVersion();
		NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, neVersion);
		if(funcName != null){
			FunctionDescriptor funcDes;
			if (me != null) {
				funcDes= FunctionDescriptorLoader.getInstance().getFuncDesBy(me, dest.getSlot(), this.isSync, funcName);
			} else {
				funcDes=neDes.getFuncDesByName(funcName);
			}
			if(funcDes == null){
				return null;
			}
			String ptStr = funcDes.getProtocol();
			pt = CommProtocol.parseCommProtocol(ptStr);
		}
		String equipName = dest.getEquipName();
		if(pt == null && neDes != null && equipName != null){
			EquipmentDescriptor equipDes = neDes.getEquipmentDescriptor(equipName);
			if(equipDes != null){
				String ptStr = equipDes.getProtocol();
				pt = CommProtocol.parseCommProtocol(ptStr);
			}
		}
		if(pt == null && neDes != null){
			String ptStr = neDes.getProtocol();
			pt = CommProtocol.parseCommProtocol(ptStr);
		}
		return pt;
	}

	public int getIsSync() {
		return isSync;
	}
	
	
}

