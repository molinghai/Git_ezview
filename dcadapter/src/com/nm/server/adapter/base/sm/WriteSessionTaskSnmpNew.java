package com.nm.server.adapter.base.sm;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.smi.OID;
import org.springframework.context.ApplicationContext;

import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.EquipmentDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CommandDestination;
import com.nm.message.Response;
import com.nm.message.ResponseCollection;
import com.nm.nmm.mstp.config.Trap;
import com.nm.nmm.res.AuthProtocol;
import com.nm.nmm.res.Equipment;
import com.nm.nmm.res.EquipmentHolder;
import com.nm.nmm.res.EquipmentHolder.EquipmentState;
import com.nm.nmm.res.EquipmentHolder.HolderState;
import com.nm.nmm.res.EthGatewayInfo;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.SnmpVersion;
import com.nm.nmm.res.PrivProtocol;
import com.nm.nmm.res.SecurityInfo;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.AsynTaskList;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.cli.CliDataAccess;
import com.nm.server.adapter.cli.CliDataAccessPool;
import com.nm.server.adapter.snmp.SnmpDataAccess;
import com.nm.server.adapter.snmp.SnmpDataException;
import com.nm.server.adapter.snmp.SnmpEumDef;
import com.nm.server.adapter.snmp.pool.SnmpDataAccessPool;
import com.nm.server.adapter.snmp.trap.NeTrapHandler;
import com.nm.server.adapter.snmp.trap.SnmpDataTrap;
import com.nm.server.adapter.snmp.trap.TrapHandler;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.comm.messagesvr.MessageTask;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.util.ToolUtil;
import com.nm.server.config.trap.TrapManager;
import com.nm.server.config.util.TPUtil;
import com.nm.util.xml.XmlNode;
import com.nm.util.xml.XmlParser;

public class WriteSessionTaskSnmpNew extends MessageTask {

	private static AtomicLong uniqueNumber = new AtomicLong(0);

	private Integer sectionInterval = 0;
	
	private static int frameOn;	
	
	private static Lock syncLock = new ReentrantLock();
	
	private Map<Integer, ManagedElement> neMap;

	
	public WriteSessionTaskSnmpNew(MessagePool msgPool,Object o)
	{
		super(msgPool,o);
	}
	
    public void setNeMap(Map<Integer,ManagedElement> neMap) {
		this.neMap = neMap;
	}

	public Map<Integer,ManagedElement> getNeMap() {
		return neMap;
	}

	private static void loadTrapXmlFile(File dir, List<String> xmlList){
        File file[] = dir.listFiles();
        for (int i = 0; i < file.length; i++) {
            if(file[i].isDirectory()){
            	loadTrapXmlFile(file[i], xmlList);
            }else if (file[i].getAbsolutePath().contains("trap.xml")){
                xmlList.add(file[i].getAbsolutePath());
            }
        }
    }
    


	public static void initSnmpTrap(final TopoManager topoMng, final TrapManager trapMng, final MessagePool pool,final Map<Integer,ManagedElement> neMap)throws IOException{
		
		if(topoMng == null){
			return;
		}
		if(trapMng == null){
			return;
		}
        

		List<Trap> trapList = trapMng.getAllNeTrap();
	
		List<Integer> ports = new ArrayList<Integer>();
		ports.add(162);
		
		synchronized(neMap) {
			if(neMap != null) {
				Iterator<Integer> it = neMap.keySet().iterator();
				while(it.hasNext()) {
					ManagedElement me = neMap.get(it.next());
					if(me.getNatPort(162) != 162) {
						ports.add(me.getNatPort(162));
					}
					if(me.getNatPort(16210)!=16210){
						ports.add(me.getNatPort(16210));
					}
					if(me.getNatPort(16211)!=16211){
						ports.add(me.getNatPort(16211));
					}
				}
			}
		}
		
		if(trapList != null && !trapList.isEmpty()){
			for(Trap t : trapList){
				if(!ports.contains(t.getPortNum())){
					ports.add(t.getPortNum());
				}
			}
		}
		
		SnmpDataTrap snmpTrap = SnmpDataTrap.getInstance();
		
		snmpTrap.setTopoManager(topoMng);
		snmpTrap.setSnmpMsgPool(pool);
		snmpTrap.setNeMap(neMap);

		String svrHome = System.getProperty("server.home");
		String comHome = svrHome + File.separator + "config" + File.separator + "common" + File.separator;
		
		File dir = new File(comHome);
		List<String> xmlFiles = new ArrayList<String>();
		loadTrapXmlFile(dir, xmlFiles);
		
		for(String trapXml : xmlFiles){
			XmlNode root = XmlParser.parse(trapXml);
			if(root != null){
				List<XmlNode> children = root.getChildren();
				if(children != null && !children.isEmpty()){
					String oid = null;
					String oidName = null;
					String command = null;
					String handlerClass = null;
					TrapHandler handler = null;
					NeTrapHandler trapHandler = null;
					Class clazz = null;
					
					for(XmlNode node : children){
						oid = node.getAttribute("oid");
						oidName = node.getAttribute("name");
						command = node.getAttribute("command");
						handlerClass = node.getAttribute("handler");
						
						try {
							if(oidName != null){
								parseTrapHandler(snmpTrap,handlerClass,oid, oidName);
							}else if(command != null){
								parseNeTrapHandler(snmpTrap,handlerClass,oid, command);
							}else{
								log.error("load trap handler error:" + trapXml);
							}							
							
						} catch (Exception e) {
							// TODO Auto-generated catch block
							log.error(e, e);
						}
					}
				}
			}
		}

		snmpTrap.startListenTrap(ports);

	}
	

	public static void initSnmpTrap(final TopoManager topoMng, final TrapManager trapMng, final MessagePool pool,final Map<Integer,ManagedElement> neMap,final ApplicationContext appContexrt)throws IOException{
		
		if(topoMng == null){
			return;
		}
		if(trapMng == null){
			return;
		}
        

		List<Trap> trapList = trapMng.getAllNeTrap();
	
		List<Integer> ports = new ArrayList<Integer>();
		ports.add(162);
		
		synchronized(neMap) {
			if(neMap != null) {
				Iterator<Integer> it = neMap.keySet().iterator();
				while(it.hasNext()) {
					ManagedElement me = neMap.get(it.next());
					if(me.getNatPort(162) != 162) {
						ports.add(me.getNatPort(162));
					}
					if(me.getNatPort(16210)!=16210){
						ports.add(me.getNatPort(16210));
					}
					if(me.getNatPort(16211)!=16211){
						ports.add(me.getNatPort(16211));
					}
				}
			}
		}
		
		if(trapList != null && !trapList.isEmpty()){
			for(Trap t : trapList){
				if(!ports.contains(t.getPortNum())){
					ports.add(t.getPortNum());
				}
			}
		}
		
		SnmpDataTrap snmpTrap = SnmpDataTrap.getInstance();
		
		snmpTrap.setTopoManager(topoMng);
		snmpTrap.setSnmpMsgPool(pool);
		snmpTrap.setNeMap(neMap);
		snmpTrap.setContext(appContexrt);

		String svrHome = System.getProperty("server.home");
		String comHome = svrHome + File.separator + "config" + File.separator + "common" + File.separator;
		
		File dir = new File(comHome);
		List<String> xmlFiles = new ArrayList<String>();
		loadTrapXmlFile(dir, xmlFiles);
		
		for(String trapXml : xmlFiles){
			XmlNode root = XmlParser.parse(trapXml);
			if(root != null){
				List<XmlNode> children = root.getChildren();
				if(children != null && !children.isEmpty()){
					String oid = null;
					String oidName = null;
					String command = null;
					String handlerClass = null;
					TrapHandler handler = null;
					NeTrapHandler trapHandler = null;
					Class clazz = null;
					
					for(XmlNode node : children){
						oid = node.getAttribute("oid");
						oidName = node.getAttribute("name");
						command = node.getAttribute("command");
						handlerClass = node.getAttribute("handler");
						
						try {
							if(oidName != null){
								parseTrapHandler(snmpTrap,handlerClass,oid, oidName);
							}else if(command != null){
								parseNeTrapHandler(snmpTrap,handlerClass,oid, command);
							}else{
								log.error("load trap handler error:" + trapXml);
							}							
							
						} catch (Exception e) {
							// TODO Auto-generated catch block
							log.error(e, e);
						}
					}
				}
			}
		}
		snmpTrap.startListenTrap(ports);
	}
	
	
	

	
	private static void parseTrapHandler(SnmpDataTrap snmpTrap, String handlerClass, String oid, String oidName){
		try {
			Class clazz = Class.forName(handlerClass);
			TrapHandler handler = (TrapHandler)clazz.newInstance();
			handler.setOid(oid);
			handler.setOidName(oidName);

			
			snmpTrap.setTrapHandler(oid, handler);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.info("WriteSessionTaskSnmpNew , parseTrapHandler", e);
//			e.printStackTrace();
		}
	}
	
