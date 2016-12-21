package com.nm.server.adapter.codec;

import java.util.List;

import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.ResFunc;

public class CoDecFrameC extends CoDecFrameD
{

	@Override
	public void combineFrame(Object destData,Object userData, HiSysMessage msg)
	{
		assert(destData != null && userData != null && msg != null);
		
		
	}

	@Override
	public void paresFrame(Object destData,ResFunc obj,List<HiSysMessage> msgList)
	{

	}

}
