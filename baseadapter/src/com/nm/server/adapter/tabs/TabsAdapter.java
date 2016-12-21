package com.nm.server.adapter.tabs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.annotation.Transactional;

import com.nm.descriptor.NeDescriptor;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.adapter.base.BaseAdapter;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.base.comm.TabsMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.config.TaskResult;
import com.nm.server.common.util.ToolUtil;

public class TabsAdapter extends BaseAdapter
{
	protected static ThreadLocal<ManagedElement> threadMe = new ThreadLocal<ManagedElement>();
	private Log log = LogFactory.getLog(this.getClass());
	
	@SuppressWarnings("unchecked")
	public HiSysMessage getMsg(Object destData,Object userData,ResFunc resFunc,TaskResult tRes)
	{		
		TabsMessage msg = null;
		
		if(userData != null && resFunc != null)
		{
			byte[] userDataB = null;
			
			if(userData instanceof byte[])
			{
				userDataB = (byte[])userData;
			}
			else if(userData instanceof List<?>)
			{
				
				List<Byte> userDataByte = (List<Byte>)userData;
				
				userDataB = new byte[userDataByte.size()];
				
				for(int index = 0; index < userDataByte.size(); index++)
				{
					userDataB[index] = userDataByte.get(index).byteValue();
				}
				
				userDataByte.clear();
			}				
			
			if(userDataB != null && userDataB.length > 0)
			{
				msg = new TabsMessage();
				
	    		try
	    		{
	    			msg.setFixedData(userDataB,userDataB.length);
	    			if(this.getExtendObj() != null) {
						ManagedElement me = (ManagedElement)this.getExtendObj();
						int tabs = me.getAddress().getTabsAddress();
						int node = me.getAddress().getNodeAddress();
						msg.setDstAddress(tabs);
						msg.setTabsAddr(tabs + "-" + node);
						msg.setSrcAddr(ToolUtil.getHostIp());
					}
	    			msg.setCmdLength(userDataB.length);
	    			
	    		}
	    		catch (Exception e)
	    		{
	    			LogFactory.getLog(
	    					TabsAdapter.class).error("getMsg exception:",e);
	    			tRes.setResult(ErrorMessageTable.FRAME_ERROR);
	    		}
			} else {
				LogFactory.getLog(TabsAdapter.class).info(
						"-----" + resFunc.getCmdId() + "'s UserData is empty!-----");
			}
			

		}
		

		
		
		return msg;
	}
	
	public List<Object> getReturnUserDataL(TaskCell taskCell)
	{
		List<Object> userDataL = null;
		
		try{
			if(taskCell != null)
			{
				List<HiSysMessage> msgList = taskCell.getResMsgList();
				
				if(msgList != null && msgList.size() > 0)
				{
					// 解析用户数据字节
					userDataL = new ArrayList<Object>();
					
					for(HiSysMessage msg:msgList)
					{
						TabsMessage tabsMsg = (TabsMessage)msg;
						byte[] userData = tabsMsg.getFixedData();
						userDataL.add(userData);
					}	
					
				}			

			}	
		}catch(Exception e) {
			LogFactory.getLog(TabsAdapter.class).error(
					"getReturnUserDataL exception:", e);
		}
		
		return userDataL;
	}
	
	public void isGetFramesValid(
			NeDescriptor neDes,List<HiSysMessage> msgList, int isSync,TaskResult tRes) {
		
		if(msgList == null || msgList.isEmpty()) {
			return;
		}
		
		for(Object obj : msgList) {
			TabsMessage msg = (TabsMessage)obj;
			
			this.isGetFrameValid(neDes, msg,isSync, tRes);
			
			if(tRes.getResult() != 0) {
				return;
			}
				
		}
	}
	
	protected void isGetFrameValid(
			NeDescriptor neDes,byte[]userData, int isSync, TaskResult tRes) {
			
		ResFunc resFunc = this.getResFunc(
				"FrameValid",
				neDes.getProductName(),
				neDes.getVersion());
		
		if(resFunc != null) {
			resFunc.setSync(isSync);
			resFunc.setExtendObj(this.extendObj);
			resFunc.isGetFrameValid(neDes, userData, tRes);
		} else {
			this.isGetFrameValidDefault(neDes, userData, tRes);
		}
	}
	
