package com.nm.server.adapter.snmp.mibload;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.nm.base.util.MibTreeNode;
public interface IMibLoaderMap {
	 Hashtable<String,String> nameHashTable = new Hashtable<String, String>();
//	 Hashtable<String, Hashtable<String,String>> oidNeHashTable = new Hashtable<String, Hashtable<String,String>>();
	 Hashtable<String,String> oidHashTable = new Hashtable<String, String>();
	 Hashtable<String,String[]> trapBindHashTable = new Hashtable<String,String[]>();
	 public void initalMibMap(List<String> mibFiles, Map<String, String> domainMap);
	 public String getNamebyOID(String oid);
	 public String getOIDbyName(String neType, String mibName);
	 public String[] getTrapBindOids(String trapName);
	 public MibTreeNode getMibTreeRoot();
}
