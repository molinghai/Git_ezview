package com.nm.server.adapter.base;



import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.message.CommandDestination;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.TaskResult;

public interface Adapter
{
	public int encode(CsCommand cmd,AsynTask cmdTask,TaskResult tRes);
	public int decode(CsCommand cmd, AsynTask resTask,Response rs,AsynTask cmdTask);	
	
	public void init(ClassPathXmlApplicationContext context);		

	public void setExtendObj(Object obj);
	public Object getExtendObj();
	
	void setNeId(long neId);
	long getNeId();
	
	public void setStrNetype(String strNetype);
	public String getStrNetype();
	
	void setNeVersion(String neVersion);
	String getNeVersion();
	
	public void setProtocol(CommProtocol protocol);
	public CommProtocol getProtocol();
	
	public Object getBean(String name);
	
	public Object getDestData(CommandDestination dest);
	public Object getDestData();
	
	public HiSysMessage getDelConfMessage();
	public HiSysMessage getBaseConfMessage();
	
	
	public int getSendTask(int cmdType,CsCommand cmd,AsynTask cmdTask,TaskResult tRes);	
	
	public int processQueryResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);	
	public int processSetResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);
	
	public int getAlarmTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes);
	public int processAlarmResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);
	
	public int getSyncTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes);
	public int processSyncResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);
	
	public int getDownloadTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes);
	public int processDownloadResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);
	
	public int getConfirmNMTask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes);
	public int processConfirmNMResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);
	
	public int getConfirmNETask(CsCommand cmd,AsynTask cmdTask,TaskResult tRes);
	public int processConfirmNEResult(CsCommand cmd,AsynTask resTask,Response rs,AsynTask cmdTask);

	public void setEquipName(String equipName);
	public void setFunctionName(String funcName);
}
