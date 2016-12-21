package com.nm.server.adapter.base.sm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import snmp.SnmpFactory;
import snmp.trap.SnmpDataTrap;

import com.nm.base.util.DefaultThreadFactory;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.nmm.mstp.config.Trap;
import com.nm.nmm.res.Address.AddressType;
import com.nm.nmm.res.EthGatewayInfo;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.SnmpVersion;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.AsynTaskList;
import com.nm.server.adapter.base.comm.SnmpMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.comm.messagesvr.MessageTask;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;
import com.nm.server.config.trap.TrapManager;

public class WriteSessionTaskSnmp extends MessageTask {

	private static final Log log = LogFactory.getLog(WriteSessionTaskSnmp.class);
	private static TopoManager topoManager;
	private static TrapManager trapManager;
	private static MessagePool snmpMsgPool;
	private static int msgSequenceNumber = 0;
//	public static Snmp snmp = null;
	public static List<Snmp> trapSnmpList = new ArrayList<Snmp>();
	private static int frameOn;
	private static ExecutorService executor = null;
	private static Map<Integer, Boolean> trapMap = new HashMap<Integer, Boolean>();
	private static Map<Integer,ManagedElement> neMap;
	private static String recordFileName = null;

	public WriteSessionTaskSnmp(MessagePool msgPool, Object o) {
		super(msgPool, o);
	}

	// 初始化snmp通信参数
	public static synchronized Snmp initSnmpComm() throws IOException {
		TransportMapping transport = new DefaultUdpTransportMapping(new UdpAddress(), true);
		Snmp snmp = new Snmp(transport);
		transport.listen();
		return snmp;

	}

	// 初始化trap通信参数
	public static void initSnmpTrap(final TopoManager topoMng, final TrapManager trapMng, 
			final MessagePool pool, Map<Integer, ManagedElement> neMap) throws IOException {

		if (topoMng == null) {
			return;
		}
		if (trapMng == null) {
			return;
		}
		topoManager = topoMng;
		trapManager = trapMng;
		snmpMsgPool = pool;

		List<Trap> trapList = trapManager.getAllNeTrap();

		if (trapList == null || trapList.size() == 0) {
			return;
		}

		if (executor == null) {
			executor = Executors.newCachedThreadPool(new DefaultThreadFactory("WriteSessionTaskSnmp-initSnmpTrap"));
		}

		for (Trap t : trapList) {
			final int portNo = t.getPortNum();
			if (portNo <= 0 || portNo > 65535) {
				continue;
			}
			Boolean b = trapMap.get(portNo);
			if (b != null && b == Boolean.TRUE) {
				continue;
			}
			trapMap.put(portNo, Boolean.TRUE);

			try {
				TransportMapping transport = null;
				Snmp trap = SnmpDataTrap.trapSnmpMap.get(portNo);
				if (trap == null) {
					UdpAddress udpAddr = new UdpAddress();
					udpAddr.setPort(portNo);
					transport = new DefaultUdpTransportMapping(udpAddr);
					trap = new Snmp(transport);
					SnmpDataTrap.trapSnmpMap.put(portNo, trap);
				}

				trapSnmpList.add(trap);
				CommandResponder trapListener = new CommandResponder() {
					public void processPdu(CommandResponderEvent e) {
						
						final CommandResponderEvent cre = e;
						Runnable trapThread = new Runnable() {
							public void run() {
								processTrap(cre, portNo);
							}
						};
						executor.execute(trapThread);
					}
				};
				trap.addCommandResponder(trapListener);
				if (transport != null && !transport.isListening()) {
					transport.listen();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.error("init snmp trap error!!", e);
			}
		}
	}
	
	public static void processTrap(CommandResponderEvent e, Integer portNo) {
		PDU pdu = e.getPDU();
		if (pdu != null) {
			log.info(pdu.toString());

			Address addr = e.getPeerAddress();
			String strAddr = addr.toString();

			int index = strAddr.lastIndexOf('/');
			com.nm.nmm.res.Address address = new com.nm.nmm.res.Address();
			address.setAddressType(AddressType.AT_IPV4);
			address.setIpAddress(ToolUtil.ipToLong(strAddr.substring(0, index)));
			ManagedElement me = (ManagedElement) topoManager.getByAddress(address);
			NeDescriptor neDes =null;
			FunctionDescriptor fd =null;
			//找到的网元可能是NMNAT,需要再次判断
			if(me!=null){
				neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
				if(neDes!=null){
				 fd = neDes.getFuncDesByName("nmNatMapping");
				}
			}

			if (me == null||(fd!=null&&SnmpDataTrap.getInstance().isDiscoveryTrap(e.getPDU()))) {// 可能是dhcp discovery
				SnmpDataTrap.getInstance().processTrap(e, portNo);
				return;
			}
			neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			if (neDes.isSupportPlatform()) {// snmp
											// platform
				SnmpDataTrap.getInstance().processTrap(e, portNo);
				return;
			}

			SnmpMessage msg = new SnmpMessage();
			msg.setPdu(pdu);

			msg.setAddress(addr.toString());
			msg.setNeType(me.getProductName());
			snmpMsgPool.insertTrapMessage(msg);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Trap", addr.toString() + "\n\n" + sb.toString());
			}
		}
	}

