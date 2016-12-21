package com.nm.server.adapter.snmp;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.nm.base.util.DefaultThreadFactory;
import com.nm.server.common.util.ToolUtil;

public class SnmpFactory {
	
	private static SnmpFactory instance = new SnmpFactory();
	public static SnmpFactory getInstance() {
        return instance;
    }
	private Snmp snmp = null;
	private TransportMapping transport = null;
	private ThreadPoolExecutor threadPool = null;
	public  Snmp getSnmp(){
		if(snmp == null){
			try {
				transport = new DefaultUdpTransportMapping(new UdpAddress(), true);
				transport.listen();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			snmp = new Snmp(transport);
		}
		return snmp;
	}
	
	public ThreadPoolExecutor getThreadPool() {
		int count = ToolUtil.getEntryConfig().getPingConcurrentNumSnmp();
		if(count < 30){
			count = 30;
		}
		if(threadPool == null || threadPool.isShutdown()){
			threadPool = new ThreadPoolExecutor(5, count, 30, 
					TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1), 
					new ThreadPoolExecutor.CallerRunsPolicy());	
			threadPool.setThreadFactory(new DefaultThreadFactory("SnmpFactory"));
		}
		return threadPool;
	}	
	
	public  Snmp getNewSnmp(){
		try {
			TransportMapping port = new DefaultUdpTransportMapping(
					new UdpAddress(), true);
			port.listen();
			Snmp snmp = new Snmp(port);
			return snmp;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return getSnmp();
	}
}
