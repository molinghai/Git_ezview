package com.nm.server.adapter.base.comm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.nm.descriptor.FunctionDescriptor;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.util.ToolUtil;

public class SnmpNewMessage extends BaseMessage {
	private String address;
	private String commandId;
	private String funcName;
	private List<String> subFuncNames;
	private List<Map<String, Object>> values;
	private List<Map<String, Object>> conditions;
	private List<String> clis;
	
	private Map<String,  List<Map<String, Object>>> subValues = new HashMap<String,  List<Map<String, Object>>>();
	private Map<String,  List<Map<String, Object>>> subConditions = new HashMap<String,  List<Map<String, Object>>>();
	private List<String> fields;
	private Integer opType;//"add=0""delete=1""update=2""query=3"
	private FunctionDescriptor fd ;
	private DeviceDest deviceDest = null;
	private List<Integer> backupSlots = null;
	
	public FunctionDescriptor getFd() {
		return fd;
	}
	public void setFd(FunctionDescriptor fd) {
		this.fd = fd;
	}
	public void setOpType(Integer opType){
		this.opType = opType;
	}
	public Integer getOpType(){
		return opType;
	}
	
	public List<String> getClis() {
		return clis;
	}
	public void setClis(List<String> clis) {
		this.clis = clis;
	}
	public void setCommandId(String commandId){
		this.commandId = commandId;
	}
	public void setFuncName(String funcName){
		this.funcName = funcName;
	}
	public String getFuncName(){
		return funcName;
	}
	public void setSubFuncNames(List<String> subFuncNames){
		this.subFuncNames = subFuncNames;
	}
	public List<String> getSubFuncNames(){
		return subFuncNames;
	}

	public void setValues(List<Map<String, Object>> values){
		this.values = values;
	}
	public List<Map<String, Object>> getValues(){
		return values;
	}
	
	public void setSubValues(String subFuncName,  List<Map<String, Object>> values){
		this.subValues.put(subFuncName, values);
	}
	public void setSubValues(Map<String,  List<Map<String, Object>>> valueMap){
		this.subValues = valueMap;
	}
	public List<Map<String, Object>> getSubValues(String subFuncName){
		if(subValues == null){
			return null;
		}
		return subValues.get(subFuncName);
	}
	
	public Map<String, List<Map<String, Object>>> getSubValuesMap(){
		return subValues;
	}
	
	public void setConditions(List<Map<String, Object>> conditions){
		this.conditions = conditions;
	}
	
	public List<Map<String, Object>> getConditions(){
		return conditions;
	}
	
	public FunctionDescriptor getFunctionDescriptor(){
		return fd;
	}
	public void setFunctionDescriptor(FunctionDescriptor fd){
		this.fd = fd;
	}
	
	public void setSubConditions(String subFuncName,  List<Map<String, Object>> values){
		this.subConditions.put(subFuncName, values);
	}
	public void setSubConditions(Map<String,  List<Map<String, Object>>> valueMap){
		this.subConditions = valueMap;
	}
	public List<Map<String, Object>> getSubConditions(String subFuncName){
		if(subConditions == null){
			return null;
		}
		return subConditions.get(subFuncName);
	}
	
	public void setFields(List<String> fields){
		this.fields = fields;
	}
	public List<String> getFields(){
		return fields;
	}
	
	public String getAddress(){
    	return address;
    }
    public void setAddress(String address){
    	this.address = address;
    }
    @Override
	public byte[] getSrcAddress()
	{
    	String strAddr = address.toString();
	    int index = strAddr.lastIndexOf('/');
	    if(index == -1){
	    	return ToolUtil.ipStringToBytes(strAddr);
	    }
		return ToolUtil.ipStringToBytes(strAddr.substring(0, index));
	}
	
	@Override
	public int getCommadSerial() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getCommandId() {
		// TODO Auto-generated method stub
		return commandId;
	}

	@Override
	public byte[] getDestAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getFrameSerial() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getSynchnizationId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSynchnizationId(boolean isSrcMsg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(HiSysMessage msg) {
		// TODO Auto-generated method stub

	}

	@Override
	public SnmpNewMessage clone()
	{
		SnmpNewMessage msg = (SnmpNewMessage)super.clone();
		if(this.subFuncNames != null)
		{
			msg.subFuncNames = new ArrayList<String>();
			for(String obj:subFuncNames)
			{
				msg.subFuncNames.add(obj);
			}
		}
		if(this.values != null)
		{
			msg.values = new ArrayList<Map<String, Object>>();
			for(Map<String, Object> map:values)
			{
				Map<String, Object> v = new HashMap<String, Object>();
				Iterator<String> it = map.keySet().iterator();
				
				while(it.hasNext())
				{
					String key = it.next();
					v.put(key, map.get(key));
				}
				msg.values.add(v);
			}
		}
		if(this.conditions != null)
		{
			msg.conditions = new ArrayList<Map<String, Object>>();
			for(Map<String, Object> map : conditions)
			{
				Map<String, Object> c = new HashMap<String, Object>();
				Iterator<String> it = map.keySet().iterator();
				
				while(it.hasNext())
				{
					String key = it.next();
					c.put(key, map.get(key));
				}
				msg.conditions.add(c);
			}
		}
		if(this.subConditions != null)
		{
			msg.subConditions = new HashMap<String,  List<Map<String, Object>>>();
			Iterator<String> iter = this.subConditions.keySet().iterator();
			while(iter.hasNext()){
				String kk = iter.next();
				List<Map<String, Object>> subVals = new ArrayList<Map<String, Object>>();
				List<Map<String, Object>> subVs = this.subConditions.get(kk);
				for(Map<String, Object> sv : subVs)
				{
					Map<String, Object> subV = new HashMap<String, Object>();
					Iterator<String> it = sv.keySet().iterator();
					
					while(it.hasNext())
					{
						String key = it.next();
						subV.put(key, sv.get(key));
					}
					subVals.add(subV);
				}
				msg.subConditions.put(kk, subVals);
			}
		}
		if(this.subValues != null)
		{
			msg.subValues = new HashMap<String,  List<Map<String, Object>>>();
			Iterator<String> iter = this.subValues.keySet().iterator();
			while(iter.hasNext()){
				String kk = iter.next();
				List<Map<String, Object>> subVals = new ArrayList<Map<String, Object>>();
				List<Map<String, Object>> subVs = this.subValues.get(kk);
				for(Map<String, Object> sv : subVs)
				{
					Map<String, Object> subV = new HashMap<String, Object>();
					Iterator<String> it = sv.keySet().iterator();
					
					while(it.hasNext())
					{
						String key = it.next();
						subV.put(key, sv.get(key));
					}
					subVals.add(subV);
				}
				msg.subValues.put(kk, subVals);
			}
		}
		return msg;
	}
	
	public DeviceDest getDeviceDest() {
		return deviceDest;
	}
	public void setDeviceDest(DeviceDest deviceDest) {
		this.deviceDest = deviceDest;
	}
	public List<Integer> getBackupSlots() {
		return backupSlots;
	}
	public void setBackupSlots(List<Integer> backupSlots) {
		this.backupSlots = backupSlots;
	}
	
	
}