	public static void restartSnmpTrap() {
		try {
			initSnmpTrap(topoManager, trapManager, snmpMsgPool, neMap);
		} catch (IOException e) {
			log.error(e, e);
		}
	}

	@SuppressWarnings("static-access")
	public void setTopoManager(TopoManager topoManager) {
		this.topoManager = topoManager;
	}

	public TopoManager getTopoManager() {
		return topoManager;
	}

	public void setNeMap(Map<Integer,ManagedElement> neMap) {
		this.neMap = neMap;
	}

	public Map<Integer,ManagedElement> getNeMap() {
		return neMap;
	}

	public void run() {
		try {

			final AdapterMessagePair mp = (AdapterMessagePair) o;
			if(mp.getScheduleType() == ScheduleType.ST_NEPOLL){
				mp.setBeginTime();
			}
			// mp.setScheduleType(ScheduleType.ST_NEPOLL);
			// if(mp.getScheduleType() == ScheduleType.ST_NEPOLL){
			//
			// }
			synchronized(mp){
				if(!mp.isSendedMsgPair()){
					msgPool.addSendedMsg(mp);
					mp.setSendedMsgPair(true);
				}
			}
			final ManagedElement me = mp.getManagedElement();

			// must put into pool firstly

			if (mp.getScheduleType() == ScheduleType.ST_NA) {
				frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
			} else {
				frameOn = ToolUtil.getEntryConfig().getPollFrameRecordOnOff();
			}
			recordFileName = this.getRecordFileName(mp);
			
			AsynTaskList asynTaskList = mp.getAsynTaskList();

			for (AsynTask asynTask : asynTaskList.getTaskList()) {
				synchronized (asynTask) {
					for (final TaskCell taskCell : asynTask.getTaskCellList()) {
						if(taskCell.getProtocolType() != null
								&& !CommProtocol.CP_SNMP.equals(taskCell.getProtocolType())
								&& !CommProtocol.CP_SNMP_I.equals(taskCell.getProtocolType())){
							continue;
						}
						synchronized (taskCell.getLockRawMsg()) {

							List<HiSysMessage> msgList = taskCell.getMsgList();

							Iterator<HiSysMessage> msgIt = msgList.iterator();

							while (msgIt.hasNext()) {
								final SnmpMessage msg = (SnmpMessage) msgIt.next();
								final PDU pdu = msg.getPdu();

								if (pdu.size() == 0) {
									msgIt.remove();
									continue;
								}

								msg.setTimeThreshold(21000);
								taskCell.addSendMsg(pdu.get(0).getOid().toString(), msg);
								// log.info("Send command "+pdu.get(0).getOid().toString());

								
								try {
									if (me != null) {
										Runnable r = new Runnable() {
											public void run() {
												ResponseEvent respEvnt = syncSendSnmp(me, pdu);
												if (respEvnt != null && respEvnt.getResponse() != null) {
													PDU resPdu = respEvnt.getResponse();
												
													if(!msg.getCommandId().contains("neAutoDiscovery") 
															&& !msg.getCommandId().contains("alarmRecordSync")
															&& resPdu.getErrorStatus() != 0){
														mp.setErrCode(resPdu.getErrorStatus() + 50000);
													}
													
													SnmpMessage resMsg = new SnmpMessage();
													resMsg.setPdu(resPdu);
													// log.info("Receive response "+
													// pdu.get(0).getOid().toString());
													taskCell.addResMsg(resPdu.get(0).getOid().toString(), resMsg);

												}
											}
										};
										SnmpFactory.getInstance().getThreadPool().execute(r);
										
//										asyncSendSnmp(me, pdu, listener)
									} else {
										ResponseListener listener = new ResponseListener() {

											@Override
											public void onResponse(ResponseEvent respEvnt) {
												// TODO Auto-generated method stub
												if (respEvnt != null && respEvnt.getResponse() != null) {
													PDU pdu = respEvnt.getResponse();
													if(!msg.getCommandId().contains("neAutoDiscovery") 
														&& !msg.getCommandId().contains("alarmRecordSync")
														&& pdu.getErrorStatus() != 0){
														mp.setErrCode(pdu.getErrorStatus() + 50000);
													}
													SnmpMessage resMsg = new SnmpMessage();
													resMsg.setPdu(pdu);
													// log.info("Receive response "+
													// pdu.get(0).getOid().toString());
													taskCell.addResMsg(pdu.get(0).getOid().toString(), resMsg);

													//
													if (frameOn == 1) {
														String[] ss = pdu.toString().split(";");
														StringBuilder sb = new StringBuilder();
														for (String s : ss) {
															sb.append(s);
															sb.append("\n");
														}
														FileTool.write("FrameInfo", recordFileName + "Response", me.getAddress().getIpAddressStr() + "\n\n" + sb.toString());
													}
												}
												((Snmp) respEvnt.getSource()).cancel(respEvnt.getRequest(), this);
											}
										};
										asyncSendSnmp(me, 161, SnmpConstants.version2c, "public", "private", pdu, listener);
									}
									Thread.sleep(20);
								} catch (Exception e) {
									// reportNoResAlarm(me,
									// ErrorMessageTable.NO_RESPONSE);//
//									log.error("snmp.send() error!!", e);
//									initSnmpComm();
								}
								msg.setBeginTime(new Date());

								msgIt.remove();

							}
						}
					}
				}

			}
			mp.setIsSend(true);
		} catch (Exception e) {
			log.error("send snmp exception:", e);
		}

		if (this.o != null) {
			o = null;
		}
	}

