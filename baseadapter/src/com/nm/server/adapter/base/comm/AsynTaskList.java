package com.nm.server.adapter.base.comm;

import java.util.ArrayList;
import java.util.List;

import com.nm.server.adapter.base.TaskType;


public class AsynTaskList implements Cloneable
{
	private TaskType taskType = TaskType.TASK_COMMON;
	
	
	private List<AsynTask> asynTaskList= new ArrayList<AsynTask>();
	
	public AsynTaskList()
	{
		
	}
	
	public AsynTaskList(TaskType taskType)
	{
		this.taskType = taskType;
	}
	
	public void setTaskType(TaskType taskType)
	{
		this.taskType = taskType;
	}

	public TaskType getTaskType()
	{
		return taskType;
	}

	
	public void setTaskList(List<AsynTask> taskList)
	{
		this.asynTaskList = taskList;
	}
	
	public List<AsynTask> getTaskList()
	{
		return this.asynTaskList;
	}
	
	
	public void addTask(AsynTask task)
	{
		if(this.asynTaskList == null)
		{
			this.asynTaskList= new ArrayList<AsynTask>();
		}
		
		this.asynTaskList.add(task);
	}
	
	public AsynTask getTask(int index)
	{
		if(this.asynTaskList == null)
		{
			return null;
		}
		
		return this.asynTaskList.get(index);
	}	
	
	public int getTaskIndex(AsynTask tc)
	{
		for(int i = 0; i < asynTaskList.size(); i++)
		{
			if(this.asynTaskList.get(i) == tc)
			{
				return i;
			}
		}
		
		return -1;
	}
	
	public AsynTaskList clone()
	{
		AsynTaskList o = null;
		
		try
		{
			o = (AsynTaskList)super.clone();
			
			o.asynTaskList = new ArrayList<AsynTask>();
			
			for(AsynTask task:this.asynTaskList)
			{
				AsynTask taskClone = task.clone();
				
				o.addTask(taskClone);
			}
		}
		catch (CloneNotSupportedException e)
		{
			System.out.println("AsynTaskList clone exception");
		}
		
		return o;
	}
	
	public void clear()
	{
		if(this.asynTaskList != null)
		{
			for(AsynTask task:this.asynTaskList)
			{
				task.clear();
				task = null;
			}
			
			this.asynTaskList.clear();
			this.asynTaskList = null;
		}
	}
}
