package com.nm.server.adapter.snmp.trap;

import java.util.List;
import java.util.Map;

import org.snmp4j.PDU;
import org.springframework.context.ApplicationContext;

import com.nm.nmm.res.ManagedElement;
import com.nm.server.common.util.ToolUtil;

public abstract class NeTrapHandler {
	protected String command = null;
	protected String ip = null;
	protected ManagedElement me = null;
	protected Integer trapPort = null;
	
	static protected Boolean isFilterBySubTpType = null;
	static{
		loadAlarmFilterBySubTpType();
	}
	
	protected ApplicationContext context;

	public ApplicationContext getContext() {
		return context;
	}

	public void setContext(ApplicationContext context) {
		this.context = context;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}
	
	public String getIp(){
		return ip;
	}
	public void setIp(String ip){
		this.ip = ip;
	}
	
	public ManagedElement getMe() {
		return me;
	}

	public void setMe(ManagedElement me) {
		this.me = me;
	}
	
	public void setTrapPort(Integer port){
		trapPort = port;
	}
	
	public Integer getTrapPort(){
		return trapPort;
	}
	
	static public Boolean loadAlarmFilterBySubTpType(){
		if(isFilterBySubTpType == null){
			String los = ToolUtil.getProperty("ALARM_REPORT_FILTER_BY_SUBTPTYPE");
			if(los != null){
				if (1 == Integer.valueOf(los).intValue()) {
					isFilterBySubTpType = true;
				}else{
					isFilterBySubTpType = false;
				}
			}
		}
		if(isFilterBySubTpType == null){
			isFilterBySubTpType = true;
		}
		return isFilterBySubTpType;
	}

	public abstract List<Map<String, Object>> handle(PDU pdu);
}