	public static ResponseEvent syncSendSnmp(ManagedElement me, PDU pdu) {
		Snmp snmp = null;
		try {
			log.info("syncSendSnmp...");
			snmp = initSnmpComm();
			String destIP = me.getNatIpEx();
			EthGatewayInfo gwInfo = me.getEthGatewayInfo();
			int  targetPort = me.getAddress().getMonitorServicePort();
			if(me.getAddress() != null && me.getAddress().getIpAddress() == -1
					&& !me.isServeAsEthGateway() && gwInfo != null){
				ManagedElement gwMe = neMap.get(me.getEthGatewayInfo().getWorkGatewayNeId());
				if(gwMe != null){
					destIP = gwMe.getNatIpEx();
					targetPort = gwMe.getAddress().getMonitorServicePort();
				}
			}
			
			/**
			 * 给H0FL-P带的远端小盒用，因为远端小盒的局端在topo上是可以变化的，
			 * 变化时未处理端口，且H0FL-P带的远端小盒固定是SNMP协议，端口固定161
			 */
			if(me.isRemote()){
				targetPort = 161;
			}
			targetPort = me.getNatPort(targetPort);
			if("NE28".equals(me.getProductName())){
				targetPort = 16101;
				
			}
			Address targetAddress = GenericAddress.parse("udp:" + destIP + "/" + targetPort);
			if(targetAddress == null){
				log.error("targetAddress == null---->" + destIP + ":" + targetPort);
				return null;
			}
			// 初始化agent目标参数
			CommunityTarget target = new CommunityTarget();

			// 通信不成功时的重试次数
			target.setRetries(0);
			// 超时时间
			target.setTimeout(10000);
			int version = SnmpConstants.version2c;
			SnmpVersion sv = me.getSnmpVersion();
			switch (sv) {
			case SV_V1:
				version = SnmpConstants.version1;
				break;
			case SV_V2:
				version = SnmpConstants.version2c;
				break;
			case SV_V3:
				version = SnmpConstants.version3;
				break;
			}
			target.setVersion(version);
			target.setAddress(targetAddress);
			if (pdu.getType() == PDU.GET || pdu.getType() == PDU.GETBULK || pdu.getType() == PDU.GETNEXT) {
				if (me.getReadCommunity() == null || me.getReadCommunity().isEmpty()) {
					target.setCommunity(new OctetString("public"));
				} else {
					target.setCommunity(new OctetString(me.getReadCommunity()));
				}
			} else if (pdu.getType() == PDU.SET) {
				if (me.getWriteCommunity() == null || me.getWriteCommunity().isEmpty()) {
					target.setCommunity(new OctetString("private"));
				} else {
					target.setCommunity(new OctetString(me.getWriteCommunity()));
				}
			}
			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", recordFileName + "Command", me.getAddress().getIpAddressStr() + "\n\n" + sb.toString());
			}

			log.info("syncSendSnmp...send...");
			ResponseEvent respEvnt = snmp.send(pdu, target);
			if (respEvnt != null && respEvnt.getResponse() != null) {
				log.info("syncSendSnmp...response...");
				pdu = respEvnt.getResponse();

				if (frameOn == 1) {
					String[] ss = pdu.toString().split(";");
					StringBuilder sb = new StringBuilder();
					for (String s : ss) {
						sb.append(s);
						sb.append("\n");
					}
					FileTool.write("FrameInfo", recordFileName + "Response", me.getAddress().getIpAddressStr() + "\n\n" + sb.toString());
				}
			}else{
				log.error("syncSendSnmp...ResponseEvent == null!!!");
			}
			return respEvnt;
		} catch (Exception e) {
			log.error(e, e);
		}finally{
			try {
				if(snmp != null){
					snmp.close();
					snmp = null;
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				log.error(e1, e1);
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static void asyncSendSnmp(ManagedElement me, int port, int version, String readCommunity, String writeCommunity, PDU pdu, ResponseListener listener) {
		Snmp snmp = null;
		try {
			snmp = initSnmpComm();
			String ip = me.getNatIpEx();
			EthGatewayInfo gwInfo = me.getEthGatewayInfo();
			if(me.getAddress() != null && me.getAddress().getIpAddress() == -1
					&& !me.isServeAsEthGateway() && gwInfo != null){
				ManagedElement gwMe = neMap.get(me.getEthGatewayInfo().getWorkGatewayNeId());
				if(gwMe != null){
					ip = gwMe.getNatIpEx();
				}
			}
			Address targetAddress = GenericAddress.parse("udp:" + ip + "/" + me.getNatPort(port));
			// 初始化agent目标参数
			CommunityTarget target = new CommunityTarget();

			// 通信不成功时的重试次数
			target.setRetries(0);
			// 超时时间
			target.setTimeout(10000);

			target.setVersion(version);
			target.setAddress(targetAddress);
			if (pdu.getType() == PDU.GET || pdu.getType() == PDU.GETBULK || pdu.getType() == PDU.GETNEXT) {
				if (readCommunity == null) {
					readCommunity = "public";
				}
				target.setCommunity(new OctetString(readCommunity));
			} else if (pdu.getType() == PDU.SET) {
				if (writeCommunity == null) {
					writeCommunity = "private";
				}
				target.setCommunity(new OctetString(writeCommunity));
			}
			Thread.sleep(5);
			if (msgSequenceNumber++ > Integer.MAX_VALUE) {
				msgSequenceNumber = 0;
			}
			pdu.setRequestID(new Integer32(msgSequenceNumber));
			try {
				snmp.send(pdu, target, null, listener);
			} catch (MessageException ex) {
				log.info(ex, ex);
				if (snmp != null) {
					try {
						snmp.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						log.error(e1, e1);
					}
					snmp = null;
				}
			}

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", recordFileName + "Command", target.toString() + "\n\n" + sb.toString());
			}

		} catch (Exception e) {
			log.error(e, e);
		} finally{
			try {
				if(snmp != null){
					snmp.close();
					snmp = null;
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				log.error(e1, e1);
			}
		}
	}

	public static void asyncSendSnmp(ManagedElement me, PDU pdu, ResponseListener listener) {
		int version = SnmpConstants.version2c;
		SnmpVersion sv = me.getSnmpVersion();
		switch (sv) {
		case SV_V1:
			version = SnmpConstants.version1;
			break;
		case SV_V2:
			version = SnmpConstants.version2c;
			break;
		case SV_V3:
			version = SnmpConstants.version3;
			break;
		}
		// String ip = ToolUtil.longToIp(me.getAddress().getIpAddress());
		int port = me.getAddress().getMonitorServicePort();
		if(me.isRemote()){
			port = 161;
		}
		asyncSendSnmp(me, port, version, me.getReadCommunity(), me.getWriteCommunity(), pdu, listener);
	}

	// private static void reportNoResAlarm(ManagedElement me, int emt){
	// //通知上报失连告警
	// // log.info("通知上报失连告警:" + emt);
	// // Response res = new Response();
	// // res.setcmdId("neConnStateFunc");
	// // CommandDestination cd = new CommandDestination();
	// // cd.setNeId(me.getId());
	// // res.setDest(cd);
	// // res.setResult(emt);
	// //// res.addParam(new CommandParameter(event));
	// // ResponseCollection rc = new ResponseCollection();
	// // rc.addResponse(res);
	// // SyncNotifyManager.getInstance().notifyToServer(rc);
	// }
	
	protected String getRecordFileName(AdapterMessagePair mp) {
		
		if(mp.getScheduleType() == ScheduleType.ST_NA){
			return "";
		}else if(mp.getScheduleType() == ScheduleType.ST_NEPOLL
				|| mp.getScheduleType() == ScheduleType.ST_GWPOLL){
			return "Poll";
		}else if(mp.getScheduleType() == ScheduleType.ST_ALARMPOLL
				|| mp.getScheduleType() == ScheduleType.ST_HISALARMPOLL){
			return "AlarmPoll";
		}else{
			return "";
		}
	}
}