package com.nm.server.adapter.base;

public enum CommProtocol
{
	CP_UNKOWN, CP_MSDH, CP_TABS, CP_SNMP, CP_SNMP_NEW,CP_OLP, CP_CLI, CP_UDP, CP_SNMP_I, CP_SNMP_II;
	
	public static CommProtocol parseCommProtocol(String  pt) {
		CommProtocol cp = null;
		if(pt == null || pt.isEmpty()){
			return cp;
		}
		if ("snmp".equalsIgnoreCase(pt)
				|| "PT_SNMP".equalsIgnoreCase(pt)) {
			cp = CommProtocol.CP_SNMP_NEW;
		} else if ("cli".equalsIgnoreCase(pt)
				|| "PT_CLI".equalsIgnoreCase(pt)) {
			cp = CommProtocol.CP_CLI;
		} else if ("msdh".equalsIgnoreCase(pt)
				|| "PT_MSDH".equalsIgnoreCase(pt)) {
			cp = CommProtocol.CP_MSDH;
		} else if ("olp".equalsIgnoreCase(pt)) {
			cp = CommProtocol.CP_OLP;
		} else {
			cp = CommProtocol.valueOf(pt);
		}
		return cp;
	}
}
