package com.nm.server.adapter.codec;

import java.util.List;

import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.ResFunc;

public interface CoDecFrame
{
	void combineFrame(Object destData, Object userData,HiSysMessage msg);
	void paresFrame(Object destData,ResFunc resFunc,List<HiSysMessage> msgList);
}
