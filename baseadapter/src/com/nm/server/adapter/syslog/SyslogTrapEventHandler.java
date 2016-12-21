package com.nm.server.adapter.syslog;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;
import org.productivity.java.syslog4j.server.SyslogServerSessionEventHandlerIF;
import org.productivity.java.syslog4j.util.SyslogUtility;

import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.nmm.res.Address;
import com.nm.nmm.res.Address.AddressType;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.comm.MessagePool;

public class SyslogTrapEventHandler implements	SyslogServerSessionEventHandlerIF {

	private static final long serialVersionUID = 1L;
	protected Log log = LogFactory.getLog(SyslogTrapEventHandler.class);
	
	private MessagePool msgPool; // 消息池，处理完的消息放到池里返回

	private TopoManager topoManager;
	
	public MessagePool getMsgPool() {
		return msgPool;
	}

	public void setMsgPool(MessagePool msgPool) {
		this.msgPool = msgPool;
	}
	
	public void setTopoManager(TopoManager topoManager){
		this.topoManager = topoManager;
	}

	@Override
	public void event(Object session, SyslogServerIF syslogServer,
			SocketAddress socketAddress, SyslogServerEventIF event) {
		// TODO Auto-generated method stub
		try{
			String date = (event.getDate() == null ? new Date() : event.getDate()).toString();
			String facility = SyslogUtility.getFacilityString(event.getFacility());
			String level = SyslogUtility.getLevelString(event.getLevel());
			
			//只解析notice：
			if(SyslogConstants.LEVEL_NOTICE != event.getLevel()){
				return;
			}
			
			String message = event.getMessage();
					
			System.out.println("--------------------------------------");		
			System.out.println("recv syslog: " + message);
			System.out.println("socketAddress: " + socketAddress);
			System.out.println("date: " + date);
			
			String ip = socketAddress.toString();
			int i = ip.indexOf("/");
			int j = ip.indexOf(":");
			ip = ip.substring(i + 1, j);
			
			
			//解析告警
//			int index = message.indexOf("%% ");
//			if(index < 0){
//				return;
//			}

			Address addr = new Address(ip);
			addr.setAddressType(AddressType.AT_IPV4);
			ManagedElement me = topoManager.getByAddress(addr);
			
			if(me == null){
				log.error("me==null : " +  ip);
				return;
			}
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			if(neDes == null){
				log.error("neDes==null : " +  me.getProductName() + me.getVersion());
				return;
			}
			
			List<Map<String, Object>> alarms = new ArrayList<Map<String, Object>>();
			
			Map<String, Object> alarmMap = AlarmParser.getInstance().parse(neDes, me, message);
            
			alarms.add(alarmMap);

			if (!alarms.isEmpty()) {
				Boolean occur = (Boolean)alarmMap.get("occur");
				SnmpNewMessage msg = new SnmpNewMessage();
				String cmdId = occur ? "alarmProductTrap" : "alarmDisappearTrap";
				msg.setCommandId(cmdId);
				msg.setAddress(ip);
				msg.setValues(alarms);
//				if(isPX10){
//					msg.setNeType("NE403");
//					msg.setNeVersion("1.0");
//				}
				msgPool.insertTrapMessage(msg);
			}
		}catch(Exception ex){
			log.error(ex, ex);
		}
	}

	@Override
	public void exception(Object session, SyslogServerIF syslogServer,
			SocketAddress socketAddress, Exception exception) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sessionClosed(Object session, SyslogServerIF syslogServer,
			SocketAddress socketAddress, boolean timeout) {
		// TODO Auto-generated method stub

	}

	@Override
	public Object sessionOpened(SyslogServerIF syslogServer,
			SocketAddress socketAddress) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void destroy(SyslogServerIF syslogServer) {
		// TODO Auto-generated method stub

	}

	@Override
	public void initialize(SyslogServerIF syslogServer) {
		// TODO Auto-generated method stub

	}

}
