package com.nm.server.adapter.snmp.pool;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.nm.server.common.util.ToolUtil;

public class SnmpPool {
	
	private static final Log log = LogFactory.getLog(SnmpPool.class);
	private int coreNum = 1; // 对象池的大小
	private int maxNum = 5; // 对象池最大的大小
	private Vector<PooledSnmp> snmpPool = null; // 存放对象池中对象的向量

	public SnmpPool() {
	}

	/*** 创建一个对象池 ***/
	public synchronized void createPool(){     
		
        // 确保对象池没有创建。如果创建了，保存对象的向量 objects 不会为空     
        if (snmpPool != null) {     
            return; // 如果己经创建，则返回     
        }     
    
        // 创建保存对象的向量 , 初始时有 0 个元素     
        snmpPool = new Vector<PooledSnmp>();     
    
        for (int x = 0; x < coreNum; x++) {     
            if ((snmpPool.size() == 0) && this.snmpPool.size() <this.maxNum) {  
               createPooledSnmp();
            }
        }
    }
	public synchronized Snmp getSnmp() {
		// 确保对象池己被创建
		if (snmpPool == null) {
			return null; // 对象池还没创建，则返回 null
		}
		maxNum = Integer.valueOf(ToolUtil.getProperty("PING_INTERVAL_SNMP"));
		Snmp conn = getFreeSnmp(); // 获得一个可用的对象

		// 如果目前没有可以使用的对象，即所有的对象都在使用中
		while (conn == null) {
			wait(250);
			conn = getFreeSnmp(); // 重新再试，直到获得可用的对象，如果
			// getFreeObject() 返回的为 null，则表明创建一批对象后也不可获得可用对象
		}

		return conn;// 返回获得的可用的对象
	}

	/**
	 * 本函数从对象池对象 snmpPool 中返回一个可用的的对象，
	 * 如果 当前没有可用的对象，则创建几个对象，并放入对象池中。
	 * 如果创建后，所有的对象都在使用中，则返回 null
	 */
	private Snmp getFreeSnmp() {

		// 从对象池中获得一个可用的对象
		Snmp obj = findFreeSnmp();

		if (obj == null) {
			if(snmpPool.size() < maxNum){
				createPooledSnmp(); // 如果目前对象池中没有可用的对象，创建一些对象
			}

			// 重新从池中查找是否有可用对象
			obj = findFreeSnmp();

			// 如果创建对象后仍获得不到可用的对象，则返回 null
			if (obj == null) {
				return null;
			}
		}

		return obj;
	}

	private void createPooledSnmp() {
		
		Snmp s = this.initSnmpComm();
		if (s != null) {
			PooledSnmp ps = new PooledSnmp(s);
			snmpPool.addElement(ps);
		}
		
	}

	/**
	 * 查找对象池中所有的对象，查找一个可用的对象， 如果没有可用的对象，返回 null
	 */
	private Snmp findFreeSnmp() {

		Snmp obj = null;
		PooledSnmp pObj = null;

		// 获得对象池向量中所有的对象
		@SuppressWarnings("rawtypes")
		Enumeration enumerate = snmpPool.elements();

		// 遍历所有的对象，看是否有可用的对象
		while (enumerate.hasMoreElements()) {
			pObj = (PooledSnmp) enumerate.nextElement();

			// 如果此对象不忙，则获得它的对象并把它设为忙
			if (!pObj.isBusy()) {
				obj = pObj.getSnmp();
				pObj.setBusy(true);
				break;
			}
		}
		return obj;// 返回找到到的可用对象
	}

	/**
	 * 此函数返回一个对象到对象池中，并把此对象置为空闲。 所有使用对象池获得的对象均应在不使用此对象时返回它。
	 */

	public void returnSnmp(Snmp snmp) {

		// 确保对象池存在，如果对象没有创建（不存在），直接返回
		if (snmpPool == null) {
			return;
		}

		PooledSnmp pObj = null;

		@SuppressWarnings("rawtypes")
		Enumeration enumerate = snmpPool.elements();

		// 遍历对象池中的所有对象，找到这个要返回的对象对象
		while (enumerate.hasMoreElements()) {
			pObj = (PooledSnmp) enumerate.nextElement();

			// 先找到对象池中的要返回的对象对象
			if (snmp.equals(pObj.getSnmp())) {
				// 找到了 , 设置此对象为空闲状态
				pObj.setBusy(false);
				break;
			}
		}
	}

	/**
	 * 关闭对象池中所有的对象，并清空对象池。
	 */
	public synchronized void closeObjectPool() {

		// 确保对象池存在，如果不存在，返回
		if (snmpPool == null) {
			return;
		}

		PooledSnmp pSnmp = null;

		@SuppressWarnings("rawtypes")
		Enumeration enumerate = snmpPool.elements();

		while (enumerate.hasMoreElements()) {

			pSnmp = (PooledSnmp) enumerate.nextElement();

			if (pSnmp.isBusy()) {
				wait(500); // 等0.5 秒
			}else{
				try {
					pSnmp.getSnmp().close();
				} catch (IOException e) {
					log.error(e.getMessage());
				}
				snmpPool.removeElement(pSnmp);
			}
		}

		// 置对象池为空
		snmpPool = null;
	}

	/**
	 * 使程序等待给定的毫秒数
	 */
	private void wait(int mSeconds) {
		try {
			Thread.sleep(mSeconds);
		} catch (InterruptedException e) {
		}
	}
	
	// 初始化snmp通信参数
	private synchronized Snmp initSnmpComm() {
		try{
			TransportMapping transport = new DefaultUdpTransportMapping(new UdpAddress(), true);
			Snmp snmp = new Snmp(transport);
			transport.listen();
			return snmp;
		}catch(IOException e){
			log.info(e.getMessage());
			return null;
		}
	}

	/**
	 * 内部使用的用于保存对象池中对象的类。 此类中有两个成员，一个是对象，另一个是指示此对象是否正在使用的标志 。
	 */
	class PooledSnmp {

		Snmp snmp = null;// 对象
		boolean busy = false; // 此对象是否正在使用的标志，默认没有正在使用

		// 构造函数，根据一个 Object 构告一个 PooledSnmp 对象
		public PooledSnmp(Snmp snmp) {

			this.snmp = snmp;

		}

		public Snmp getSnmp() {
			return snmp;
		}

		public void setSnmp(Snmp snmp) {
			this.snmp = snmp;
		}

		// 获得对象对象是否忙
		public boolean isBusy() {
			return busy;
		}

		// 设置对象的对象正在忙
		public void setBusy(boolean busy) {
			this.busy = busy;
		}
		
		public boolean equals(Object o){
			if(o instanceof PooledSnmp){
				PooledSnmp pSnmp = (PooledSnmp)o;
				return this.snmp.equals(pSnmp.getSnmp());
			}
			return false;
		}
	}
}
