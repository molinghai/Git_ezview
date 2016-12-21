package com.nm.server.adapter.codec;

import java.util.List;

import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.ResFunc;


public class CoDecFrameCESM extends CoDecFrameD
{

	@Override
	public void combineFrame(Object destData, Object userData,
			HiSysMessage msg)
	{
		assert(destData != null && userData != null && msg != null);
		
		this.setFrameData(destData, 3, msg, userData);

	}

	@Override
	public void paresFrame(Object destData,ResFunc resFunc,List<HiSysMessage> msgList)
	{
		assert(resFunc != null && msgList != null);
		
		this.setObjData(destData,3, resFunc, msgList);

	}

}
