package com.nm.server.adapter.snmp;

public class SnmpEumDef {
	SnmpEumDef(){
		
	}
	public static final String snmpRead = "snmpRead";
    public static final String snmpReadFn = "BaseNE.snmpRead";
    public static final String snmpStatus = "snmpStatus";
    public static final String snmpStatusFn = "BaseNE.snmpStatus";
    public static final String snmpWrite = "snmpWrite";
    public static final String snmpWriteFn = "BaseNE.snmpWrite";
    public static final String snmpPort = "snmpPort";
    public static final String snmpPortFn = "BaseNE.snmpPort";
    public static final String snmpTimeout = "snmpTimeout";
    public static final String snmpTimeoutFn = "BaseNE.snmpTimeout";
    public static final String snmpRetry = "snmpRetry";
    public static final String snmpRetryFn = "BaseNE.snmpRetry";
    public static final String snmpCommunity = "snmpCommunity";
    public static final String snmpCommunityFn = "BaseNE.snmpCommunity";
    public static final String neId = "neId";
	public static final Object OID = "oid";
	
	//externalType
	public static final String INT32 = "INTEGER32";
    public static final String INTEGER = "INTEGER";
    public static final String UINT32 = "UINT32";
    public static final String OCTET = "OCTET";
    public static final String COUNTER32 = "COUNTER32";
    public static final String COUNTER64 = "COUNTER64";
    public static final String GAUGE = "GAUGE";
    public static final String TIMETICKS = "TIMETICKS";
    public static final String TIMESTAMP = "TIMESTAMP";
    public static final String OPAQUE = "OPAQUE";
    public static final String TRUTHVALUE = "TRUTHVALUE";
    public static final String TimeInterval = "TimeInterval";
    public static final String StorageType = "StorageType";
    public static final String BitString = "BitString";
    public static final String IpAddress = "IpAddress";
    public static final String tOID = "OID";
    public static final String PortList = "PortList";
    public static final String MACAddress = "MACAddress";
    public static final String MACString = "MACString";
	//externalPropMask
    public static final String Table = "1";
    public static final String Scalar = "0";
    public static final String Index = "3";
    public static final String RowStatus = "4";
    public static final String EntryStatus = "5";
    public static final String RowStatusForUpdateThenCreate = "6";
    public static final String RowStatusForAddAndUpdate = "7";
    //mibInstance
    public static final String mibInstance = "instanceid";
    //FactoryID
    public static final String factoryID = "9966";
    
}
