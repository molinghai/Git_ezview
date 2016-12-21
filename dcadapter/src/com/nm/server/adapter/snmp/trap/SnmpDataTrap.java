package com.nm.server.adapter.snmp.trap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.context.ApplicationContext;


import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.nmm.res.Address.AddressType;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.NatInfo;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.comm.SnmpMessage;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.adapter.snmp.SnmpDataAccess;
import com.nm.server.adapter.snmp.pool.SnmpDataAccessPool;
import com.nm.server.comm.HiSysMessage.HiMsgType;
import com.nm.server.comm.MessagePool;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class SnmpDataTrap {
	protected Log log = LogFactory.getLog(this.getClass());

	private static SnmpDataTrap instance;

	private TopoManager topoManager;

	private Map<Integer,ManagedElement> neMap;
	// private List<SnmpTrapListener> trapListeners = new
	// ArrayList<SnmpTrapListener>();
	private Map<Integer, Boolean> trapMap = new HashMap<Integer, Boolean>();

	private MessagePool snmpMsgPool; // 消息池，处理完的消息放到池里返回


	private Map<String, TrapHandler> oidHanlderMap = new ConcurrentHashMap<String, TrapHandler>();
	// private Map<String, String> oidNameMap = new ConcurrentHashMap<String,
	// String>();

	private static int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();

	public static Map<Integer, Snmp> trapSnmpMap = new ConcurrentHashMap<Integer, Snmp>();

	// for new gpn ne
	private Map<String, NeTrapHandler> neTrapHandlerMap = new ConcurrentHashMap<String, NeTrapHandler>();
	// dhcp ne discovery
	private OID neBasicInfoAlmInfo;
	private OID neBasicInfoAlmInfo2;
	private OID nePwrDownAlmInfo;

	protected Map<String, String> PX10SAVEDIPsMap = new HashMap<String, String>();// 存放PX10的XE的IP
	
	private BlockingQueue<CommandResponderEvent> frameQueue = new LinkedBlockingQueue<CommandResponderEvent>();
	
	protected ApplicationContext context;
	
	public ApplicationContext getContext() {
		return context;
	}

	public void setContext(ApplicationContext context) {
		this.context = context;
	}
	
	private SnmpDataTrap() {
	}

	public static SnmpDataTrap getInstance() {
		if (instance == null) {
			instance = new SnmpDataTrap();
		}
		return instance;
	}
	
	@SuppressWarnings("unused")
	private void initTrapThreads(){
		Integer threadNum = null;
		try{
			threadNum = Runtime.getRuntime().availableProcessors() + 1;
		}catch( Exception ex){
			log.error(ex, ex);
		}
		if(threadNum == null || threadNum < 1){
			threadNum = 5;
		}
	}

	private void initTrapTasks(){
		try{
			Integer threadNum = Runtime.getRuntime().availableProcessors() + 1;
			String value  = ToolUtil.getProperty("TRAP_THREAD_NUM");
			if(value != null){
				try{
					threadNum = Integer.valueOf(value);
				}catch(Exception e){
					log.error(e, e);
				}
			}
			if(threadNum == null || threadNum < 1){
				threadNum = 5;
			}
		}catch( Exception ex){
			log.error(ex, ex);
		}
	}
	private void startRecordFrameInfo(){
		new Thread(){
			public void run(){
				CommandResponderEvent cre = null;
				while(true){
					try{
						cre = frameQueue.take();
						if(cre != null){
							FileTool.write("FrameInfo", "Trap", cre.getPeerAddress() + "\n" + cre.getPDU());
						}
					}catch(Exception e){
						log.error(e, e);
					}
				}
			}
		}.start();
	}
	public void setTrapHandler(String oid, TrapHandler handler) {
		oidHanlderMap.put(oid, handler);
	}

	public void setNeTrapHandler(String oid, NeTrapHandler handler) {
		neTrapHandlerMap.put(oid, handler);
	}

	public void setTopoManager(TopoManager topoManager) {
		this.topoManager = topoManager;
	}

	
	public void setNeMap(Map<Integer,ManagedElement> neMap) {
		this.neMap = neMap;
	}

	public Map<Integer,ManagedElement> getNeMap() {
		return neMap;
	}

	public MessagePool getSnmpMsgPool() {
		return snmpMsgPool;
	}

	public void setSnmpMsgPool(MessagePool snmpMsgPool) {
		this.snmpMsgPool = snmpMsgPool;
	}

	public void startListenTrap(List<Integer> ports) {
		initTrapTasks();
		startRecordFrameInfo();
		try {
			if (neBasicInfoAlmInfo == null) {
				String oid = SnmpDataAccess.getOID("dc", "neBasicInfoAlmInfo");
				if(oid == null){
					oid = SnmpDataAccess.getOID(null, "neBasicInfoAlmInfo");
				}
				neBasicInfoAlmInfo = new OID(oid);
			}
			if (neBasicInfoAlmInfo2 == null) {
				String oid = SnmpDataAccess.getOID("dc", "neBasicInfoAlmInfo");
				if(oid == null){
					oid = SnmpDataAccess.getOID(null, "neBasicInfoAlmInfo");
				}
				neBasicInfoAlmInfo2 = new OID(oid);
			}
			if (nePwrDownAlmInfo == null) {
				String oid = SnmpDataAccess.getOID("dc", "nePwrDownAlmInfo");
				if(oid == null){
					oid = SnmpDataAccess.getOID(null, "nePwrDownAlmInfo");
				}
				nePwrDownAlmInfo = new OID(oid);
			}
		} catch (Exception ex) {
			log.error("load neBasicInfoAlmInfo and nePwrDownAlmInfo failed!!", ex);
		}

		if (neBasicInfoAlmInfo == null) {
			neBasicInfoAlmInfo = new OID("1.3.6.1.4.1.9966.1.11");
		}
		if (neBasicInfoAlmInfo2 == null) {
			neBasicInfoAlmInfo2 = new OID("1.3.6.1.4.1.9966.10.2");
		}
		
		//增加CPX10端口
		for(int p = 16201; p < 16220; p++){
			ports.add(p);
		}
		log.info("starting listen snmp trap...");
		for (final Integer port : ports) {
			// 防止重复
			Boolean b = trapMap.get(port);
			if (b != null && b == Boolean.TRUE) {
				continue;
			}
			if(port <= 0 || port > 65535){
				continue;
			}
			trapMap.put(port, Boolean.TRUE);
			log.info("starting listen snmp trap, port=" + port);

			try {
				TransportMapping transport = null;
				Snmp trap = trapSnmpMap.get(port);
				if (trap == null) {
					UdpAddress udpAddr = new UdpAddress();
					udpAddr.setPort(port);
					transport = new DefaultUdpTransportMapping(udpAddr);
					trap = new Snmp(transport);
					trapSnmpMap.put(port, trap);

					CommandResponder trapListener = new CommandResponder() {
						public void processPdu(CommandResponderEvent e) {
							final CommandResponderEvent cre = e;
							Runnable trapTask = new Runnable() {
								public void run() {
									processTrap(cre, port);
								}
							};
							Executors.newCachedThreadPool().execute(trapTask);
						}
					};
					trap.addCommandResponder(trapListener);
					if (transport != null && !transport.isListening()) {
						transport.listen();
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.error("init snmp trap error!!", e);
//				JOptionPane.showMessageDialog(null, "port:" + port + " has been occupied!");
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void processTrap(CommandResponderEvent e, Integer port) {
		try {
			log.info("trap port:::" + port);
			PDU pdu = e.getPDU();
			if (pdu != null) {
				Address addr = e.getPeerAddress();
				String strAddr = addr.toString();
				if (frameOn == 1) {
					frameQueue.put(e);
//					FileTool.write("FrameInfo", "Trap", strAddr + "\n" + pdu.toString());
				}
				if (isDiscoveryTrap(pdu)) {
					log.info("dhcp discovery!");
					this.processDiscoveryTrap(e);
				} else {
					int result = pdu.getErrorIndex();
					if (result != 0) {
						return;
					}

					int index = strAddr.lastIndexOf('/');
					String ip = strAddr.substring(0, index);
					String rPort = strAddr.substring(index + 1, strAddr.length());
					com.nm.nmm.res.Address address = new com.nm.nmm.res.Address();
					address.setAddressType(AddressType.AT_IPV4);
					Long ipLong = ToolUtil.ipToLong(ip);
					address.setIpAddress(ipLong);
					

					ManagedElement me = null;
					int slot = -1;
					boolean isPX10 = false;
					boolean isCPX10 = false;
					
					me = getMangedElement(ipLong,port);
					NeDescriptor neDes=null;
					if(me!=null){
						neDes =NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
					}
					if(neDes!=null){
						FunctionDescriptor fd = neDes.getFuncDesByName("nmNatMapping");
						if(fd!=null){
							ManagedElement natNe = this.getNatNe(ipLong,Integer.valueOf(rPort));
							if(natNe!=null){
								me = natNe;
							}
						}
						
					}
					
					if(port >= 16200 && port <= 16220){
						isCPX10 = true;
						slot = port - 16200;
						log.info("CPX10 trap...IP:" + ip);
					}else{
						String IP_PORT = getPX10NMXEIP(ip);// PX10所在NM的IP
						if (IP_PORT != null && !"".equals(IP_PORT)) {
							isPX10 = true;
							String[] ss = IP_PORT.split(":");
							slot = Integer.valueOf(ss[1]);
							ip = ss[0];
							address.setIpAddress(ToolUtil.ipToLong(ss[0]));
							me = this.getMangedElement(address.getIpAddress(), port);
					} else {
							me = this.getMangedElement(address.getIpAddress(),port);
							if (me != null) {
								neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
								if (neDes != null) {
									FunctionDescriptor fd = neDes.getFuncDesByName("nmNatMapping");
									if (fd != null) {
										ManagedElement natNe = this.getNatNe(ipLong, Integer.valueOf(rPort));
										if (natNe != null) {
											me = natNe;
										}
									}

								}
							}
						}
					}
					//ManagedElement me = (ManagedElement) topoManager.getByAddress(address);

					
					if (me == null) {
						log.error("can't find me-->>" + address.getIpAddressStr());
						return;
					}

					String oidName = null;
					List<Map<String, Object>> alarms = new ArrayList<Map<String, Object>>();

					 neDes = null;
					if(isPX10 || isCPX10){
						neDes = NeDescriptionLoader.getNeDescriptor("NE403", "1.0");
					}else{
						neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
					}
					
					if (neDes == null) {
						log.error("---Trap Error---Can't find Ne Desc:" + me.getProductName() + " " + me.getVersion());
						return;
					}
					String domain = neDes.getFunctionDesList().get(0).getDomain();
					if(isPX10){
						domain = "gpn";
					}
//					if(neDes.isSnmp()){
//						domain = "gpn";
//					}
					log.info("Trap Ne domain: " + domain);

					if (!neDes.isSupportPlatform()) {// old
						SnmpMessage msg = new SnmpMessage();
						msg.setPdu(pdu);
						msg.setAddress(me.getAddress().getIpAddressStr());
						msg.setNeType(me.getProductName());
						msg.getCommandId();
						snmpMsgPool.insertTrapMessage(msg);

//						if (frameOn == 1) {
//							StringBuilder data = new StringBuilder(addr.toString());
//							Vector<? extends VariableBinding> vbs = pdu.getVariableBindings();
//							for (VariableBinding vb : vbs) {
//								data.append("\n");
//								data.append(vb.getOid().toString());
//								data.append("\n");
//							}
//							FileTool.write("FrameInfo", "Trap", pdu.getRequestID().toString() + "--" + data.toString());
//						}
						return;
					} else if (domain != null && domain.toLowerCase().equals("gpn")) {
	                     Vector<? extends VariableBinding> vbs = pdu.getVariableBindings();
	                     // OID oid = vbs.get(1).getOid();
	                     String temOid = null;
	                     if(pdu.getType() == PDU.V1TRAP){
	                         for (VariableBinding vb : vbs) {
	                            if(vb.getOid().toString().equals("1.3.6.1.6.3.1.1.4.1.0")){
	                                temOid = vb.getVariable().toString();
	                                break;
	                            }
	                         }
	                         if(temOid == null){
	                            PDUv1 v1Pdu = (PDUv1)pdu;
	                            temOid = v1Pdu.getEnterprise().toString() + "." + v1Pdu.getSpecificTrap();
	                         }
	                      }else{
	                         temOid = vbs.get(1).getVariable().toString();
	                      }

	                     NeTrapHandler handler = neTrapHandlerMap.get(temOid);
	                     
	                     if (handler != null) {
	                    	 List<Map<String, Object>> almList = null;
	                    	 synchronized(handler){
	                    		 handler.setMe(me);
		                         handler.setIp(me.getAddress().getIpAddressStr());
		                         handler.setTrapPort(port);
	                         	 handler.setContext(this.context);
		                         almList = handler.handle(pdu);
	                    	 }
	                    	 
	                         batchSetIP(almList, me.getAddress().getIpAddressStr());
	                         batchSetSlot(almList, slot);
	                         alarms.addAll(almList);
	                         oidName = handler.getCommand();
	                     } else {
	                         log.error("gpn Can't find TrapHandler for oid:" + temOid);
	                     }
	                  } else {
	                     List<Map<String, Object>> almList = null;
	                     TrapHandler handler = null;
	                     Vector<? extends VariableBinding> vbs = pdu.getVariableBindings();

	                     for (VariableBinding vb : vbs) {
                            OID oid = vb.getOid();
                            OID temOid = (OID) oid.clone();
                            while (true) {
                                if (!temOid.isValid() || temOid.isException()) {
                                   log.error("Can't find TrapHandler for oid:" + vb.getOid().toString());
                                   break;
                                }
                                handler = oidHanlderMap.get(temOid.toString());
                                if (handler != null) {
                                	handler.setMe(me);
                                   almList = handler.handle(vb);
                                   batchSetIP(almList, me.getAddress().getIpAddressStr());
                                   alarms.addAll(almList);
                                   oidName = handler.getOidName();
                                   break;
                                } else {
                                   // log.error("Can't find TrapHandler for oid:"
                                   // +
                                   // vb.getOid().toString());
                                }
                                temOid.removeLast();
                            }
                         }
	                  }
					if (!alarms.isEmpty()) {
						SnmpNewMessage msg = new SnmpNewMessage();
						String cmdId = oidName;
						msg.setCommandId(cmdId);
						msg.setAddress(me.getAddress().getIpAddressStr());
						msg.setValues(alarms);
						if(isPX10){
							msg.setNeType("NE403");
							msg.setNeVersion("1.0");
						}
						snmpMsgPool.insertTrapMessage(msg);
					}
				}
			}
		 
		} catch (Exception ex) {
			log.error(ex, ex);
		}
	}

	private void batchSetIP(List<Map<String, Object>> almList, String ip) {
		if (null != almList && !almList.isEmpty()) {
			Iterator<Map<String, Object>> iterator = almList.iterator();
			Map<String, Object> alm;
			while (iterator.hasNext()) {
				alm = iterator.next();
				if (null != alm) {
					alm.put("ip", ip);
				}
			}
		}
	}
	
	private void batchSetSlot(List<Map<String, Object>> almList, int slot) {
		if (null != almList && !almList.isEmpty()) {
			Iterator<Map<String, Object>> iterator = almList.iterator();
			Map<String, Object> alm;
			while (iterator.hasNext()) {
				alm = iterator.next();
				if (null != alm && (alm.get("slot") == null || (Integer)alm.get("slot") == 0)) {
					alm.put("slot", slot);
				}
			}
		}
	}

	public boolean isDiscoveryTrap(PDU pdu) {
		if (pdu != null) {
			@SuppressWarnings("unchecked")
			Vector<? extends VariableBinding> vbs = pdu.getVariableBindings();
			for (VariableBinding vb : vbs) {
				OID oid = vb.getOid();
				if(oid.toString().contains("1.3.6.1.4.1.9966.1.11")){
					return true;
				}
				OID temOid = (OID) oid.clone();
				temOid.removeLast();
				if (neBasicInfoAlmInfo != null && temOid.equals(neBasicInfoAlmInfo)) {
					return true;
				}
				if (neBasicInfoAlmInfo2 != null && temOid.equals(neBasicInfoAlmInfo2)) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void processDiscoveryTrap(CommandResponderEvent e) {
		try{
			String ipAddr = e.getPeerAddress().toString();

			// 给设备发应答命令
			

			PDU pdu = e.getPDU();
			if (pdu != null) {
		
				log.info(pdu.toString());

				Vector<? extends VariableBinding> vbs = pdu.getVariableBindings();
				for (VariableBinding vb : vbs) {
					OID oid = (OID) vb.getOid().clone();
					if(pdu.getType() != PDU.V1TRAP){
						oid.removeLast();
					}
					
					if (oid.compareTo(neBasicInfoAlmInfo) == 0 || oid.compareTo(neBasicInfoAlmInfo2) == 0 || oid.toString().contains("1.3.6.1.4.1.9966.1.11")) {
//						35:eb:00:1d:80:b5:b6:b9:00:c0:c0:02:6f:ff:ff:ff:00:c0:c0:02:02:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00						
						OctetString variable = (OctetString) vb.getVariable();
						byte[] values = variable.getValue();
						int index = 0;
						int seriesId = values[index++];
						if (seriesId < 0) {
							seriesId += 256;
						}
						int modelId = values[index++];
						if (modelId < 0) {
							modelId += 256;
						}
						int mac1 = values[index++];
						if (mac1 < 0) {
							mac1 += 256;
						}
						int mac2 = values[index++];
						if (mac2 < 0) {
							mac2 += 256;
						}
						int mac3 = values[index++];
						if (mac3 < 0) {
							mac3 += 256;
						}
						int mac4 = values[index++];
						if (mac4 < 0) {
							mac4 += 256;
						}
						int mac5 = values[index++];
						if (mac5 < 0) {
							mac5 += 256;
						}
						int mac6 = values[index++];
						if (mac6 < 0) {
							mac6 += 256;
						}
						
						@SuppressWarnings("unused")
						int reserved = values[index++];

						int ip1 = values[index++];
						if (ip1 < 0) {
							ip1 += 256;
						}
						int ip2 = values[index++];
						if (ip2 < 0) {
							ip2 += 256;
						}
						int ip3 = values[index++];
						if (ip3 < 0) {
							ip3 += 256;
						}
						int ip4 = values[index++];
						if (ip4 < 0) {
							ip4 += 256;
						}

						int mask1 = values[index++];
						if (mask1 < 0) {
							mask1 += 256;
						}
						int mask2 = values[index++];
						if (mask2 < 0) {
							mask2 += 256;
						}
						int mask3 = values[index++];
						if (mask3 < 0) {
							mask3 += 256;
						}
						int mask4 = values[index++];
						if (mask4 < 0) {
							mask4 += 256;
						}

						int gate1 = values[index++];
						if (gate1 < 0) {
							gate1 += 256;
						}
						int gate2 = values[index++];
						if (gate2 < 0) {
							gate2 += 256;
						}
						int gate3 = values[index++];
						if (gate3 < 0) {
							gate3 += 256;
						}
						int gate4 = values[index++];
						if (gate4 < 0) {
							gate4 += 256;
						}

						String ip = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
						String communicationIP= ip;
						String mac = mac1 + "." + mac2 + "." + mac3 + "." + mac4 + "." + mac5 + "." + mac6;
						String mask = mask1 + "." + mask2 + "." + mask3 + "." + mask4;
						String gate = gate1 + "." + gate2 + "." + gate3 + "." + gate4;

	
						List<Map<String, Object>> params = new ArrayList<Map<String, Object>>();
						Map<String, Object> param = new HashMap<String, Object>();
						List<NatInfo> natPorts=null;
						String productName = null;
						
						boolean isNat =false;
						String[] natIpAr =  ipAddr.split("/");
						com.nm.nmm.res.Address addr = this.getAddressByIp(natIpAr[0]);
						ManagedElement natNe = topoManager.getByAddress(addr);
						if(natNe!=null){
							NeDescriptor descriptor =NeDescriptionLoader.getNeDescriptor(natNe.getProductName(), natNe.getVersion());
							if(descriptor!=null){
								FunctionDescriptor func = descriptor.getFuncDesByName("dhcpNatMapping");
								if(func!=null){
									List<AttributeDescriptor> attrList = this.getAttribute(func);
									String ipAndPort = natIpAr[0] + "/" + 161;
									SnmpDataAccess dataAccess = SnmpDataAccessPool
											.getInstance().getSnmpDataAccess(
													ipAndPort, "public",
													"private", 100);
									Map<String ,Object> ipMa = new HashMap<String, Object>();
									ipMa.put("targetDevIp", ip);
									List<Object> indexList = new ArrayList<Object>();
									indexList.add(1);
									dataAccess.update(func, ipMa, indexList);
									List<Map<String,Object>> resultList = dataAccess.getDatas(attrList, null);
									natPorts = this.getNatPort(resultList, ip);
									
									int trapPort = Integer.valueOf(natIpAr[1]);
									int setPort = trapPort-1;
									if(natPorts!=null&&!natPorts.isEmpty()){
										for(NatInfo natInfo:natPorts){
											if(natInfo.getSrcPort()==161){
												setPort = natInfo.getDstPort();
											}
										}
									}
									communicationIP=natIpAr[0] + "/" + setPort;
									isNat=true;
									
								}
								
							}
						}
						
						
						if(seriesId == 0 && modelId == 0){
						
							productName = getNeType(communicationIP,isNat);
						}else{
							productName = NeDescriptionLoader.getNeProductName(seriesId, modelId, 0);
						}
						
						if (productName == null) {
							if (seriesId == 41) {
								productName = NeDescriptionLoader.getNeProductName(seriesId, 211, 0);
							}
						}

						if (productName == null) {
							log.error("dhcp unknown ne type:" + seriesId + "," + modelId);
							continue;
						}
						log.info("dhcp discovery: " + productName );
						String version = NeDescriptionLoader.getNeHighestVersion(productName);
//						NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(productName, version);
						String[] ip_port = ipAddr.split("/");
						String ipAr = ip_port[0];
						com.nm.nmm.res.Address neAddr = this.getAddressByIp(ipAr);
						ManagedElement ne = topoManager.getByAddress(neAddr);
					
						
						param.put("seriesId", seriesId);
						param.put("modelId", modelId);
						param.put("productName", productName);
						param.put("version", version);
						param.put("ip", ip);
						param.put("mac", mac);
						param.put("mask", mask);
						param.put("gate", gate);
						if(natPorts!=null){
							param.put("isNatEnable", true);
						    param.put("natIP", ne.getAddress().getIpAddressStr());
						    param.put("natPorts", natPorts);
						}
						params.add(param);
						
						int natPort161 = 161;
						if(natPorts!=null){
							for(NatInfo natPort:natPorts){
								log.info("src port:"+natPort.getSrcPort()+" dst port:"+natPort.getDstPort());
								if(natPort.getSrcPort()==161){
									if(natPort.getDstPort()>0){
										natPort161 = natPort.getDstPort();
									}
								}
								
							}
							
						}else{
							log.info("natPorts is null!");
							
						}
						
						log.info("natPort161 is "+natPort161);
						ackDiscoveryTrap(ipAddr,natPort161);

						SnmpNewMessage msg = new SnmpNewMessage();
						String cmdId = "neBasicInfoAlmInfo";
						msg.setCommandId(cmdId);
						msg.setMsgType(HiMsgType.HTM_NONETRAP);
						msg.setAddress(ip);
						msg.setValues(params);
						msg.setNeType(productName);
						msg.setNeVersion(version);
						snmpMsgPool.insertTrapMessage(msg);
						
//						if (neDes.isSupportPlatform()) {
//							SnmpNewMessage msg = new SnmpNewMessage();
//							String cmdId = "neBasicInfoAlmInfo";
//							msg.setCommandId(cmdId);
//							msg.setMsgType(HiMsgType.HTM_NONETRAP);
//							msg.setAddress(ip);
//							msg.setValues(params);
//							msg.setNeType(productName);
//							msg.setNeVersion(version);
//							snmpMsgPool.insertTrapMessage(msg);
//						} else {
//							SnmpMessage msg = new SnmpMessage() {
//								public String getCommandId() {
//									return "neBasicInfoAlmInfo_old";
//								}
//							};
//
//							msg.setMsgType(HiMsgType.HTM_NONETRAP);
//							msg.setAddress(ip);
//							// msg.setValues(params);
//							msg.setPdu(pdu);
//							msg.setNeType(productName);
//							msg.setNeVersion(version);
//							snmpMsgPool.insertTrapMessage(msg);
//						}
						break;
					}
				}
			}
		}catch(Exception ex){
			log.error(ex, ex);
		}
	}

	private void ackDiscoveryTrap(String ip,int natPort161 ) {
		SnmpDataAccess snmpAccess = null;
		try{
			String[] ipAr =  ip.split("/");
			String ip_port = ipAr[0] + "/" + natPort161;
			snmpAccess = SnmpDataAccessPool.getInstance().getSnmpDataAccess(ip_port, "public", "private", 20);
			snmpAccess.ackNeDiscoveryTrap();
		}catch(Exception ex){
			log.error(ex, ex);
		}finally{
			snmpAccess.close();
			SnmpDataAccessPool.getInstance().releaseSnmpDataAccess(snmpAccess);
		}
	}
	
	private String getNeType(String ip,boolean isNat){
		SnmpDataAccess snmpAccess = null;
		try{
			String ip_port =null;
			if(isNat){
				ip_port = ip;
			}else{
				String[] ipAr =  ip.split("/");
				ip_port = ipAr[0] + "/" + 161;
			}
			snmpAccess = SnmpDataAccessPool.getInstance().getSnmpDataAccess(ip_port, "public", "private", 20);
			String objId = snmpAccess.getObjId();
			if(objId != null){
				return NeDescriptionLoader.getNeProductName(objId);
			}
		}catch(Exception ex){
			log.error(ex, ex);
		}finally{
			snmpAccess.close();
			SnmpDataAccessPool.getInstance().releaseSnmpDataAccess(snmpAccess);
		}
		return null;
	}
	

	
	protected String getPX10NMXEIP(String address) {
		try {
			if (PX10SAVEDIPsMap.isEmpty()) {
				String PX10SAVEDIP = ToolUtil.getProperty("PX10SAVEDIP");
				String[] PX10SAVEDIPs = PX10SAVEDIP.split(";");

				for (String s : PX10SAVEDIPs) {
					String[] ss = s.split("@");
					//String[] ss2 = ss[0].split(":");
					PX10SAVEDIPsMap.put(ss[1], ss[0]);
				}
			}
			return PX10SAVEDIPsMap.get(address);
		} catch (Exception ex) {
			log.info("getPX10IP exception!!");
		}
		return null;
	}
	
	private ManagedElement getMangedElement(long ip,int port) {
		if(neMap != null){
			synchronized(neMap) {
				Iterator<Integer> it = neMap.keySet().iterator();
				while(it.hasNext()) {
					ManagedElement me = neMap.get(it.next());
					if(me.getAddress().getIpAddress() == ip
							||me.getAddress().getOutBandIpAddress() == ip||(me.getNatIpEx().equals(ToolUtil.longToIp(ip)) && me.isNatPortExist(port))) {
						return me;
					}
				}
			}
		}else{
			com.nm.nmm.res.Address address = new com.nm.nmm.res.Address();
			address.setAddressType(AddressType.AT_IPV4);
			address.setIpAddress(ip);
			return topoManager.getByAddress(address);
		}
		
		return null;
	}
	private com.nm.nmm.res.Address getAddressByIp(String ip){
		com.nm.nmm.res.Address addr = new com.nm.nmm.res.Address();
		addr.setAddressType(AddressType.AT_IPV4);
		addr.setIpAddress(ToolUtil.ipToLong(ip));
		return addr;		
	}
		
	private List<AttributeDescriptor> getAttribute(FunctionDescriptor fd){
		List<AttributeDescriptor> attrList =new ArrayList<AttributeDescriptor>();
		if(fd==null){
			return  attrList;
		}
		for(AttributeDescriptor attr:fd.getAttributeList()){
			//if("natRuleOrder".equals(attr.getName())||"natConfInterfaceID".equals(attr.getName())||"privateIp".equals(attr.getName())||"innerPort".equals(attr.getName())||"publicPort".equals(attr.getName()))
			attrList.add(attr);
				
		}
		return attrList;
	} 
	private List<NatInfo> getNatPort(List<Map<String, Object>> resultList,String ip){
		List<NatInfo> natPorts = new ArrayList<NatInfo>();
		List<Integer> portList = new ArrayList<Integer>();
		portList.add(21);
		portList.add(22);
		portList.add(23);
		portList.add(161);
		portList.add(162);
		portList.add(2650);
		portList.add(3000);
		portList.add(12305);
		
		if (null != resultList && !resultList.isEmpty()) {
		
		for(Map<String ,Object> data:resultList){
			IpAddress ipAddr =null;
			if (data.containsKey("targetDevIp")) {
				ipAddr = (IpAddress) data.get("targetDevIp");
				if(!ipAddr.toString().equals(ip)){
					continue;
				}
			}
			
			for(int port:portList){
			
				String natPortKey = "port";
				String natRowKey = "indexInNatRule";
	
				if (data.containsKey(natPortKey+port)&&data.containsKey(natRowKey+port)) {
					NatInfo natInfo = new NatInfo();
					natInfo.setSrcPort(port);
					natInfo.setDstPort(Integer.valueOf(data.get(natPortKey+port).toString()));
					natInfo.setNatRuleOrder(Integer.valueOf(data.get(natRowKey+port).toString()));
					natPorts.add(natInfo);
				}
				
				
				}
			}
		}
		if(natPorts.isEmpty()){
			return null;
		}
		else {
			return natPorts;
		}
	}
	
	private ManagedElement getNatNe(long ip,int port) {
		if(neMap != null){
			synchronized(neMap) {
				Iterator<Integer> it = neMap.keySet().iterator();
				while(it.hasNext()) {
					ManagedElement me = neMap.get(it.next());
					if(me.getNatIpEx().equals(ToolUtil.longToIp(ip)) && me.isNatPortExist(port)) {
						return me;
					}
				}
			}
		}
		
		return null;
	}

		
	}
	

