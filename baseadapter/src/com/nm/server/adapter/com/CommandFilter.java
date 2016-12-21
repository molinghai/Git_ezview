package com.nm.server.adapter.com;

import java.util.Iterator;
import java.util.Map;

import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.CsCommandCollection;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.common.util.ToolUtil;

public class CommandFilter
{
	
	public static boolean isNeValid(ManagedElement me)
	{
		return true;
	}
	
	
	public static boolean isNeValid1(ManagedElement me)
	{		
		return isNeValid(me,151569) || isNeValid(me,143736) || isNeValid(me,143701);
	}
	
	
	public static  boolean isNeValid2(ManagedElement me)
	{		
		return isNeValid(me,123852) || isNeValid(me,154195) || isNeValid(me,156818);
	}	
	
	public static  boolean isNeValid3(ManagedElement me)
	{		
		return isNeValid(me,159291) || isNeValid(me,161814) || isNeValid(me,164287);
	}	
	
	public static  boolean isNeValid4(ManagedElement me)
	{
		return 
		
		isNeValid(me,51) || 
		isNeValid(me,889) || 
		isNeValid(me,967)|| 
		isNeValid(me,1045) || 
		
		isNeValid(me,11081)|| 
		isNeValid(me,11329) || 
		isNeValid(me,11277)|| 
		isNeValid(me,11300) || 
		
		isNeValid(me,21205)|| 
		isNeValid(me,21230);
	}
	
	public static  boolean isNeValid5(ManagedElement me)
	{
		return 
		
		isNeValid(me,22253) || 
		isNeValid(me,22301) || 
		isNeValid(me,32205)|| 
		isNeValid(me,32403) || 
		
		isNeValid(me,32451)|| 
		isNeValid(me,32499) || 
		isNeValid(me,42702)|| 
		isNeValid(me,42725) || 
		
		isNeValid(me,42748)|| 
		isNeValid(me,43971);
	}
	
	private static  boolean isNeValid(ManagedElement me,int neId)
	{
		if(me.getId() == neId ||
				(me.getEthGatewayInfo() != null && 	me.getEthGatewayInfo().getMainGatewayNeId() == neId))
		{
			return true;
		}		
		
		return false;		
	}	
	
	
	public static boolean isGwValid4(int neId)
	{
		return 
		neId == 22253 || 
		neId == 22301|| 
		neId == 32205|| 
		neId == 32403|| 
		
		neId == 32451|| 
		neId == 32499|| 
		neId == 42702|| 
		neId == 42725|| 
		
		neId == 42748|| 
		neId == 43971;
	}
	
	public static boolean isGwValid5(int neId)
	{
		return 
		neId == 51 || 
		neId == 889|| 
		neId == 967|| 
		neId == 1045|| 
		
		neId == 11081|| 
		neId == 11329|| 
		neId == 11277|| 
		neId == 11300|| 
		
		neId == 21205|| 
		neId == 21230;
	}
	
	
	public static  boolean isBelogToThisAdapter(CsCommandCollection ccc,Map<Integer,ManagedElement> neMap)
	{
		if(ccc != null && ccc.getCmdList() != null && !ccc.getCmdList().isEmpty())
		{
			for(CsCommand cmd : ccc.getCmdList())
			{
				CommandDestination dest = cmd.getDest();
				
				if(dest == null && cmd.getParamSet() != null && !cmd.getParamSet().isEmpty())
				{
					CommandParameter cp = (CommandParameter)cmd.getParamSet().toArray()[0];
					
					dest = cp.getDest();
				}
				
				return dest != null && isBelogToThisAdapter(dest.getNeId(),neMap);
			}
			
		}			
		

		
		return false;
	}
	
	public static boolean isBelogToThisAdapter(int neId,Map<Integer,ManagedElement> neMap)
	{
		synchronized(neMap)
		{
			return (neId == -10000) || (neId > 0 && neMap != null && neMap.get(neId) != null);
		}			
		
	}
	
	public static ManagedElement getBelogedNe(long ip,Map<Integer,ManagedElement> neMap)
	{
		synchronized(neMap)	{
			if(neMap == null) {
				return null;
			}
			
			Iterator<Integer> it = neMap.keySet().iterator();
			while(it.hasNext()) {
				ManagedElement me = neMap.get(it.next());
				
				if(me.getAddress().getIpAddress() == ip 
						|| me.getAddress().getOutBandIpAddress() == ip) {
					return me;
				}
			}
		}
		return null;
	}
	public static ManagedElement getBelogedGw(long ip,int port,Map<Integer,ManagedElement> neMap)
	{
		synchronized(neMap)	{
			if(neMap == null) {
				return null;
			}
			
			Iterator<Integer> it = neMap.keySet().iterator();
			while(it.hasNext()) {
				ManagedElement me = neMap.get(it.next());
				
				if(me.getAddress().getIpAddress() == ip 
						|| me.getAddress().getOutBandIpAddress() == ip
						|| (me.getNatIpEx().equals(ToolUtil.longToIp(ip)) && me.isNatPortExist(port))) {
					return me;
				}
			}
		}
		return null;
	}
}
