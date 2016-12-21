package com.nm.server.adapter.snmp.mibload;

import java.util.List;
import java.util.Map;

import com.nm.base.util.MibTreeNode;

@SuppressWarnings("static-access")
public class ManFactoryMibLoader implements IManFactoryMibLoader{
	private static final ManFactoryMibLoader facMibLoaderInstace = new ManFactoryMibLoader();
	private static String DEFUMANFAC = "9966";
	private ManFactoryMibLoader(){
		
	}
	public static ManFactoryMibLoader getInstace(){
		return facMibLoaderInstace;
	}
	
	public String getNamebyOID(Object manufcturer, String oid) {
		
		return this.manFacMibMap.get(manufcturer).getNamebyOID(oid);
	}
	public String getNamebyOID(String oid) {
		
		return this.manFacMibMap.get(DEFUMANFAC).getNamebyOID(oid);
	}
	
	public String getOIDbyName(Object manufcturer, String mibName) {
		
		return this.manFacMibMap.get(manufcturer).getOIDbyName(mibName);
	}
	public String getOIDbyName(String mibName) {
		
		return this.manFacMibMap.get(DEFUMANFAC).getOIDbyName(mibName);
	}
	
	public String getOIDbyName(Object manufcturer, String neType, String mibName) {
		
		return this.manFacMibMap.get(manufcturer).getOIDbyName(neType, mibName);
	}
	public String getOIDbyName(String neType, String mibName) {
		
		return this.manFacMibMap.get(DEFUMANFAC).getOIDbyName(neType, mibName);
	}
	
	public String[] getTrapBindOids(Object manufcturer, String trapName) {
		
		return this.manFacMibMap.get(manufcturer).getTrapBindOids(trapName);
	}
	public String[] getTrapBindOids( String trapName) {
		
		return this.manFacMibMap.get(DEFUMANFAC).getTrapBindOids(trapName);
	}
	
	public void addManFacMibMap(Object manufacturer, List<String> mibFiles, Map<String, String> domainMap) {
		
		this.manFacMibMap.put(manufacturer, new MibLoaderMap(mibFiles, domainMap));
	}

	
	public void delManFacMibMap(Object manufacturer) {
		
		this.manFacMibMap.remove(manufacturer);
	}
	
	public MibTreeNode getMibTreeRoot(Object manufacturer){
		if(manufacturer != null){
			return manFacMibMap.get(manufacturer).getMibTreeRoot();
		}else{
			return manFacMibMap.get(DEFUMANFAC).getMibTreeRoot();
		}
	}

}
