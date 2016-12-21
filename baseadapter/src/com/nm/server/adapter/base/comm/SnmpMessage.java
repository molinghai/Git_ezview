package com.nm.server.adapter.base.comm;

import org.snmp4j.PDU;
import org.snmp4j.smi.VariableBinding;

import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.snmp.OidLoader;
import com.nm.server.common.util.ToolUtil;

public class SnmpMessage extends BaseMessage
{
    private PDU pdu;
    private String address;
    public void setPdu(PDU pdu){
	   this.pdu = pdu;
    }
    public PDU getPdu(){
	   return pdu;
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
	    	index = strAddr.length();
	    }
		return ToolUtil.ipStringToBytes(strAddr.substring(0, index));
	}

	@Override
	public byte[] getDestAddress()
	{
		return null;
	}	
    @Override
	public int getCommadSerial() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public String getCommandId() {
		// TODO Auto-generated method stub
		if(pdu != null && neType != null){
			VariableBinding vb = pdu.get(0);
			if(vb != null){
				String oidStr = vb.getOid().toString();
				String oidName = OidLoader.getInstance().getOidName(neType, oidStr);
				if(oidName == null){
					for(int i = 0; i < 4; i++){
						int index = oidStr.lastIndexOf('.');
						oidName = OidLoader.getInstance().getOidName(neType, oidStr.substring(0, index));
						if(oidName != null){
							break;
						}
					}
				}
				if(oidName != null){
					return neType + "_" + oidName;
				}else{
					return neType;
				}
				
			}
		}
		return "";
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
	public String getSynchnizationId(boolean arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(HiSysMessage arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SnmpMessage clone()
	{
		return (SnmpMessage)super.clone();
	}
}