	private static void parseNeTrapHandler(SnmpDataTrap snmpTrap, String handlerClass, String oid, String oidName){
		try {
			Class clazz = Class.forName(handlerClass);
			NeTrapHandler handler = (NeTrapHandler)clazz.newInstance();
			handler.setCommand(oidName);
			snmpTrap.setNeTrapHandler(oid, handler);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//	public static void restartSnmpTrap(){
//		try {
//			initSnmpTrap(topoManager, trapManager, snmpMsgPool);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

	public void run()
	{
		try
		{
			AdapterMessagePair mp = (AdapterMessagePair)o;
			if(mp.getScheduleType() == ScheduleType.ST_NEPOLL){
				mp.setBeginTime();
			}
			ManagedElement ne = mp.getManagedElement();
			ManagedElement me = ne;
			
			
			NeDescriptor neDes = me == null ? null : NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			// must put into pool firstly
			synchronized(mp){
				if(!mp.isSendedMsgPair()){
					msgPool.addSendedMsg(mp);
					mp.setSendedMsgPair(true);
				}
			}
	
			
			if(mp.getScheduleType() == ScheduleType.ST_NA)
			{
				frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
			}
			else
			{
				frameOn = ToolUtil.getEntryConfig().getPollFrameRecordOnOff();
			}
			
			AsynTaskList asynTaskList = mp.getAsynTaskList();
			
			synchronized(asynTaskList){
				if(asynTaskList == null || asynTaskList.getTaskList() == null){
					return;
				}
				List<AsynTask> asynTcList =  asynTaskList.getTaskList();
				for(int i = 0; i < asynTcList.size(); i++) {
					AsynTask asynTask = asynTcList.get(i);
					List<TaskCell> tcs = asynTask.getTaskCellList();
					synchronized(tcs){
						for(int ii = 0; ii < tcs.size(); ii++) {
							TaskCell taskCell = tcs.get(ii);
							//比对协议类型，如果不通则跳过
							if(taskCell.getProtocolType() != null
									&& !CommProtocol.CP_SNMP_NEW.equals(taskCell.getProtocolType())
									&& !CommProtocol.CP_SNMP_II.equals(taskCell.getProtocolType())
									&& !CommProtocol.CP_CLI.equals(taskCell.getProtocolType())){
								log.info("TaskCell StiID="+taskCell.getStrID()
										+", protocolType=" + taskCell.getProtocolType());
								continue;
							}
							List<HiSysMessage> msgList = taskCell.getMsgList();	
							if(msgList == null || msgList.isEmpty()){
								continue;
							}
							synchronized(taskCell.getLockRawMsg()){//pjd,20140613,同步锁
								
								Iterator<HiSysMessage> msgIt = msgList.iterator();
								
								while(msgIt.hasNext())    				
			    				{						
									try{
										HiSysMessage tmp = msgIt.next();
										SnmpNewMessage msg = (SnmpNewMessage)tmp;
				    					
				    					if(uniqueNumber.addAndGet(1) >= Long.MAX_VALUE){
				    						uniqueNumber.set(0);
				    					}
				    					FunctionDescriptor fd = msg.getFunctionDescriptor();
				    					DeviceDest dest = msg.getDeviceDest();
				    					int slot = dest.getSlot();
				    					
	                                    List<Integer> backUpSlots = msg.getBackupSlots();
	                                    boolean hasBackUpSlot = false;
	                                    if(backUpSlots != null && !backUpSlots.isEmpty()){
	                                    	hasBackUpSlot = true;
	                                    	if(backUpSlots.contains(-1)){
	                                    		msgPool.removeSendedMsg(mp);
	                                			mp.setErrCode(ErrorMessageTable.CHECK_MASTER_CARD);
	                                			this.addMsgToProcessedPoolAndCancelProgress(mp);
	                                			
	                                    	}else if(backUpSlots.contains(-2)){
	                                    		msgPool.removeSendedMsg(mp);
	                                			mp.setErrCode(ErrorMessageTable.CHECK_P1_OR_P2_STATUS);
	                                			this.addMsgToProcessedPoolAndCancelProgress(mp);
	                                    	}else{
	                                    		boolean isChanged = false;
	                                    		int lastChangeSlot = 0;
	                                    		TPUtil tpUtil = (TPUtil)this.appContext.getBean("localTPUtil");
	                                    		for(int s : backUpSlots){
	                                    			if (slot != s) {
	                                    				tpUtil.convertPortIndex(me, slot, s, fd, msg.getConditions(), msg.getValues());
	                                    				isChanged = true;
	                                    				lastChangeSlot = s;
	                                    			}else if(isChanged){
	                                    				tpUtil.convertPortIndex(me, lastChangeSlot, s, fd, msg.getConditions(), msg.getValues());
	                                    				isChanged = false;
	                                    			}
		                                    		boolean suc = sendMsg(mp, me, neDes, fd, msg, taskCell, asynTask, s, hasBackUpSlot);
		                                    		if(!suc){
		                                    			break;
		                                    		}else if(isChanged){
	                                    				tpUtil.convertPortIndex(me, lastChangeSlot, slot, fd, msg.getConditions(), msg.getValues());
	                                    				isChanged = false;
	                                    			}
		                                    	}
	                                    	}
	                                    }else{
	                                    	sendMsg(mp, me, neDes, fd, msg, taskCell, asynTask, slot, hasBackUpSlot);
	                                    }
	                                    
									}catch(Exception e){
										log.error(e, e);
									}
									
			    				}
								if(taskCell.getMsgList() != null){
									taskCell.getMsgList().clear();
								}
							}
						}
					}
				}
			}
			
			mp.setIsSend(true);
		}
		catch (Exception e)
		{
			log.error("send snmp exception:", e);
		}
	}
	
	private boolean sendMsg(AdapterMessagePair mp, ManagedElement me, NeDescriptor neDes, 
			FunctionDescriptor fd, SnmpNewMessage msg, TaskCell taskCell, AsynTask asynTask, 
			int slot, boolean hasBackUpSlot) throws InterruptedException {
		String msgId = String.valueOf(uniqueNumber.get());
		String funcName = msg.getFuncName();
		List<String> subFuncNames = msg.getSubFuncNames();
		Integer opType = msg.getOpType();
		if(opType == null){
			opType = -1;
		}
		List<Map<String, Object>> conditions = msg.getConditions();
		List<Map<String, Object>> values = msg.getValues();
		
		List<String> fields = msg.getFields();
		DeviceDest dest = msg.getDeviceDest();
		NeDescriptor des = NeDescriptionLoader.getHighestVersionNedes(dest.getNeType());
		if (fd == null) {
			if(fd == null && me != null && funcName != null){
				fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
			}
			if (dest.isNeAutoDiscovery() && neDes != null) {
				fd = des.getFuncDesByName(funcName);
				if (fd == null) {
					EquipmentDescriptor ed = des.getEquipmentDescriptor(dest.getEquipName());
					if (ed != null) {
						fd = ed.getFuncDesByName(funcName);
					}
				}
			}
		}
		String protocol = "snmp";
		int timeout = 10;
        if(fd != null){
        	protocol = fd.getProtocol();
        	if(fd.getTimeout() > 0){
        		timeout = fd.getTimeout();
        	}
        }else{
        	if(msg.getClis() != null && !msg.getClis().isEmpty()){
        		protocol = "cli";
        	}
        }
        
		if(protocol != null && protocol.equals("cli")){//telnet方式通信
			SnmpNewMessage msgCli = msg.clone();
			boolean suuccess;
			if (dest.isNeAutoDiscovery() && neDes == null) {
				suuccess = sendCli(mp, me, des, msgCli, taskCell, slot, msgId);
			} else {
				suuccess = sendCli(mp, me, neDes, msgCli, taskCell, slot, msgId);
			}
        	List<Integer> backUpSlots = msg.getBackupSlots();
        	if(backUpSlots != null && !backUpSlots.isEmpty()){
	        	for(int s : backUpSlots){
	    			if (slot != s) {
	    				TPUtil tpUtil = (TPUtil)this.appContext.getBean("localTPUtil");
	    				tpUtil.convertPortIndex(me, slot, s, fd, msgCli.getConditions(), msgCli.getValues());
	    			}
	        	}
        	}
			return suuccess;
        }
        boolean success = true;
        //snmp通信
		boolean isDiscovery = false;
		SnmpDataAccess snmpAccess = null;
		try {
			if (!dest.isNeAutoDiscovery()) {
				if (me != null) {
					String ip_port = me.getNatIpEx()+"/"+me.getNatPort(161);
			EthGatewayInfo gwInfo = me.getEthGatewayInfo();
			if(me.getShelfNum() != -1
					&& !me.isServeAsEthGateway() && gwInfo != null){
				ManagedElement gwMe = neMap.get(me.getEthGatewayInfo().getWorkGatewayNeId());
				if(gwMe != null){
					ip_port = gwMe.getNatIpEx()+"/"+me.getNatPort(161);
				}
			}
					if (neDes.getProductName().equals("NE51")) {
						String slotNo = String.valueOf(slot);
						EquipmentHolder eh = me.getEquipmentHolder(slot);
						if (eh != null) {
							if ("NM".equals(eh.getName())) {
								slotNo = "";
							}
						}
						int actualPort = Integer.valueOf(161+slotNo); 
						ip_port = me.getNatIpEx()+"/"+me.getNatPort(actualPort);
					}
					if (me.getSnmpVersion() == SnmpVersion.SV_V3) {
						SecurityInfo si = me.getSecurityInfo();
						if (si != null) {
							OID authProto = null;
							if (si.getAuthProtocol() == AuthProtocol.MD5) {
								authProto = AuthMD5.ID;
							} else if (si.getAuthProtocol() == AuthProtocol.MD5) {
								authProto = AuthSHA.ID;
							}
							OID privProto = null;
							if (si.getPrivProtocol() == PrivProtocol.DES) {
								privProto = PrivDES.ID;
							} else if (si.getPrivProtocol() == PrivProtocol.AES128) {
								privProto = PrivAES128.ID;
							}
							int securityLevel = SecurityLevel.NOAUTH_NOPRIV;
							if (authProto != null) {
								securityLevel = SecurityLevel.AUTH_NOPRIV;
							}
							if (privProto != null) {
								securityLevel = SecurityLevel.AUTH_PRIV;
							}
							snmpAccess = SnmpDataAccessPool.getInstance().getSnmpv3DataAccess(ip_port,si.getUserName(), securityLevel,authProto, si.getAuthPasswd(),privProto, si.getPrivPasswd(), timeout);
						}
					} else {
						snmpAccess = SnmpDataAccessPool.getInstance().getSnmpDataAccess(ip_port,me.getReadCommunity(),me.getWriteCommunity(), timeout);
					}
					snmpAccess.setNeType(me.getProductName());
				} else {
					String ip_port = asynTask.getCommand().getDest().getNeIp()+ "/161";
					snmpAccess = SnmpDataAccessPool.getInstance().getSnmpDataAccess(ip_port, "public", "private", timeout);
				}
			} else {
				String ip_port = dest.getNeIp() + "/161";

				if (des != null && des.getFuncDesByName("LldpOnCard") != null) {
					String port = String.valueOf(slot);
					ip_port += port;
				}

				snmpAccess = SnmpDataAccessPool.getInstance()
						.getSnmpDataAccess(ip_port, "public", "private", timeout);
				snmpAccess.setNeType(dest.getNeType());
			}

			taskCell.addSendMsg(msgId, msg);
			if(snmpAccess != null){
				snmpAccess.setScType(mp.getScheduleType());
				snmpAccess.setSync(mp.isSync());
				if(taskCell.getStrID().equalsIgnoreCase("getCardConfig")
						&& me.getEquipmentHolders().size() <= 1){  
					msg.setValues(values);
				}else if((taskCell.getStrID().equalsIgnoreCase("neConnStateFunc") && !"CardConfig".equals(funcName))
						|| taskCell.getStrID().equalsIgnoreCase("getCpuUtilization")){                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   
					try{
						if(mp.getScheduleType() != ScheduleType.ST_NEPOLL){
							snmpAccess.getFactoryId();
							log.info(snmpAccess.getIpAddress() + " can be reached---");
							reportNoResAlarm(me, ErrorMessageTable.SUCCESS);
						}else{
							//轮询优化
							snmpAccess.getPollFactoryId();
						}
					}catch(Exception e){
						snmpAccess.close();
						mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
						log.info(snmpAccess.getIpAddress() + " can't be reached!!!");
						if (mp.getScheduleType() != ScheduleType.ST_NEPOLL) {
							reportNoResAlarm(me, ErrorMessageTable.NE_RESPONSE_TIME_OUT);
						}
					}
				}else if(opType == 10){//discovery
					dicovery(isDiscovery, values, snmpAccess, msg);
					
				}else if(opType == 3){//query
					qurey(dest, me, slot, neDes, fd, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames);
					
				}else if(opType == 2){//update
//					values = handleValues(values);
//					if(slot == 11){
//						snmpAccess = null;
//					}
					update(me, slot, neDes, fd, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames);

				}else if(opType == 0){//add
					add(me, slot, neDes, fd, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames);
					
				}else if(opType == 1){//delete
					delete(fd, snmpAccess, conditions);

				}else if(opType == 4) { //根据rowStatus添加数据
					deleteAndCreate(me, slot, neDes, fd, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames);
					
				}else if(opType == 5) { //createAndWaitAndActive
					success = addByRowStatus(me, slot, neDes, fd, mp, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames, hasBackUpSlot);
					
					
				}else if(opType == 110){//下载,先查询，再删除，再添加
					downLoad(me, slot, neDes, fd, mp, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames, hasBackUpSlot);
					
				}else if(opType == 111){//上载
					upLoad(me, slot, neDes, fd, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames);
					
				}else if(opType == 6){ //getBulk
					
					getBulk(me, slot, neDes, fd, msg, snmpAccess,
							fields, funcName, values, conditions, subFuncNames);
				}
			}else{
				mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
			}
			taskCell.addResMsg(msgId, msg);
		}catch(SnmpDataException e){
			if(e.getAgentErrCode() == SnmpDataException.AGT_ERROR){
				if(e.getProtocolErrCode() > 100){
					log.error("ErrorCode out of range：" + e.getProtocolErrCode() + fd.getName() );
				}
				mp.setErrCode(e.getProtocolErrCode() + 50000);
			}else{
				if(snmpAccess != null){
					snmpAccess.close();
				}
				if(fd!=null&&"SubE1BaseConfigEntry".equals(fd.getName())){
					mp.setErrCode(ErrorMessageTable.SUCCESS);
				}else{
					mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
				}
			}
			
			if(3 != opType && !isMasterCard(me, slot) && hasBackUpSlot){
				mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				taskCell.addResMsg(msgId, msg);
			}else{
				msgPool.removeSendedMsg(mp);
				if( 5 != opType){
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
			}
			
			
			//log.error("SNMP error, or response time out: " + snmpAccess.getIpAddress(), e);
			log.error("------- SNMP error or response time out !!: " + snmpAccess.getIpAddress());
            return false;
		}catch(Exception e){
			log.error(e, e);
			mp.setErrCode(ErrorMessageTable.UNKNOW);
			
			if(3 != opType && !isMasterCard(me, slot) && hasBackUpSlot){
				mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				taskCell.addResMsg(msgId, msg);
			}else{
				msgPool.addCancelledMsg(mp);
				this.addMsgToProcessedPoolAndCancelProgress(mp);
			}
			
			return false; 
		}finally{
			if(!isDiscovery && snmpAccess != null){
				SnmpDataAccessPool.getInstance().releaseSnmpDataAccess(snmpAccess);
			}
		}
		
		if(taskCell.getObj() != null &&((ResFunc)taskCell.getObj()).getFrameInterval() > 0){
			Thread.sleep(((ResFunc)taskCell.getObj()).getFrameInterval());
			log.debug("Frame Interval of TaskCell is:"+((ResFunc)taskCell.getObj()).getFrameInterval());
		}else if(msg.getFrameInterval()>0){
			Thread.sleep(msg.getFrameInterval());
			log.debug("Frame Interval of Msg is:"+msg.getFrameInterval());
		}else{
			Thread.sleep(this.getWriteFrameInterval() * 30);
			log.debug("Frame Interval of Default is:"+this.getWriteFrameInterval() * 30);
		}
		return success;
	}

	private boolean isMasterCard(ManagedElement me, int slot) {
		EquipmentHolder eh = me.getEquipmentHolder(slot);
        if (eh != null && eh.getHolderState() == HolderState.INSTALLED_AND_EXPECTED) {
       	 Equipment e = eh.getInstalledEquipmentEx();
            if (e != null) {
                if (eh.getEquipmentState() == EquipmentState.MASTER) {
                	return true;
                }
            }
        }
		return false;
	}

	private boolean sendCli(AdapterMessagePair mp, ManagedElement me, 
			NeDescriptor neDes, SnmpNewMessage msg,
			TaskCell taskCell, int slot, String msgId) {
		// TODO Auto-generated method stub
		String userName = null;
        String password = null;
        
        if(neDes.getUserName() != null){
        	userName = neDes.getUserName();
        } 
        if(neDes.getPassword() != null){
        	password = neDes.getPassword();
        } else {
        	password = null;
        }
		taskCell.addSendMsg(msgId, msg);
    	List<String> clis = msg.getClis();
    	List<String> res = new ArrayList<String>();
    	if(clis != null && !clis.isEmpty()){
    		Integer port = null;
    		String ip;
			if (me != null) {
				ip = me.getNatIpEx();
		
			} else {
				ip = msg.getDeviceDest().getNeIp();
			}
			if (port == null) {
				if (me != null) {
					port = me.getNatPort(23);
				} else {
					port = 23;
				}
				if(neDes.getProductName().equals("NE51")){
	    			int actualPort = 23000 + slot;
	    			port = me.getNatPort(actualPort);
	       		}
			}
    		try {
    			 if (this.msgPool instanceof AdapterMessagePool && this.appContext == null) {
    				 this.appContext = ((AdapterMessagePool)this.msgPool).getAppContext();
				}
    			CliDataAccess cda = CliDataAccessPool.getInstance(this.appContext).getCliDataAccess(ip, me, slot, neDes);
    			if (cda == null) {
    				mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
    			} else {
    				if (cda.getTelnetConnectErrorCode() != ErrorMessageTable.SUCCESS) {
    					mp.setErrCode(cda.getTelnetConnectErrorCode());
    				} else {
    					synchronized (cda) 
                		{
    						log.info("******************&&&&&&&&&&&&&&&&&&&&&&& telnet start , thread :" + Thread.currentThread().getId());
                			// taskCell.getStrID()
    						log.info("******************&&&&&&&&&&&&&&&&&&&&&&& telnet restart , commandId :" + taskCell.getStrID());
    						String userNameTest = null;
    						String passwordTest = null;
    						for(String cli : clis){
    							if(cli.contains("switch-user ")){
    								userNameTest = cli.split(" ")[1];
                				}
                				if(cli.contains("password ")){
                					passwordTest = cli.split(" ")[1];
                				}
                			}
    						if(userNameTest != null && passwordTest != null){
    							boolean isConnect = cda.reconnect(userNameTest, passwordTest);
    							if(isConnect){
                					res.add("Connect Success");
                				}else{
                					res.add("Connect Failed");
                				}
                			}else if (cda.reconnect()) {
                				for(String cli : clis){
                        			Object result = cda.sendCommand(cli,msg);
                        			res.add(result != null ? result.toString() : "");
                        			if (result == null) {
                        				mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
                        				log.info("******************&&&&&&&&&&&&&&&&&&&&&&& telnet ip :"+ ip + ", port : " + port + ", NE_RESPONSE_TIME_OUT");
                        				break;
                        			} else if (result.toString().contains("Protocol daemon is not running")) {
                        				mp.setErrCode(ErrorMessageTable.MPLS_ERROR_1625);
                        				log.info("******************&&&&&&&&&&&&&&&&&&&&&&& telnet ip :"+ ip + ", port : " + port + ", Protocol daemon is not running");
                        				break;
                        			} else {
                        				boolean isError = false;
                        				if (result.toString().contains("%Error ")) {
            								break;
            							}
                        				if (isError) {
                        					break;
                        				} else if (result.toString().contains("Error 140357")
                        						|| result.toString().contains("Simultaneous configs not allowed")) {
                        					mp.setErrCode(7013);
            								break;
            							}
                        			}
                        		}
                				sendCloseCommandByCli(ip, port,
                    					userName, password, neDes,msg);
                			} else {
                				mp.setErrCode(cda.getTelnetConnectErrorCode());
                			}
    						log.info("******************&&&&&&&&&&&&&&&&&&&&&&& telnet end , thread :" + Thread.currentThread().getId());
                		}
    				}
    			}
    		} catch (Exception e) {
    			mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
    		}
    		if (mp.getErrCode() != ErrorMessageTable.SUCCESS) {
    			log.info("#####cli == null && clis.isEmpty() : " + msg.getFuncName());
    			msgPool.removeSendedMsg(mp);
				this.addMsgToProcessedPoolAndCancelProgress(mp);
                return false;
    		}
    	} 
    	
    	msg.setClis(res);
    	taskCell.addResMsg(msgId, msg);
    	return true;

	}

	private void getBulk(ManagedElement me, int slot, NeDescriptor neDes,
		FunctionDescriptor fd, SnmpNewMessage msg, SnmpDataAccess snmpAccess,
		List<String> fields, String funcName, List<Map<String, Object>> values,
		List<Map<String, Object>> conditions, List<String> subFuncNames) {
		log.info("begin getBulk。。。。。" + funcName);
		values = snmpAccess.getDatasByBulk(fd, fields, conditions);
		log.info("getBulk end。。。。。" + funcName + values.size());
		Map<String, List<Map<String, Object>>> subValueMap = new HashMap<String, List<Map<String, Object>>>();
		
		if(subFuncNames != null){
			List<Map<String, Object>> vs = null;
			for(String subFunc : subFuncNames){
				fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, subFunc);
//				fd = neDes.getFuncDesByName(subFunc);
				if(fd == null){
					EquipmentHolder eh = me.getEquipmentHolder(slot);
					if(eh != null){
						Equipment eqp = eh.getInstalledEquipment();
						EquipmentDescriptor ed = neDes.getEquipmentDescriptor(eqp.getName(), eqp.getSoftwareVersion());
						fd = ed.getFuncDesByName(subFunc);
					}
				}
				 List<Map<String, Object>> subConditionsList =msg.getSubConditions(subFunc);
					vs = snmpAccess.getDatasByBulk(fd, fields, subConditionsList);
					if(vs != null){
						subValueMap.put(subFunc, vs);
				 }
			}
		}
		
//		//调用特殊逻辑处理器
//    	values = handleValues(values);
		msg.setValues(values);
		if(!subValueMap.isEmpty()){
			msg.setSubValues(subValueMap);
		}
		
	
}

	private void upLoad(ManagedElement me, int slot, NeDescriptor neDes,
		FunctionDescriptor fd, SnmpNewMessage msg, SnmpDataAccess snmpAccess,
		List<String> fields, String funcName, List<Map<String, Object>> values,
		List<Map<String, Object>> conditions, List<String> subFuncNames) throws Exception{
		log.info("begin upload ......" + funcName);
		for(int i = 0; i < 3; i++){
			try{
				if(fields == null && conditions == null){
			    	values = snmpAccess.getDatas(fd);
				}else if(fields != null){
					values = snmpAccess.getDatas(fd, fields);
				}else{
					values = snmpAccess.getDatas(fd, fields, conditions);
				}
				break;
			}catch(Exception ex){
				log.error(funcName +" upload error");
				Thread.sleep(1000);
			}
		}
		int count = values == null ? 0 : values.size();
		
		Thread.sleep(this.getWriteFrameInterval() * 20);
		
		if(subFuncNames != null){
			for(int i = 0; i < 3; i++){
				try{
					List<Map<String, Object>> vs = null;
					for(String subFunc : subFuncNames){
						fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, subFunc);
						if(fd == null){
							continue;
						}
						vs = snmpAccess.getDatas(fd);
						if(vs != null){
							values.addAll(vs);
						}
						Thread.sleep(this.getWriteFrameInterval() * 20);
					}
					break;
				}catch(Exception ex){
					log.error(funcName +" upload error");
					Thread.sleep(1000);
				}
			}

		}
		log.info("upload end ...... " + funcName + count);	
		msg.setValues(values);
	
}

	private void downLoad(ManagedElement me, int slot, NeDescriptor neDes,
		FunctionDescriptor fd, AdapterMessagePair mp, SnmpNewMessage msg, SnmpDataAccess snmpAccess,
		List<String> fields, String funcName, List<Map<String, Object>> values,
		List<Map<String, Object>> conditions, List<String> subFuncNames, boolean hasBackUpSlot) throws Exception{
		List<AttributeDescriptor> attrList = fd.getAttributeList();
		String  rowstatus = "";
		List<String>  indexNames = new ArrayList<String>();
		long rowIndex = 0;
		int addOperaType = 0;
		for(AttributeDescriptor attr : attrList){
			if(attr.getExternalName()!=null&&attr.getExternalPropMask()!=null){
					if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatus)){
						rowstatus = attr.getName();
						addOperaType = 4;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.EntryStatus)){
						rowstatus = attr.getName();
						addOperaType = 2;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForUpdateThenCreate)){
						rowstatus = attr.getName();
						addOperaType = 1;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForAddAndUpdate)){
						rowstatus = attr.getName();
						addOperaType = 3;
					}
					
					if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)){
						indexNames.add(attr.getName());
						
					}	
				}
		}
		if(addOperaType == 0){//不可创建和删除，只能修改或查询
//			for(Map<String, Object> m : values){
//				for(String index : indexNames){
//					if(m.containsKey(index)){
//						m.remove(index);
//					}
//				}
//			}
		}else if(addOperaType == 1 || addOperaType == 0){//rowStatus.externalPropMask==4
			for(Map<String, Object> m : values){
					if(m.containsKey(rowstatus)){
						m.remove(rowstatus);
					}
			}
		}else{
			addByRowStatus(me, slot, neDes, fd, mp, msg, snmpAccess, fields, funcName, values, conditions, subFuncNames, hasBackUpSlot);
			return;
		}
		if(funcName.toLowerCase().contains("vlanconf")){
			try{
				for(Map<String, Object> m : values){
					if(m.containsKey("vlanRowStatus")){
						m.remove("vlanRowStatus");
					}
				}
				List<Map<String, Object>> vs = snmpAccess.getDatas(fd);
				AttributeDescriptor ad = null;
				if(vs != null && !vs.isEmpty()){
					for(Map<String, Object> v : vs){
						Set<Entry<String, Object>> es = v.entrySet();
						for(Entry<String, Object> e : es){
							ad = fd.getAttribute(e.getKey());
							if(ad != null && ad.getExternalPropMask() != null && ad.getExternalPropMask().equals(SnmpEumDef.Index)){
								Map<String, Object> ic = new HashMap<String, Object>();
								ic.put(e.getKey(), e.getValue());
								conditions.add(ic);
								snmpAccess.deleteDatas(fd, conditions);
								conditions.clear();
								
								Thread.sleep(this.getWriteFrameInterval() * 10);
							}
						}

					}
				}
			}catch(Exception ex){
				log.error("download delete error " + funcName, ex);
			}
		}

		
		int count=0;
		List<Map<String, Object>> vs = null;
		for(Map<String, Object> m : values){
			if(vs == null){
				vs = new ArrayList<Map<String, Object>>();
			}else{
				vs.clear();
			}

			vs.add(m);
			for(int i = 0; i < 3; i++){
				try{
					snmpAccess.addDatas(fd, vs);
					break;
				}catch(Exception ex){
//					log.error(funcName +" download error",ex);
					if(i == 2){
						throw ex;
					}
//					log.error(ex,ex);
					Thread.sleep(100);
				}
			}

			Thread.sleep(this.getWriteFrameInterval() * 20);
		}
    	msg.setValues(values);
    	Thread.sleep(this.getWriteFrameInterval() * 20);
	
}

	private boolean addByRowStatus(ManagedElement me, int slot, NeDescriptor neDes,
		FunctionDescriptor fd, AdapterMessagePair mp, SnmpNewMessage msg, SnmpDataAccess snmpAccess,
		List<String> fields, String funcName, List<Map<String, Object>> values,
		List<Map<String, Object>> conditions, List<String> subFuncNames, boolean hasBackUpSlot) {
		int count=0;
		List<AttributeDescriptor> attrList = fd.getAttributeList();
		String  rowstatus = "";
		List<String>  indexNames = new ArrayList<String>();
		long rowIndex = 0;
		int addOperaType = 0;
		for(AttributeDescriptor attr : attrList){
			if(attr.getExternalName()!=null&&attr.getExternalPropMask()!=null){
					if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatus)){
						rowstatus = attr.getName();
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.EntryStatus)){
						rowstatus = attr.getName();
						addOperaType = 2;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForUpdateThenCreate)){
						rowstatus = attr.getName();
						addOperaType = 1;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForAddAndUpdate)){
						rowstatus = attr.getName();
						addOperaType = 3;
					}
					
					if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)){
						indexNames.add(attr.getName());
						
					}	
				}
		}
		List<Map<String, Object>> vs = new ArrayList<Map<String, Object>>();
		
		boolean ok = false;//激活不成功要删除
		if(addOperaType == 3) { 
			try{
				for(int i = 0; i < conditions.size(); i++){
					Map<String, Object> c = conditions.get(i);
					vs.clear();
					c.put(rowstatus, 4);
					if(indexNames != null && !indexNames.isEmpty()){
						for(String name : indexNames){
							if(values.get(i).get(name) instanceof Long){
								rowIndex = (Long)values.get(i).get(name);
								c.put(name, rowIndex);
							}else if(values.get(i).get(name) instanceof Integer){
								rowIndex = (Integer)values.get(i).get(name);
								c.put(name, rowIndex);
							}else{
								c.put(name, values.get(i).get(name));
							}
						}
					}
						vs.add(c);
						snmpAccess.addDatas(fd, vs);
						if(count++ > 128){
							count = 0;
							Thread.sleep(getSectionInterval());
						}else{
							Thread.sleep(this.getWriteFrameInterval());
						}
				}
	        	msg.setValues(values);
	        	
	        	if(values != null && !values.isEmpty()){
					int index = 0;
					Map<String, Object> v = null;
					if(conditions != null && !conditions.isEmpty()){
						for(Map<String, Object> c : conditions){
							List<Map<String, Object>> cs = null;
							if(!c.isEmpty()){
								cs = new ArrayList<Map<String, Object>>();
								cs.add(c);
							}
//							v = values.get(0);
							if(values.size() > index){
								v = values.get(index++);
							}
							if(!v.isEmpty()){
								ok = snmpAccess.updateDatas(fd, v, cs);
                                if(count++ > 128){
    								count = 0;
    								Thread.sleep(getSectionInterval());
    							}else{
    								Thread.sleep(this.getWriteFrameInterval());
    							}
								if(!ok){
									snmpAccess.deleteDatas(fd, cs);
									return false;
								}
							}
							
							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					}else{
						snmpAccess.updateDatas(fd, values.get(0), null);
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}
				}
				msg.setValues(values);
			}catch(SnmpDataException e){
				if(e.getAgentErrCode() == SnmpDataException.AGT_ERROR){
					if(e.getProtocolErrCode() > 100){
						log.error("ErrorCode out of range：" + e.getProtocolErrCode() + fd.getName() );
					}
					mp.setErrCode(e.getProtocolErrCode() + 50000);
				}else{
					snmpAccess.close();
					mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
				}
				if(!isMasterCard(me, slot) && hasBackUpSlot){
					mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				}else{
					msgPool.removeSendedMsg(mp);
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
				//log.error("SNMP error, or response time out: " + snmpAccess.getIpAddress(), e);
				log.error("#####add Active Error!!!");
				snmpAccess.deleteDatas(fd, conditions);
                return false;
			}catch(Exception e){
				mp.setErrCode(ErrorMessageTable.UNKNOW);
				if(!isMasterCard(me, slot) && hasBackUpSlot){
					mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				}else{
					msgPool.removeSendedMsg(mp);
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
				log.error("#####add Active Error!!!");
				snmpAccess.deleteDatas(fd, conditions);
				return false;
			}
			
		}
		
		else if(addOperaType == 2){
			try{
				for(int i = 0; i< conditions.size(); i++){
					vs.clear();
					Map<String, Object> c = conditions.get(i);
					c.put(rowstatus, 2);
					if(indexNames != null && !indexNames.isEmpty()){
						for(String name : indexNames){
							if(values.get(i).get(name) instanceof Long){
								rowIndex = (Long)values.get(i).get(name);
								c.put(name, rowIndex);
							}else if(values.get(i).get(name) instanceof Integer){
								rowIndex = (Integer)values.get(i).get(name);
								c.put(name, rowIndex);
							}else{
								c.put(name, values.get(i).get(name));
							}
						}
					}
					vs.add(c);
					snmpAccess.addDatas(fd, vs);
					if(count++ > 128){
						count = 0;
						Thread.sleep(getSectionInterval());
					}else{
						Thread.sleep(this.getWriteFrameInterval());
					}
				}

	        	msg.setValues(values);
				
	        	if(values != null && !values.isEmpty()){
					Map<String, Object> v = null;
					if(conditions != null && !conditions.isEmpty()){
						for(Map<String, Object> c : conditions){
							List<Map<String, Object>> cs = null;
							if(!c.isEmpty()){
								cs = new ArrayList<Map<String, Object>>();
								cs.add(c);
							}
//				            								v = values.get(0);
							if(values != null && !values.isEmpty()){
								v = values.get(0);
							}
							if(!v.isEmpty()){
							    snmpAccess.updateDatas(fd, v, cs);
							}
							
							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					}else{
						snmpAccess.updateDatas(fd, values.get(0), null);
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}
				}
				msg.setValues(values);
				
				log.info("begin valid…………");
				for(Map<String, Object> c : conditions){
					c.put(rowstatus, 1);
					if(indexNames != null && !indexNames.isEmpty()){
						for(String name : indexNames){
							if(values.get(0).get(name) instanceof Long){
								rowIndex = (Long)values.get(0).get(name);
								c.put(name, rowIndex);
							}else if(values.get(0).get(name) instanceof Integer){
								rowIndex = (Integer)values.get(0).get(name);
								c.put(name, rowIndex);
							}else{
								c.put(name, values.get(0).get(name));
							}
						}
					}
					vs.add(c);
					ok = snmpAccess.updateDatas(fd, vs.get(0), null);
					if(count++ > 128){
						count = 0;
						Thread.sleep(getSectionInterval());
					}else{
						Thread.sleep(this.getWriteFrameInterval());
					}
					if(!ok){
						break;
					}
				}
				if(!ok){
					snmpAccess.deleteDatas(fd, conditions);
					return false;
				}else{
					msg.setValues(values);
				}
	        	log.info("end valid…………");
			}catch(SnmpDataException e){
				if(e.getAgentErrCode() == SnmpDataException.AGT_ERROR){
					if(e.getProtocolErrCode() > 100){
						log.error("ErrorCode out of range：" + e.getProtocolErrCode() + fd.getName() );
					}
					mp.setErrCode(e.getProtocolErrCode() + 50000);
				}else{
					snmpAccess.close();
					mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
				}
				
				msgPool.removeSendedMsg(mp);
				this.addMsgToProcessedPoolAndCancelProgress(mp);
				//log.error("SNMP error, or response time out: " + snmpAccess.getIpAddress(), e);
				log.error("##### add Active Eroor!!!");
				snmpAccess.deleteDatas(fd, conditions);
				return false;
			}catch(Exception e){
				mp.setErrCode(ErrorMessageTable.UNKNOW);
				msgPool.removeSendedMsg(mp);
				this.addMsgToProcessedPoolAndCancelProgress(mp);
				log.error("##### add Active Eroor!!!");
				snmpAccess.deleteDatas(fd, conditions);
				return false;
			}
			
		}else if(addOperaType == 0){

			//active
			try{
				log.info("#####conditions.size()==" + conditions.size());
				if(conditions.isEmpty()){
					conditions.add(new HashMap<String, Object>());
				}
				for(int i = 0; i< conditions.size(); i++){
					vs.clear();
					Map<String, Object> c = conditions.get(i);
					c.put(rowstatus, 5);
					if(indexNames != null && !indexNames.isEmpty()){
						for(String name : indexNames){
							if(values.get(i).get(name) instanceof Long){
								rowIndex = (Long)values.get(i).get(name);
								c.put(name, rowIndex);
							}else if(values.get(0).get(name) instanceof Integer){
								rowIndex = (Integer)values.get(i).get(name);
								c.put(name, rowIndex);
							}else{
								c.put(name, values.get(i).get(name));
							}
						}
					}
						vs.add(c);
						ok = snmpAccess.addDatas(fd, vs);
						if(count++ > 128){
							count = 0;
							Thread.sleep(getSectionInterval());
						}else{
							Thread.sleep(this.getWriteFrameInterval());
						}
				}

	        	msg.setValues(values);
				
	        	if(values != null && !values.isEmpty()){
					int index = 0;
					Map<String, Object> v = null;
					if(conditions != null && !conditions.isEmpty()){
						for(Map<String, Object> c : conditions){
							List<Map<String, Object>> cs = null;
//							c.put(indexName, rowIndex);
							c.remove(rowstatus);
							if(!c.isEmpty()){
								cs = new ArrayList<Map<String, Object>>();
								cs.add(c);
							}
//				            								v = values.get(0);
							if(values.size() > index){
								v = values.get(index++);
								v.remove(rowstatus);
							}
							if(!v.isEmpty()){
							   ok = snmpAccess.updateDatas(fd, v, cs);
							}
							
							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					}else{
						ok = snmpAccess.updateDatas(fd, values.get(0), null);
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}
					log.info("#######设置值成功？ " + ok);
				}
	        	if(!ok){
					snmpAccess.deleteDatas(fd, conditions);
				}else{
					msg.setValues(values);
				}
				log.info("begin active…………");
				for(Map<String, Object> c : conditions){
					c.put(rowstatus, 1);
					if(indexNames != null && !indexNames.isEmpty()){
						for(String name : indexNames){
							if(values.get(0).get(name) instanceof Long){
								rowIndex = (Long)values.get(0).get(name);
								c.put(name, rowIndex);
							}else if(values.get(0).get(name) instanceof Integer){
								rowIndex = (Integer)values.get(0).get(name);
								c.put(name, rowIndex);
							}else{
								c.put(name, values.get(0).get(name));
							}
						}
					}
					vs.add(c);
					ok = snmpAccess.addDatas(fd, vs);
					log.info("#######Active Success? " + ok);
					if(count++ > 128){
						count = 0;
						Thread.sleep(getSectionInterval());
					}else{
						Thread.sleep(this.getWriteFrameInterval());
					}
					if(!ok){
						break;
					}
				}
				if(!ok){
					snmpAccess.deleteDatas(fd, conditions);
					return false;
				}else{
					msg.setValues(values);
					if(subFuncNames != null){
						for(String subFunc : subFuncNames){
							vs = msg.getSubValues(subFunc);
							if(vs == null || vs.isEmpty()){
								continue;
							}
							fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, subFunc);
//							fd = neDes.getFuncDesByName(subFunc);
							
							List<Map<String, Object>> subConditions = msg.getSubConditions(subFunc);
                            if(subConditions == null){
                            	subConditions = conditions;
                            }
                            Map<String, Object> v = vs.get(0);
							if(subConditions != null && !subConditions.isEmpty()){
								for(int i = 0; i < subConditions.size(); i++){
									Map<String, Object> c = new HashMap<String, Object>();
    								List<Map<String, Object>> cs = null;
    								if(!c.isEmpty()){
    									cs = new ArrayList<Map<String, Object>>();
        								cs.add(c);
    								}
//						            								v = vs.get(0);
    								if(vs  == null){
    									v = values.get(0);
    								}else if(vs.size() > i){
    									v = vs.get(i);
    								}
    								if(!v.isEmpty()){
    									snmpAccess.updateDatas(fd, v, cs);
    								}
    								
    								Thread.sleep(this.getWriteFrameInterval() * 10);
    							}
							}else{
								snmpAccess.updateDatas(fd, v, null);
								Thread.sleep(this.getWriteFrameInterval() * 10);
							}
							
							
							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					}
				}
			}catch(SnmpDataException e){
				if(e.getAgentErrCode() == SnmpDataException.AGT_ERROR){
					if(e.getProtocolErrCode() > 100){
						log.error("ErrorCode out of range：" + e.getProtocolErrCode() + fd.getName() );
					}
					mp.setErrCode(e.getProtocolErrCode() + 50000);
				}else{
					snmpAccess.close();
					mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
				}
				if(!isMasterCard(me, slot) && hasBackUpSlot){
					mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				}else{
					msgPool.removeSendedMsg(mp);
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
				//log.error("SNMP error, or response time out: " + snmpAccess.getIpAddress(), e);
				log.error("##### add Active Eroor!!!");
				snmpAccess.deleteDatas(fd, conditions);
				return false;
			}catch(Exception e){
				mp.setErrCode(ErrorMessageTable.UNKNOW);
				if(!isMasterCard(me, slot) && hasBackUpSlot){
					mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				}else{
					msgPool.removeSendedMsg(mp);
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
				log.error("##### add Active Eroor!!!");
				snmpAccess.deleteDatas(fd, conditions);
				return false;
			}

        	log.info("edd active…………");
		}else if(addOperaType == 1){//公有mib添加
			try{
				if(values != null && !values.isEmpty()){
					int index = 0;
					Map<String, Object> v = null;
					if(conditions != null && !conditions.isEmpty()){
						for(Map<String, Object> c : conditions){
							List<Map<String, Object>> cs = null;
//							c.put(indexName, rowIndex);
							c.remove(rowstatus);
							if(!c.isEmpty()){
								cs = new ArrayList<Map<String, Object>>();
								cs.add(c);
							}
//				            								v = values.get(0);
							if(values.size() > index){
								v = values.get(index++);
							}
							if(!v.isEmpty()){
							    snmpAccess.updateDatas(fd, v, cs);
							}
							
							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					}else{
						snmpAccess.updateDatas(fd, values.get(0), null);
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}
					if(subFuncNames != null){
						int i = 1;
						for(String subFunc : subFuncNames){
							vs = msg.getSubValues(subFunc);
							
							fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, subFunc);
//							fd = neDes.getFuncDesByName(subFunc);
							
							List<Map<String, Object>> subConditions = msg.getSubConditions(subFunc);
                            if(subConditions == null){
                            	subConditions = conditions;
                            }
							if(subConditions != null && !subConditions.isEmpty()){
								for(Map<String, Object> c : conditions){
    								List<Map<String, Object>> cs = null;
    								if(!c.isEmpty()){
    									cs = new ArrayList<Map<String, Object>>();
        								cs.add(c);
    								}
//						            								v = vs.get(0);
    								if(vs  == null){
    									v = values.get(i++);
    								}else if(vs.size() > index){
    									i--;
    									v = vs.get(i++);
    								}
    								if(!v.isEmpty()){
    									snmpAccess.updateDatas(fd, v, cs);
    								}
    								
    								Thread.sleep(this.getWriteFrameInterval() * 10);
    							}
							}else{
								snmpAccess.updateDatas(fd, v, null);
								Thread.sleep(this.getWriteFrameInterval() * 10);
							}
							
							
							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					}
				}
				msg.setValues(values);
				//createAndGo
				for(int i = 0; i< conditions.size(); i++){
					vs.clear();
					Map<String, Object> c = conditions.get(i);
					c.put(rowstatus, 4);
					if(indexNames != null && !indexNames.isEmpty()){
						for(String name : indexNames){
							if(values.get(i).get(name) instanceof Long){
								rowIndex = (Long)values.get(i).get(name);
								c.put(name, rowIndex);
							}else if(values.get(i).get(name) instanceof Integer){
								rowIndex = (Integer)values.get(i).get(name);
								c.put(name, rowIndex);
							}else{
								c.put(name, values.get(i).get(name));
							}
						}
					}
						vs.add(c);
						ok = snmpAccess.addDatas(fd, vs);
						if(count++ > 128){
							count = 0;
							Thread.sleep(getSectionInterval());
						}else{
							Thread.sleep(this.getWriteFrameInterval());
						}
						if(!ok){
							break;
						}
				}
				if(!ok){
					snmpAccess.deleteDatas(fd, conditions);
					return false;
				}else{
					msg.setValues(values);
				}
			}catch(SnmpDataException e){
				if(e.getAgentErrCode() == SnmpDataException.AGT_ERROR){
					if(e.getProtocolErrCode() > 100){
						log.error("ErrorCode out of range：" + e.getProtocolErrCode() + fd.getName() );
					}
					mp.setErrCode(e.getProtocolErrCode() + 50000);
				}else{
					snmpAccess.close();
					mp.setErrCode(ErrorMessageTable.NE_RESPONSE_TIME_OUT);
				}
				if(!isMasterCard(me, slot) && hasBackUpSlot){
					mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				}else{
					msgPool.removeSendedMsg(mp);
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
				//log.error("SNMP error, or response time out: " + snmpAccess.getIpAddress(), e);
				log.error("##### add Active Eroor!!!");
				snmpAccess.deleteDatas(fd, conditions);
                return false;
			}catch(Exception e){
				mp.setErrCode(ErrorMessageTable.UNKNOW);
				if(!isMasterCard(me, slot) && hasBackUpSlot){
					mp.setErrCode(ErrorMessageTable.PART_SUCCESS);
				}else{
					msgPool.removeSendedMsg(mp);
					this.addMsgToProcessedPoolAndCancelProgress(mp);
				}
				log.error("##### add Active Eroor!!!");
				snmpAccess.deleteDatas(fd, conditions);
				return false;
			}
        	
		}
	return true;
}

	private void deleteAndCreate(ManagedElement me, int slot, NeDescriptor neDes,
		FunctionDescriptor fd, SnmpNewMessage msg, SnmpDataAccess snmpAccess,
		List<String> fields, String funcName, List<Map<String, Object>> values,
		List<Map<String, Object>> conditions, List<String> subFuncNames) throws Exception {
		int count=0;
		List<AttributeDescriptor> attrList = fd.getAttributeList();
		String  rowstatus = "";
		List<String>  indexNames = new ArrayList<String>();
		long rowIndex = 0;
		int addOperaType = 0;
		for(AttributeDescriptor attr : attrList){
			if(attr.getExternalName()!=null&&attr.getExternalPropMask()!=null){
					if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatus)){
						rowstatus = attr.getName();
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.EntryStatus)){
						rowstatus = attr.getName();
						addOperaType = 2;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForUpdateThenCreate)){
						rowstatus = attr.getName();
						addOperaType = 1;
					}else if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForAddAndUpdate)){
						rowstatus = attr.getName();
						addOperaType = 3;
					}
					
					if(attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)){
						indexNames.add(attr.getName());
						
					}	
				}
		}
		List<Map<String, Object>> vs = new ArrayList<Map<String, Object>>();
		if(addOperaType == 4) { 
			for(Map<String, Object> c : conditions){
				c.put(rowstatus, 4);
				if(indexNames != null && !indexNames.isEmpty()){
					for(String name : indexNames){
						if(values.get(0).get(name) instanceof Long){
							rowIndex = (Long)values.get(0).get(name);
							c.put(name, rowIndex);
						}else if(values.get(0).get(name) instanceof Integer){
							rowIndex = (Integer)values.get(0).get(name);
							c.put(name, rowIndex);
						}else{
							c.put(name, values.get(0).get(name));
						}
					}
				}
					vs.add(c);
					snmpAccess.addDatas(fd, vs);
					if(count++ > 128){
						count = 0;
						Thread.sleep(getSectionInterval());
					}else{
						Thread.sleep(this.getWriteFrameInterval());
					}
			}
        	msg.setValues(values);
        	
        	if(values != null && !values.isEmpty()){
				int index = 0;
				Map<String, Object> v = null;
				if(conditions != null && !conditions.isEmpty()){
					for(Map<String, Object> c : conditions){
						List<Map<String, Object>> cs = null;
						if(!c.isEmpty()){
							cs = new ArrayList<Map<String, Object>>();
							cs.add(c);
						}
//						v = values.get(0);
						if(values.size() > index){
							v = values.get(index++);
						}
						if(!v.isEmpty()){
							try{
								snmpAccess.updateDatas(fd, v, cs);
							}catch(Exception ex){
								log.error(ex, ex);
							}
						}
						
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}
				}else{
					snmpAccess.updateDatas(fd, values.get(0), null);
					Thread.sleep(this.getWriteFrameInterval() * 10);
				}
			}
			msg.setValues(values);
		}else{
			for(Map<String, Object> c : conditions){
				if(null != c.get(rowstatus)){
					vs.add(c);
					snmpAccess.addDatas(fd, vs);
					if(count++ > 128){
						count = 0;
						Thread.sleep(getSectionInterval());
					}else{
						Thread.sleep(this.getWriteFrameInterval());
					}
				}
			}

        	msg.setValues(values);
			
        	if(values != null && !values.isEmpty()){
				int index = 0;
				Map<String, Object> v = null;
				if(conditions != null && !conditions.isEmpty()){
					for(Map<String, Object> c : conditions){
						List<Map<String, Object>> cs = null;
						if(!c.isEmpty()){
							cs = new ArrayList<Map<String, Object>>();
							cs.add(c);
						}
//				            								v = values.get(0);
						if(values.size() > index){
							v = values.get(index++);
						}
						if(!v.isEmpty()){
							try{
								snmpAccess.updateDatas(fd, v, cs);
							}catch(Exception ex){
								log.error(ex, ex);
							}
						}
						
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}
				}else{
					snmpAccess.updateDatas(fd, values.get(0), null);
					Thread.sleep(this.getWriteFrameInterval() * 10);
				}
			}
			msg.setValues(values);
		}
	
}

	private void delete(FunctionDescriptor fd, SnmpDataAccess snmpAccess,
		List<Map<String, Object>> conditions) throws Exception{
		int count=0;
		for(Map<String, Object> m : conditions){
			List<Map<String, Object>> cs = new ArrayList<Map<String, Object>>();
			cs.add(m);
			snmpAccess.deleteDatas(fd, cs);
			if(fd.getName().equalsIgnoreCase("GpnEstAgentTrapReceiverEntry")){
				Thread.sleep(5000);//针对0402B/P
			}
			if(count++ > 128){
				count = 0;
				Thread.sleep(getSectionInterval());
			}else{
				Thread.sleep(this.getWriteFrameInterval());
			}
		}
	
}

	private void add(ManagedElement me, int slot, NeDescriptor neDes,
		FunctionDescriptor fd, SnmpNewMessage msg, SnmpDataAccess snmpAccess,
		List<String> fields, String funcName, List<Map<String, Object>> values,
		List<Map<String, Object>> conditions, List<String> subFuncNames) throws Exception{
		int count=0;
		for(Map<String, Object> m : values){
			List<Map<String, Object>> vs = new ArrayList<Map<String, Object>>();
			vs.add(m);
			
//			for(int i = 0; i < 2; i++){//出错后重发
//				try{
//					snmpAccess.addDatas(funcName, vs);
//					break;
//				}catch(Exception ex){
//					log.error("snmp exception", ex);
//					Thread.sleep(5000);
//				}
//			}
			
			snmpAccess.addDatas(fd, vs);
			
			if(count++ > 128){
				count = 0;
				Thread.sleep(getSectionInterval());
			}else{
				Thread.sleep(this.getWriteFrameInterval());
			}
		}
//    	snmpAccess.addDatas(funcName, values);
    	msg.setValues(values);
	
}

	private void update(ManagedElement me, int slot, NeDescriptor neDes,
			FunctionDescriptor fd, SnmpNewMessage msg,
			SnmpDataAccess snmpAccess, List<String> fields, String funcName,
			List<Map<String, Object>> values,
			List<Map<String, Object>> conditions, List<String> subFuncNames) throws Exception{
		if (values != null && !values.isEmpty()) {
			int index = 0;
			Map<String, Object> v = null;
			if (conditions != null && !conditions.isEmpty()) {
				for (Map<String, Object> c : conditions) {
					List<Map<String, Object>> cs = null;
					if (!c.isEmpty()) {
						cs = new ArrayList<Map<String, Object>>();
						cs.add(c);
					}
					// v = values.get(0);
					if (values.size() > index) {
						v = values.get(index++);
					}
					if (!v.isEmpty()) {
						snmpAccess.updateDatas(fd, v, cs);
					}

					Thread.sleep(this.getWriteFrameInterval() * 10);
				}
			} else {
				snmpAccess.updateDatas(fd, values.get(0), null);
				Thread.sleep(this.getWriteFrameInterval() * 10);
			}
			if (subFuncNames != null) {
				int i = 0;
				List<Map<String, Object>> vs = null;
				for (String subFunc : subFuncNames) {
					vs = msg.getSubValues(subFunc);

					fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, subFunc);
//					fd = neDes.getFuncDesByName(subFunc);
					// snmpAccess.updateDatas(fd, v, null);
					if (fd == null) {
						EquipmentHolder eh = me.getEquipmentHolder(slot);
						if (eh != null) {
							Equipment eqp = eh.getInstalledEquipment();
							EquipmentDescriptor ed = neDes
									.getEquipmentDescriptor(eqp.getName(), eqp.getSoftwareVersion());
							fd = ed.getFuncDesByName(subFunc);
						}
					}
					List<Map<String, Object>> subConditions = msg
							.getSubConditions(subFunc);
					if (subConditions == null) {
						subConditions = conditions;
					}
					if (subConditions != null && !subConditions.isEmpty()) {
						for (Map<String, Object> c : subConditions) {
							List<Map<String, Object>> cs = null;
							if (!c.isEmpty()) {
								cs = new ArrayList<Map<String, Object>>();
								cs.add(c);
							}
							// v = vs.get(0);
							if (vs == null) {
								v = values.get(i++);
							} else if (vs.size() == subConditions.size()) {
								v = vs.get(0);
							} else {
								v = vs.get(0);
							}
							if (!v.isEmpty()) {
								snmpAccess.updateDatas(fd, v, cs);
							}

							Thread.sleep(this.getWriteFrameInterval() * 10);
						}
					} else {
						snmpAccess.updateDatas(fd, v, null);
						Thread.sleep(this.getWriteFrameInterval() * 10);
					}

					Thread.sleep(this.getWriteFrameInterval() * 10);
				}
			}
		}
		msg.setValues(values);

	}

	private void qurey(DeviceDest dest, ManagedElement me, int slot, NeDescriptor neDes,
			FunctionDescriptor fd, SnmpNewMessage msg,
			SnmpDataAccess snmpAccess, List<String> fields, String funcName,
			List<Map<String, Object>> values,
			List<Map<String, Object>> conditions, List<String> subFuncNames) {
		log.info("begin Qurey。。。。。" + funcName);
		if(fields == null && conditions == null){
	    	values = snmpAccess.getDatas(fd);
		}else if(fields != null){
			values = snmpAccess.getDatas(fd, fields, conditions);
		}else{
			values = snmpAccess.getDatas(fd, fields, conditions);
		}
		log.info("Qurey end。。。。。" + funcName + values.size());
		Map<String, List<Map<String, Object>>> subValueMap = new HashMap<String, List<Map<String, Object>>>();
		
		if(subFuncNames != null){
			List<Map<String, Object>> vs = null;
			for(String subFunc : subFuncNames){
				fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, dest.getSlot(), subFunc);
//				fd = neDes.getFuncDesByName(subFunc);
				if(fd == null){
					if(dest != null && dest.getEquipName() != null){
						EquipmentDescriptor ed = neDes.getEquipmentDescriptor(dest.getEquipName());
						fd = ed.getFuncDesByName(subFunc);
					}else{
						EquipmentHolder eh = me.getEquipmentHolder(slot);
						if(eh != null){
							Equipment eqp = eh.getInstalledEquipment();
						
							EquipmentDescriptor ed = neDes.getEquipmentDescriptor(eqp.getName());
							fd = ed.getFuncDesByName(subFunc);
						}
					}
				}
				 List<Map<String, Object>> subConditionsList =msg.getSubConditions(subFunc);
					vs = snmpAccess.getDatas(fd, null, subConditionsList);
					if(vs != null){
						subValueMap.put(subFunc, vs);
				 }
			}
		}
		
