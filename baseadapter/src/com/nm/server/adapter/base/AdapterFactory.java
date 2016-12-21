package com.nm.server.adapter.base;


import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.server.adapter.base.exception.NoNeAdpaterFoundException;

public class AdapterFactory
{
	private static ClassPathXmlApplicationContext adapter = new ClassPathXmlApplicationContext("classpath*:common/server/adapter/profile/adapter.xml");

//	private static Map<AdapterDescr, Adapter> adapterMap = new HashMap<AdapterDescr, Adapter>();

	public static Adapter getNeAdapter(long neId,String strNeType, String neVersion)
			throws NoNeAdpaterFoundException
	{
//		Adapter neAdapter = adapterMap.get(new AdapterDescr(strNeType, CommProtocol.CP_MSDH));
//		if (null == neAdapter)
//		{
			Adapter	neAdapter = AdapterFactory.createAdapter(strNeType);

			if (neAdapter == null)
			{
				throw new NoNeAdpaterFoundException("can not find NE apdater for " + strNeType);
			}
//			else
//			{
//				adapterMap.put(new AdapterDescr(strNeType, CommProtocol.CP_MSDH), neAdapter);
//			}			
//
//		}
		
		neAdapter.setNeId(neId);
		neAdapter.setStrNetype(strNeType);
		neAdapter.setNeVersion(neVersion);
		
		return neAdapter;
	}




	public static Adapter createAdapter(String bean)
	{
		Adapter ada = null;
		try{
			 ada =  (Adapter) adapter.getBean(bean);
		}catch(Exception e){
//			LogFactory.getLog(AdapterFactory.class).debug(
//					"can't find adapter:" + bean);
		}
		return ada;
	}
	
	public static Adapter getNeAdapter(CommProtocol protocol,
			long neId, String neVersion, String strNeType, String equipName, String funcName){
		Adapter adapter = null;
		String strKey = null;
		if (protocol != null && equipName != null && funcName != null) {
			//按协议+板卡类型+功能名查找适配器
			strKey= protocol.toString() + "_" + equipName + "_"
					+ funcName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && equipName != null && funcName != null) {
			//按板卡类型+功能名查找适配器
			strKey = equipName + "_" + funcName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && protocol != null && funcName != null) {
			//按协议+功能名查找适配器
			strKey = protocol + "_" + funcName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && strNeType != null && funcName != null) {
			//按网元+功能名查找适配器
			strKey = strNeType + "_" + funcName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && funcName != null) {
			//按功能名查找适配器
			strKey = funcName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && equipName != null && protocol != null) {
			//按协议+板卡类型查找适配器
			strKey = protocol + "_" + equipName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && equipName != null) {
			//按板卡类型查找适配器
			strKey = equipName;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && protocol != null && strNeType != null) {
			//按协议+网元类型查找适配器
			strKey = protocol + "_" + strNeType;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if (adapter == null && strNeType != null) {
			//按网元类型查找适配器
			strKey = strNeType;
			adapter = AdapterFactory.createAdapter(strKey);
		}
		if(adapter != null){
			adapter.setNeId(neId);
			adapter.setStrNetype(strNeType);
			adapter.setNeVersion(neVersion);
			if(equipName != null){
				adapter.setEquipName(equipName);
			}
			if(funcName != null){
				adapter.setFunctionName(funcName);
			}
		}else{
			LogFactory.getLog(AdapterFactory.class).error("can't find adapter:" + strNeType);
		}
		return adapter;
		
	}
	
	public static Adapter getNeAdapter(CommProtocol protocol,
			long neId,String strNeType, String neVersion) 
	throws NoNeAdpaterFoundException {
		
		if(protocol != null) {
			String strKey = protocol.toString() + "_" + strNeType;
			
			try {
				Adapter	neAdapter = AdapterFactory.createAdapter(strKey);
				
				neAdapter.setNeId(neId);
				neAdapter.setStrNetype(strNeType);
				neAdapter.setNeVersion(neVersion);
				neAdapter.setProtocol(protocol);
				
				return neAdapter;
				
			} catch(Exception e){
				LogFactory.getLog(AdapterFactory.class).debug(
						"can't find adapter:" + strKey);
			}
		}
		
//		return getNeAdapter(neId,strNeType, neVersion);
		return null;
	}
}