	private void isGetFrameValidDefault(
			NeDescriptor neDes,byte[]userData, TaskResult tRes) {
			
		if(userData == null || userData.length == 0) {
			return;
		}
		
		
//		if(userData[2] == (byte)neDes.getSeriesID() 
//				&& (userData[3] == (byte)neDes.getModelID())) {
//			
//			if(userData[4] != 0) {
////				tRes.setResult(20000 + userData[4]);
//				return;
//			}
//		} else {
//			tRes.setResult(ErrorMessageTable.NE_TYPE_MISMACTH);
//			return;
//		}
	}
	
	
	public String bytesToHexString(byte[] bArray) {
		StringBuffer sb = new StringBuffer(bArray.length);
		String sTemp;
		for (int i = 0; i < bArray.length; i++) {
			sTemp = Integer.toHexString(0xFF & bArray[i]);
			if (sTemp.length() < 2)
				sb.append(0);
			sb.append(sTemp.toLowerCase());
		}
		return sb.toString();
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
				
				// 一个对象对应多个帧的情况，需要组合多个帧
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
				
				if(resFunc == null){
					log.info("**********************error,can not find resFunc, cmdID = " + cmd.getId() + "--" + taskCell.getStrID());
					continue;
				}
				resFunc.setFuncName(cmd.getFuncName());
				// 解析用户数据字节
				List<Object> userDataL = new ArrayList<Object>();
				
				for(HiSysMessage msg:msgList)
				{
					TabsMessage tabsMsg = (TabsMessage)msg;
					int neId = ((DeviceDest)taskCell.getDestList().get(0)).getNeId();
					ManagedElement me = this.topoManager.getManagedElementById(neId);
					TaskResult tres = new TaskResult();
					checkTabsAddr(tabsMsg, me, tres);
					if(tres.getResult() != 0){
						rs.setResult(tres.getResult());
						return tres.getResult();
					}
					byte[] userData = tabsMsg.getFixedData();
					userDataL.add(userData);
					
				}	
				
				if(!userDataL.isEmpty())
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
			
			this.isGetFramesValid(neDes, taskCell, 0, tRes, cmd.getId());//改从command id取做对比 --zhaoliang
			
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
	
	private void isGetFramesValid(NeDescriptor neDes, TaskCell taskCell, int i,
			TaskResult tRes, String funcName) {
		this.isGetFramesValid(neDes, taskCell.getResMsgList(), 0, tRes);
		List<HiSysMessage> msgList = taskCell.getResMsgList();
		for(HiSysMessage msg : msgList){
			TabsMessage tabsMsg = (TabsMessage)msg;
			int neId = ((DeviceDest)taskCell.getDestList().get(0)).getNeId();
			ManagedElement me = this.topoManager.getManagedElementById(neId);
			if("getNENodeAddress".equals(funcName)){
				continue;
			}
			checkTabsAddr(tabsMsg, me, tRes);
		}
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
	
	private void checkTabsAddr(TabsMessage tabsMsg, ManagedElement me, TaskResult tRes) {
		if(me != null){
			int tabsAddr = me.getAddress().getTabsAddress();
			int nodeAddr = me.getAddress().getNodeAddress();
			String msgTabsAddr = tabsMsg.getTabsAddr();
			int tAddr = Integer.parseInt(msgTabsAddr.split("-")[0]);
			int nAddr = Integer.parseInt(msgTabsAddr.split("-")[1]);
			if(tabsAddr != tAddr){
				tRes.setResult(ErrorMessageTable.COMM_TABS_ADDRESS_ERROR);
				return;
			}
			if(nodeAddr != nAddr){
				tRes.setResult(ErrorMessageTable.COMM_TABS_NODE_ADDRESS_ERROR);
			}
		}
	}
}