//		//调用特殊逻辑处理器
//    	values = handleValues(values);
		msg.setValues(values);
		if(!subValueMap.isEmpty()){
			msg.setSubValues(subValueMap);
		}
		
	}


	private void dicovery(boolean isDiscovery, List<Map<String, Object>> values,
		SnmpDataAccess snmpAccess, SnmpNewMessage msg) {
		isDiscovery = true;
		values = new ArrayList<Map<String, Object>>();
		String oid = null;
		try{
			oid = snmpAccess.getObjId();
		}catch(Exception e){
			snmpAccess.setReadCommunity("admin");
			oid = snmpAccess.getObjId();
		}
		
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("oid", oid);
		values.add(m);
		msg.setValues(values);
	}
	
	private Object sendCommandByCli(String ip, int port, String userName, String password, String cli, NeDescriptor nedes,SnmpNewMessage msg){
		CliDataAccess cda = CliDataAccessPool.getInstance(this.appContext).getCliDataAccess(ip, port, userName, password, nedes);
		Object result = null;
		try{
			result = cda.sendCommand(cli,msg);
		}catch(Exception e){
			log.info("WriteSessionTaskSnmpNew, sendCommandByCli", e);
		}
	
		return result;
	}
	private Object sendCloseCommandByCli(String ip, int port, String userName, String password, NeDescriptor nedes,SnmpNewMessage msg){
		CliDataAccess cda = CliDataAccessPool.getInstance(this.appContext).getCliDataAccess(ip, port, userName, password, nedes);
		try{
			cda.disconnect();
		}catch(Exception e){
			log.info("WriteSessionTaskSnmpNew, sendCloseCommandByCli", e);
		}
	
		return null;
	}
	private void reportNoResAlarm(ManagedElement me, int emt){
		//通知上报失连告警
		Response res = new Response();
        res.setcmdId("neConnStateFunc");
        CommandDestination cd = new CommandDestination();
        cd.setNeId(me.getId());
        res.setDest(cd);
        res.setResult(emt);
//	    res.addParam(new CommandParameter(event));
        ResponseCollection rc = new ResponseCollection();
        rc.addResponse(res);
        SyncNotifyManager.getInstance().notifyToServer(rc);
	}
	protected int getWriteFrameInterval() {
		return ToolUtil.getEntryConfig().getWriteFrameInterval();
	}
	protected int getSectionInterval(){
		if(sectionInterval != null && sectionInterval > 0){
			return sectionInterval;
		}
		String interval = ToolUtil.getProperty("SECTION_INTERNAL");
		if(interval != null){
			sectionInterval = Integer.valueOf(interval);
			if(sectionInterval != null){
				return sectionInterval;
			}
		}
		return 0;
	}
}
