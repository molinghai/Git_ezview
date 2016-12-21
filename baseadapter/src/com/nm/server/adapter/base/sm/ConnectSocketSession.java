package com.nm.server.adapter.base.sm;



import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.base.util.DefaultThreadFactory;
import com.nm.nmm.res.EthGate;

public class ConnectSocketSession extends Thread {
	protected static final Log log = LogFactory.getLog(ConnectSocketSession.class);
	protected ThreadPoolExecutor threadPool = new ThreadPoolExecutor(100, 200, 60, 
			TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1), 
			new ThreadPoolExecutor.CallerRunsPolicy());
	protected BlockingQueue<EthGate> gwQueue = new LinkedBlockingQueue<EthGate>();
		
	private SocketMgrServer sm;	
	private boolean run = true;
	
	public ConnectSocketSession(SocketMgrServer sm) {
		this.sm = sm;
	}
	
	synchronized public void setRun(boolean run) {
		this.run = run;		
	}

	synchronized public boolean isRun() {		
		return run;
	}
	
	public void setSm(SocketMgrServer sm) {
		this.sm = sm;
	}

	public SocketMgrServer getSm() {
		return sm;
	}
	
	public void addGw(EthGate gw) {
		try {
			this.gwQueue.put(gw);
		} catch (InterruptedException e) {
			log.error("ConnectSocketSession addGw exception:", e);
		}
	}

	public void run() {
		
		try {
			threadPool.setThreadFactory(new DefaultThreadFactory(this.getName()));
			while(this.isRun())	{
				
				EthGate gw = gwQueue.take();

				ConnectSocketTask task = new ConnectSocketTask(sm,gw);
				
				threadPool.execute(task);
			}
			
			
		} catch (Exception e) {
			log.error("ConnectSocketSession run exception:", e);
		}

	}
	
	synchronized public void stopThread()
	{
		this.notifyAll();
		
		this.setRun(false);
		
	}
}
