package com.nm.server.adapter.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nm.descriptor.NeDescriptor;
import com.nm.nmm.res.ManagedElement;

public class CliDataAccessPool {
	protected static Log log = LogFactory.getLog(CliDataAccessPool.class);
	
	private static CliDataAccessPool instance;
	
	private Map<String, Map<Integer, Object>> cdaCache = new ConcurrentHashMap<String, Map<Integer, Object>>();
	private ClassPathXmlApplicationContext appContext = null;
//	private ScheduledExecutorService schedule = Executors.newScheduledThreadPool(1);
	
	private CliDataAccessPool(){
		try{
//			//定时检查连接状态
//			Runnable r = new Runnable(){
//				public void run(){
//					if(!cdaCache.isEmpty()){
//						Set<Entry<String, CliDataAccess>> es = cdaCache.entrySet();
//						for(Entry<String, CliDataAccess> e : es){
//							try {
//								if(!e.getValue().isConnected()){
//									e.getValue().reconnect();
//								}
//							} catch (IOException e1) {
//								log.error(e1, e1);
//							}
//						}
//					}
//				}
//			};
//			schedule.scheduleAtFixedRate(r, 30, 20, TimeUnit.SECONDS);
		}catch(Exception ex){
			log.error(ex, ex);
		}
	};
	
	public static CliDataAccessPool getInstance(ClassPathXmlApplicationContext appContext){
		if(instance == null){
			instance = new CliDataAccessPool();
			instance.appContext = appContext;
		}
		if (instance.appContext == null) {
			instance.appContext = appContext;
		}
		return instance;
	}
	public static CliDataAccessPool getInstance(){
		if(instance == null){
			instance = new CliDataAccessPool();
		}
		return instance;
	}
	
	public CliDataAccess getCliDataAccess(String ip, int port, String userName, String password, NeDescriptor nedes){
		Map<Integer, Object> cdaMap = cdaCache.get(ip);
		if(cdaMap == null){
			cdaMap = new HashMap<Integer, Object>();
			cdaCache.put(ip, cdaMap);
		}
		CliDataAccess cda = (CliDataAccess)cdaMap.get(port);
		if(cda == null)
		{
			if (appContext != null && nedes != null) {
				CliDataAccess cdaBean = null;
				try {
					cdaBean = (CliDataAccess)appContext.getBean(nedes.getProductName() + "_CliDataAccessBean");
				} catch (Exception e) {}
				if (cdaBean == null && nedes.getNeSeries() != null && !nedes.getNeSeries().isEmpty()) {
					try {
						cdaBean = (CliDataAccess)appContext.getBean(nedes.getNeSeries() + "_CliDataAccessBean");
					} catch (Exception e) {}
				}
				if (cdaBean != null) {
					cda = cdaBean.getInstance(ip, port, userName, password, nedes);
				}
			}
			if (cda == null) {
				cda = new CliDataAccess(ip, port, userName, password, nedes);
			}
			try {
				if (cda.isConnected()) {
					cdaMap.put(port, cda);
					cda.reConnectCounter = 0;
				} else {
					cda = null;
				}
			} catch (IOException e) {
				cda = null;
			}
		} else {
			cda.reConnectCounter = 0;
		}
		return cda;
	}
	
	public CliDataAccess getCliDataAccess(String ip, ManagedElement me, int slot, NeDescriptor neDes){
		Integer port = neDes.getPort();
		if (me != null) {
			ip = me.getNatIpEx();
		} else if (ip == null) {
			return null;
		}
		if (me != null) {
			port = me.getNatPort(port);
		}
		if(neDes.getProductName().equals("NE51")){
			int actualPort = 23000 + slot;
			port = me.getNatPort(actualPort);
			
			if (slot == 0) {
				if (me.getEquipmentHolder(10) != null && me.getEquipmentHolder(10).getInstalledEquipment() != null) {
					slot = 10;
				} else {
					slot = 11;
				}
			}
   		}
		
		Map<Integer, Object> cdaMap = cdaCache.get(ip);
		if(cdaMap == null){
			cdaMap = new HashMap<Integer, Object>();
			cdaCache.put(ip, cdaMap);
		}
		CliDataAccess cda = (CliDataAccess)cdaMap.get(port);
		//依赖其他工程,暂时注释掉
//		String userName = me.getUserName();
//		String password = me.getPassword()
		String userName = null;
		String password = null;;
		if(cda == null)
		{
			log.info("初始化网元 CliDataAccess ： neType : " + neDes.getProductName() + " neIP : " + ip + " appContext : " + (appContext == null ? "null" : "not null"));
			if (appContext != null && neDes != null) {
				CliDataAccess cdaBean = null;
				try {
					cdaBean = (CliDataAccess)appContext.getBean(neDes.getProductName() + "_CliDataAccessBean");
					log.info("初始化网元 CliDataAccess OK ! ：" + neDes.getProductName() + "_CliDataAccessBean" + " neIP : " + ip);
				} catch (Exception e) {}
				if (cdaBean == null && neDes.getNeSeries() != null && !neDes.getNeSeries().isEmpty()) {
					try {
						cdaBean = (CliDataAccess)appContext.getBean(neDes.getNeSeries() + "_CliDataAccessBean");
						log.info("初始化网元 CliDataAccess OK ! ：" + neDes.getNeSeries() + "_CliDataAccessBean" + " neIP : " + ip);
					} catch (Exception e) {}
				}
				if (cdaBean != null) {
					cda = cdaBean.getInstance(ip, port, userName, password, neDes);
				}
			}
			if (cda == null) {
				cda = new CliDataAccess(ip, port, userName, password, neDes);
				log.info("初始化网元 CliDataAccess OK ! ：" + "CliDataAccess" + " neIP : " + ip);
			}
			try {
				if (cda.isConnected()) {
					cdaMap.put(port, cda);
					cda.reConnectCounter = 0;
				} else {
					cda = null;
				}
			} catch (IOException e) {
				cda = null;
			}
		} else {
			cda.reSetUserAndPwd(userName, password, neDes);
		}
		if(cda != null){
			cda.reConnectCounter = 0;
		}
		return cda;
	}
	
	public CliDataAccessOS getCliDataAccessOS(ManagedElement me, NeDescriptor neDes){
		CliDataAccessOS cda = null;
		Integer port = 2650;
		String ip;
		if (me != null) {
			ip = me.getNatIpEx();
			Map<Integer, Object> cdaMap = cdaCache.get(ip);
			if(cdaMap == null){
				cdaMap = new HashMap<Integer, Object>();
				cdaCache.put(ip, cdaMap);
			}
			port = me.getNatPort(port);
			cda = (CliDataAccessOS)cdaMap.get(port);
			if (cda == null) {
				cda = new CliDataAccessOS(ip, port, "root", "root", neDes);
				cdaMap.put(port, cda);
			}
			cda.reConnectCounter = 0;
		}
		return cda;
	}
	
	public void removeCliDataAccess(String ip){
		cdaCache.remove(ip);
	}
}
