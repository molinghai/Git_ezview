package com.nm.server.adapter.base.comm;

import java.util.ArrayList;
import java.util.List;

import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.server.adapter.base.TaskExecType;
import com.nm.server.common.config.DeviceDest;

public class AsynTask implements Cloneable
{
	private String connectionId;
	private String msgId;
	
	
	private TaskExecType execType = TaskExecType.TASK_EXECUTED_ASYN;// Task executing type.		
	
	private CsCommand cmd;
	private Response response;
	private DeviceDest deviceDest;
	private List<TaskCell> taskCellList= new ArrayList<TaskCell>();	
	
	
	public AsynTask()
	{
	}
		
	
	public AsynTask(DeviceDest deviceDest)
	{
		this.deviceDest = deviceDest;
	}
	
	
	public AsynTask(CsCommand cmd)
	{
		this.cmd = cmd;
	}
	
	public AsynTask(CsCommand cmd,Response res)
	{
		this.cmd = cmd;
		this.response = res;
	}	
	
	public void setConnectionId(String connectionId)
	{
		this.connectionId = connectionId;
	}


	public String getConnectionId()
	{
		return connectionId;
	}


	public void setMsgId(String msgId)
	{
		this.msgId = msgId;
	}


	public String getMsgId()
	{
		return msgId;
	}


	public void setExecType(TaskExecType execType)
	{
		this.execType = execType;
	}


	public TaskExecType getExecType()
	{
		return execType;
	}	
	
	public void setCommand(CsCommand cmd)
	{
		this.cmd = cmd;
	}
	
	public CsCommand getCommand()
	{
		return this.cmd;
	}
	
	public void setResponse(Response response)
	{
		this.response = response;
	}


	public Response getResponse()
	{
		return response;
	}


	public void setDeviceDest(DeviceDest deviceDest)
	{
		this.deviceDest = deviceDest;
	}


	public DeviceDest getDeviceDest()
	{
		return deviceDest;
	}


	public void setTaskCellList(List<TaskCell> taskCellList)
	{
		this.taskCellList = taskCellList;
	}
	
	public List<TaskCell> getTaskCellList()
	{
		return this.taskCellList;
	}
	
	public void addTaskCell(TaskCell taskCell)
	{
		if(this.taskCellList == null)
		{
			this.taskCellList= new ArrayList<TaskCell>();
		}
		
		if(!this.isTaskCellExists(taskCell))
		{
			this.taskCellList.add(taskCell);
		}		
		
	}
	
	private boolean isTaskCellExists(TaskCell tc)
	{
		for(TaskCell tcL : this.taskCellList)
		{
			if(tc.equals(tcL))
			{
				return true;
			}
		}
		
		
		return false;
	}
	
	public TaskCell getTaskCell(int index)
	{
		if(this.taskCellList == null)
		{
			return null;
		}
		
		return this.taskCellList.get(index);
	}		
		
	public AsynTask clone()
	{
		AsynTask o = null;
		
		try
		{
			o = (AsynTask)super.clone();
			
			
			if(this.cmd != null)
			{
				o.setCommand(this.cmd.clone());
			}
			
			if(this.response != null)
			{
				o.setResponse(this.response.clone());
			}	
			
			if(this.deviceDest != null)
			{
				o.setDeviceDest(this.deviceDest.clone());
			}
			
			o.taskCellList = new ArrayList<TaskCell>();
			
			for(int i = 0; i < this.taskCellList.size(); i++)
			{
				TaskCell taskCellClone = this.taskCellList.get(i).clone();
				
				o.addTaskCell(taskCellClone);
			}
		}
		catch (CloneNotSupportedException e)
		{
			System.out.println("AsynTask clone exception.");
		}
		
		return o;
	}
	public void clear()
	{
		if(this.cmd != null)
		{
			this.cmd = null;
		}
		
		if(this.response != null)
		{
			this.response = null;
		}
		
		if(this.deviceDest != null)
		{
			this.deviceDest = null;
		}
		
		if(this.taskCellList != null)
		{
			for(TaskCell taskCell : this.taskCellList)
			{
				taskCell.clear();
				taskCell = null;
			}
			
			this.taskCellList.clear();
			this.taskCellList = null;
		}
	}	
	
}
