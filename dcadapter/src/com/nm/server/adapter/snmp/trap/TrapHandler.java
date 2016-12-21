package com.nm.server.adapter.snmp.trap;

import java.util.List;
import java.util.Map;

import org.snmp4j.smi.VariableBinding;

import com.nm.nmm.res.ManagedElement;


public abstract class TrapHandler {
	private String oid;
	private String oidName;
	private Map<Object, Object> alarmMap; //告警类型与名称的映射
	protected ManagedElement me;
	
	
    public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public String getOidName() {
		return oidName;
	}

	public void setOidName(String oidName) {
		this.oidName = oidName;
	}
	
	public Map<Object, Object> getAlarmMap() {
		return alarmMap;
	}

	public void setAlarmMap(Map<Object, Object> alarmMap) {
		this.alarmMap = alarmMap;
	}


	public ManagedElement getMe() {
		return me;
	}

	public void setMe(ManagedElement me) {
		this.me = me;
	}

	public abstract List<Map<String, Object>> handle(VariableBinding vb);
}
