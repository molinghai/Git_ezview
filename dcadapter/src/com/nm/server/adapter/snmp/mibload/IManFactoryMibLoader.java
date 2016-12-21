package com.nm.server.adapter.snmp.mibload;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.nm.base.util.MibTreeNode;

public interface IManFactoryMibLoader {
	 Hashtable<Object,MibLoaderMap> manFacMibMap = new Hashtable<Object,MibLoaderMap>();
	 public String getNamebyOID(Object manufcturer,String oid);
	 public String getOIDbyName(Object manufcturer,String mibName);
	 public String[] getTrapBindOids(Object manufcturer,String trapName);
	 public String getNamebyOID(String oid);
	 public String getOIDbyName(String mibName);
	 public String getOIDbyName(String neType, String mibName);
	 public String[] getTrapBindOids(String trapName);
	 public void addManFacMibMap(Object manufacturer,List<String> mibFiles,Map<String, String> domainMap);
	 public void delManFacMibMap(Object manufacturer);
	 public MibTreeNode getMibTreeRoot(Object manufacturer);
}
