package com.nm.server.adapter.codec;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;

public class CoDecFrameD implements CoDecFrame
{

	@Override
	public void combineFrame(Object destData, Object userData,
			HiSysMessage msg)
	{

	}

	@Override
	public void paresFrame(Object destData,ResFunc resFunc,List<HiSysMessage> msgList)
	{

	}
	
	public byte[] getDestData(Object destData,int len)
	{
		DeviceDest destDataB = (DeviceDest)destData;
		
		byte[] destB = new byte[8];
		
		int index = 0;
		destB[index++] = (byte)destDataB.getDeviceSerial();
		destB[index++] = (byte)destDataB.getDeviceType();
		destB[index++] = (byte)destDataB.getSlot();			
		destB[index++] = (byte)destDataB.getModule();
		destB[index++] = (byte)destDataB.getTpIndex();		
	
		
		
		return destB;
		
	}
	
	
	public void setFrameData(Object destData,int codecType,HiSysMessage msg,Object userData)
	{
		
		byte[] userDataB = (byte[])userData;
		
		
		// 根据帧的组合方式，计算设备信息的长度（设备系列、设备类型、槽位号、模块类型、端口号等信息）
		int incLen = this.getIncLen(codecType);
		
		byte[] destDataB = this.getDestData(destData, incLen);
		
		// userData数组的前两位为：复帧标志、组合方式
		byte[] fixedData = new byte[userDataB.length + incLen];		
		
		int len = userDataB.length + incLen;				

		// 设置命令字
		System.arraycopy(userDataB, 2, fixedData, 0, 2);
		// 设置设备信息
		System.arraycopy(destDataB, 0, fixedData, 2, incLen);		
		// 设置用户数据
		System.arraycopy(userDataB, 4, fixedData, 2 + incLen, userDataB.length -2);
		
		MsdhMessage msg_ = (MsdhMessage)msg;
		
		try
		{
			msg_.setFixedData(fixedData, len);
		}
		catch (Exception e)
		{
			LogFactory.getLog(CoDecFrameD.class).error("setFrameData exception:", e);
		}
		
		//构造帧（帧序号默认设置为0）
		// 在发送的时候再构造也可以
//		msg_.buildFrame(0);
	}
	
	public void setObjData(Object destData,int codecType,ResFunc resFunc,List<HiSysMessage> msgList)
	{
		
		int incLen = this.getIncLen(codecType);	
		
		List<byte[]> userDataList = new ArrayList<byte[]>();
		
		for(HiSysMessage msg:msgList)
		{
			MsdhMessage msg_ = (MsdhMessage)msg;
			byte[] fixedData = msg_.getFixedData();
			byte[] userData = new byte[fixedData.length -incLen];			
			
			System.arraycopy(fixedData, 2, destData, 0, incLen);
			System.arraycopy(fixedData, 2 + incLen, userData, 0, fixedData.length - 2 - incLen);
			
			userDataList.add(userData);			
		}
		
//		obj.initObjList(userDataList);
		
//		List<Constructor> objList = new ArrayList<Constructor>();				
//		objList.add(obj);
//		
//		for(int i = 0; i < obj.getMutiObj(); i++)
//		{
//			Constructor obj_ = (Constructor)obj.clone();
//			objList.add(obj_);
//		}		
		
	}
	
	public int getIncLen(int codecType)
	{		
		
		int incLen = 0;
		
		if(incLen > 1)
		{
			incLen = codecType;
		}		
		
		return incLen;
	}

}
