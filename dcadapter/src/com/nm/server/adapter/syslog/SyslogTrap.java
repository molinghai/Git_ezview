package com.nm.server.adapter.syslog;


import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerConfigIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;

import com.nm.nmm.service.TopoManager;
import com.nm.server.comm.MessagePool;

public class SyslogTrap extends Thread{

	SyslogTrapEventHandler eventHandler = new SyslogTrapEventHandler();


	public void setMsgPool(MessagePool msgPool) {
		eventHandler.setMsgPool(msgPool);
	}
	public void setTopoManager(TopoManager topoManager){
		eventHandler.setTopoManager(topoManager);
	}
	
	public void run(){
		SyslogServerIF syslogServer = SyslogServer.getInstance("udp");
		SyslogServerConfigIF config = syslogServer.getConfig();
//		config.setHost("192.168.1.114");
//		config.setPort(1514);
		config.addEventHandler(eventHandler);
		syslogServer.initialize("udp",config);
		syslogServer.run();
		System.out.println("syslogServer running...");
	}
}
