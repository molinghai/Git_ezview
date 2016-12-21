package com.nm.server.adapter.olp;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.server.adapter.base.BaseAdapter;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.OlpMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.mstp.MstpAdapter;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.config.TaskResult;

public class OlpAdapter extends BaseAdapter {
	public HiSysMessage getMsg(Object destData, Object userData,
			ResFunc resFunc, TaskResult tRes) {
		OlpMessage msg = null;

		if (userData != null && resFunc != null) {
			if(userData instanceof String){
				String userDataStr = (String)userData;
				msg = new OlpMessage(userDataStr);
			}
		} else {
			LogFactory.getLog(MstpAdapter.class).info(
					"-----" + resFunc.getCmdId()
							+ "'s UserData is empty!-----");
		}

		return msg;
	}

	public List<Object> getReturnUserDataL(TaskCell taskCell) {
		List<Object> userDataL = null;

		if (taskCell != null) {
			List<HiSysMessage> msgList = taskCell.getResMsgList();

			if (msgList != null && msgList.size() > 0) {
				// 解析用户数据字节
				userDataL = new ArrayList<Object>();

				for (HiSysMessage msg : msgList) {
					OlpMessage olpMsg = (OlpMessage) msg;
					userDataL.add(olpMsg.getUserDataStr());
				}
			}

		}

		return userDataL;
	}
	
	@Override
	public int processSyncResult(CsCommand cmd, AsynTask resTask, Response rs,
			AsynTask cmdTask) {
		LogFactory.getLog(MstpAdapter.class).info("-------------------processSyncResult------------begin-------");
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
					LogFactory.getLog(MstpAdapter.class).info("**********************error,can not find resFunc, cmdID = " + cmd.getId() + "--" + taskCell.getStrID());
					continue;
				}
				resFunc.setFuncName(cmd.getFuncName());
				// 解析用户数据字节
				List<Object> userDataL = new ArrayList<Object>();
				
				for(HiSysMessage msg:msgList)
				{
					OlpMessage msdhMsg = (OlpMessage)msg;
					
					byte[] userData = msdhMsg.getUserData();
					
					userDataL.add(userData);
				}	
				
				if(!userDataL.isEmpty())
				{
					TaskResult tRes = new TaskResult();
					LogFactory.getLog(MstpAdapter.class).info("-------------------command:" + resFunc.getCmdId()+ " updateQuery------------begin-------");
					// 更新数据库
					resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);
					LogFactory.getLog(MstpAdapter.class).info("-------------------command:" + resFunc.getCmdId()+ " updateQuery------------end-------");
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
		LogFactory.getLog(MstpAdapter.class).info("-------------------processSyncResult------------end-------");
		return 0;
	}
}
