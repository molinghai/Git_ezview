package com.nm.server.adapter.snmp.pool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.springframework.context.ApplicationContext;

import com.nm.base.util.MibTreeNode;
import com.nm.server.adapter.snmp.SnmpDataAccess;
import com.nm.server.adapter.snmp.mibload.ManFactoryMibLoader;

public class SnmpDataAccessPool {
   protected static Log log = LogFactory.getLog(SnmpDataAccessPool.class);
   private  ApplicationContext context;
   private static SnmpDataAccessPool instance;
   
   private BlockingQueue<SnmpDataAccess> snmpPool = new LinkedBlockingQueue<SnmpDataAccess>(5);
//   private LRUHashMap<String, SnmpDataAccess> pool = new LRUHashMap<String, SnmpDataAccess>(3);
   private static ManFactoryMibLoader mibLoader; 
   @SuppressWarnings("unused")
   private static AtomicInteger snmpPort = new AtomicInteger(165);
   
   private SnmpDataAccessPool(){
	   String svrHome = System.getProperty("server.home");
	   String comHome = svrHome + File.separator + "config" + File.separator + "common";
		
	   String filename = comHome + File.separator +"mibs" + File.separator + "mib.properties";
	   
	   Map<String, String> domainMap = new HashMap<String, String>();
	   List<String> list = readFileByLine(filename, domainMap);
       if(list != null && !list.isEmpty()){
		   mibLoader = ManFactoryMibLoader.getInstace();
		   mibLoader.addManFacMibMap("9966", list, domainMap);
       }
       
   }
   public static SnmpDataAccessPool getInstance(){
	   if(instance == null){
		   instance = new SnmpDataAccessPool();
	   }
	   return instance;
   }
   
   public MibTreeNode getMibTreeNode(){
	   return mibLoader.getMibTreeRoot("9966");
   }
   
   public  SnmpDataAccess getSnmpDataAccess(String ip_port, String readCommunity, String writeCommunity){
	   return getSnmpDataAccess(ip_port, readCommunity, writeCommunity, 10);
   }
   
   public  SnmpDataAccess getSnmpDataAccess(String ip_port, String readCommunity, String writeCommunity, int timeout){
	   return this.getSnmpDataAccess(ip_port, readCommunity, writeCommunity, SnmpConstants.version2c, timeout);
   }
   
   public  SnmpDataAccess getSnmpDataAccess(String ip_port, String readCommunity, String writeCommunity, int version, int timeout){
	   if(readCommunity == null){
		   readCommunity = "public";
	   }
	   if(writeCommunity == null){
		   writeCommunity = "private";
	   }
	   SnmpDataAccess sda = buildSnmpDataAccess(ip_port, readCommunity, writeCommunity, version, timeout);
		
	   if(sda == null){
		   try {
				sda = snmpPool.poll(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				log.error(e, e);
			}//pool.remove(ip_port);
		   if(sda != null){
			   sda.setIpAddress(ip_port);
			   sda.setReadCommunity(readCommunity);
			   sda.setWriteCommunity(writeCommunity);
			   sda.setTimeOut(timeout);
		   }
	   }

	   
//	   if(sda == null){
//		   sda = this.buildSnmpDataAccess(ip_port, readCommunity, writeCommunity, version, timeout);
//		   if(sda == null){
//			   Set<Entry<String, SnmpDataAccess>> es = pool.entrySet();
//			   Iterator<Entry<String, SnmpDataAccess>> it = es.iterator();
//			   sda = pool.remove(it.next().getKey());
//			   sda.setIpAddress(ip_port);
//			   sda.setReadCommunity(readCommunity);
//			   sda.setWriteCommunity(writeCommunity);
//			   sda.setTimeOut(timeout);
//		   }
//	   }else{
//		   sda.setIpAddress(ip_port);
//		   sda.setReadCommunity(readCommunity);
//		   sda.setWriteCommunity(writeCommunity);
//		   sda.setTimeOut(timeout);
//	   }
	   if(sda == null){
		   log.error("SnmpDataAccess is null, ip_port:" + ip_port );
		   sda = buildTempSnmpDataAccess(ip_port, readCommunity, writeCommunity, version, timeout);
	   }
	   return sda;
   }
   
   public SnmpDataAccess getSnmpv3DataAccess(String ip_port, String userName, int securityLevel, 
			OID authProtocol, String authPassword, OID privProtocol, String privPassword, int timeout){
	   
	   SnmpDataAccess sda = null;
		try {
			sda = snmpPool.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}//pool.remove(ip_port + userName + securityLevel + authProtocol + authPassword + privProtocol + privPassword);
	   if(sda == null){
		   sda = this.buildSnmpv3DataAccess(ip_port, userName, securityLevel, authProtocol, authPassword, privProtocol, privPassword, timeout);
	   }else{
		   sda.setTimeOut(timeout);
	   }
	   return sda;
   }
   
   protected synchronized SnmpDataAccess buildTempSnmpDataAccess(String ip_port, String readCommunity, String writeCommunity, int version, int timeout){
	   try{
		   SnmpDataAccess snmpDataAccess = new SnmpDataAccess(ip_port,readCommunity, writeCommunity, timeout, 1 , version, 0);
		   snmpDataAccess.setAppContext(this.context);
		   return snmpDataAccess;
	   }catch(Exception e){
		   log.error(e, e);
	   }
	   return null;
   }
   
   protected synchronized SnmpDataAccess buildSnmpDataAccess(String ip_port, String readCommunity, String writeCommunity, int version, int timeout){
//	   try{
//		   int port = snmpPort.incrementAndGet();
//		   if(port > 170){
//			   snmpPort.set(171);
//			   return null;
////			   snmpPort.set(166);
////			   port = 166;
//		   }
//		   SnmpDataAccess snmpDataAccess = new SnmpDataAccess(ip_port,readCommunity, writeCommunity, timeout, 1, null , version, port);
//		   snmpDataAccess.setAppContext(this.context);
//		   return snmpDataAccess;
//	   }catch(Exception e){
//		   log.error(e, e);
//	   }
	   
	   return buildTempSnmpDataAccess(ip_port,readCommunity, writeCommunity, version, timeout);
   }
   
   protected SnmpDataAccess buildSnmpv3DataAccess(String ip_port, String userName, int securityLevel, 
			OID authProtocol, String authPassword, OID privProtocol, String privPassword, int timeout){
	   try{
		   SnmpDataAccess snmpDataAccess = new SnmpDataAccess(ip_port, userName,securityLevel, authProtocol, authPassword, privProtocol, privPassword);
		   snmpDataAccess.setTimeOut(timeout);
		   return snmpDataAccess;
	   }catch(Exception e){
		   log.error(e, e);
	   }
	   return null;
   }
   
   public void releaseSnmpDataAccess(SnmpDataAccess sda){
	   if(sda != null){
		   sda.close();
	   }
//	   if(sda != null && sda.getPort() > 0){
//		   try {
//			   if(snmpPool.size() < 5){
//				   snmpPool.put(sda);
//			   }else{
//				   sda = null;
//			   }
//		   } catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				log.error(e, e);
//		   }
////		   if(pool.get(sda.getIpAddress()) == null){
////			   pool.put(sda.getIpAddress(), sda);
////		   }
//	   }
   }
   
   public static List<String> readFileByLine(String fileName, Map<String, String> domainMap){
       File file = new File(fileName);
       BufferedReader reader = null;
       List<String> files = new ArrayList<String>();
       String svrHome = System.getProperty("server.home");
       String comHome = svrHome + File.separator + "config" + File.separator + "common";
       try {
           reader = new BufferedReader(new FileReader(file));
           String tempString = null;
           String filePath = null;
           String domain = null;
           int line =0;
           while ((tempString = reader.readLine()) != null) {
           	if(!tempString.startsWith("--") && !tempString.isEmpty()){
           		int index = tempString.indexOf("=");
           		filePath = comHome+tempString.substring(0, index);
           		files.add(filePath);
           		domain = tempString.substring(index + 1, tempString.length());
           		domainMap.put(filePath, domain);
                System.out.println("line " + line + ": " + filePath);
                line++;
           	}
           }
           
           reader.close();
           
       } catch (IOException e) {
    	   log.error(e,e);
       } finally {
           if (reader != null) {
               try {
                   reader.close();
               } catch (IOException e1) {
               }
           }
       }
       return files;
   }
   public void intAppContext(ApplicationContext appContext){
	   this.context=appContext;
   }
}
