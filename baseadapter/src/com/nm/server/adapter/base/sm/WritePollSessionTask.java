package com.nm.server.adapter.base.sm;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.nm.error.ErrorMessageTable;
import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.MonitorLinkType;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePair;
import com.nm.server.comm.MessagePool;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;


public class WritePollSessionTask extends WriteSessionTask {

	protected static byte[] wps_lock = new byte[0];
	
	public WritePollSessionTask(MessagePool msgPool, Object o) {
		super(msgPool, o);
	}
	
	public void run() {
//		synchronized(wps_lock)
		{
			AdapterMessagePair mp = (AdapterMessagePair)o;
			try  {
				
				if(mp.isRepliedAll() || mp.isTimeout() || mp.isErr() 
						|| mp.getManagedElement() == null) {
					return;
				}
				
				ManagedElement me = mp.getManagedElement();
			
				if (me.getMonitorLinkType() == MonitorLinkType.MLT_ETH)	{
					
					SocketChannel sc = this.getSocketChannel(me);
					
	    			if (null == sc)	{
	    				mp.setErrCode(ErrorMessageTable.COMM_PORT_NOEXISTS);
	    				((AdapterMessagePool)msgPool).addScheduledProcessedMsg(mp);  				
	    				return;
	    			}
	    			
//	    			mp.setGwId(this.getEthGwId(me));
//	    			
//	    			if(this.getSyncSendMode() == 1) {
//	    				if(!this.canScheduleMsgBeSended(sc)) {
////	    					msgPool.addUnSendScheduleMsg(mp);
//	    					((AdapterMessagePool)msgPool).addScheduledGwMsgPair(mp.getGwId(), mp);
//	    					return;
//	    				}
//	    			}
	    			
					// must put into pool firstly
	    			msgPool.addScheduledSendedMsg(mp);
	    			
					Iterator<AsynTask> taskIt = mp.getAsynTaskList().getTaskList().iterator();
					
					while(taskIt.hasNext())	{
							
						AsynTask asynTask = taskIt.next();					

						String destIP = ToolUtil.longToIp(me.getAddress().getIpAddress());			
						
						byte [] srcAddr = sc.socket().getLocalAddress().getAddress();
						byte [] destAddr = ToolUtil.ipStringToBytes(destIP);		
						
		    			for(int ii = 0; ii < asynTask.getTaskCellList().size(); ii++) {
		    				TaskCell taskCell = asynTask.getTaskCellList().get(ii);		    				
		    				
		    				this.addSendMsg(mp,taskCell, sc,srcAddr, destAddr);	
	    				}
					}					
				
					// avoid timeout before sended
					mp.setIsSend(true);
				}
			} catch(NullPointerException nullEx) {
				log.error("------------Null Pointer Exception!-------------");
			} catch (Exception e) {
				mp.setErrCode(ErrorMessageTable.SERVER_EXEC_ERROR);
				((AdapterMessagePool)msgPool).addScheduledProcessedMsg(mp);
				
				log.error("write frame exception:", e);
			}
		}
	}
	
	protected boolean addSendMsg(AdapterMessagePair mp,
			TaskCell taskCell,SocketChannel sc,
			byte[] srcAddr,byte[] destAddr)	{
	
		boolean isSyncMode = this.getSyncSendMode() == 1;
		
		boolean sslWrite = false;
		SSLReadSession sslrs =null;
		if(sc.socket().getPort() == 4000){
			sslWrite = true;
			try {
				 sslrs = (SSLReadSession)this.getReadSession(mp.getManagedElement());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
//		synchronized(msgPool.getLockScheduledSendedMsg()) {
			synchronized(taskCell.getLockRawMsg()) {
				
				try	{
					List<HiSysMessage> msgList = taskCell.getMsgList();				
					
					if(msgList == null || msgList.isEmpty()) {
						return false;
					}
					
					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();
	
					for(int iii = 0; iii < msgList.size(); iii++) {						
						HiSysMessage msg = msgList.get(iii);
						
						MsdhMessage frame = (MsdhMessage)msg;	
						
						sendedMsgList.add(frame);
						
						String priorFrameId = frame.getSynchnizationId(true);
				
						frame.setSrcAddress(srcAddr);
						frame.setDstAddress(destAddr);		        
	
						synchronized(ws_lock) 
						{
							// 构造完整的帧，并注入帧序号				
							frame.buildFrame(EthGate.getNextMsgSequenceNumber());	 
							
//							FileTool.writeSimple("FrameInfo", "FrameId", frame.getSynchnizationId());
						
							// add sent message into sendMsgMap
							taskCell.addSendMsg(frame.getSynchnizationId(true), frame);
							taskCell.removeSendMsg(priorFrameId); 	
							

							Thread.sleep(this.getWriteFrameInterval());
							
							// 构造完整的帧，并注入帧序号
							
							byte[] frameB = frame.getFrame();	        						
							
							// 发送
							ByteBuffer bb = ByteBuffer.wrap(frameB);
							try	{
								if(sslWrite){
									synchronized(sslrs.sslcs){
										sslrs.sslcs.write(bb);
									}
								}else{
									
									synchronized(sc) {
										sc.write(bb);
									}
								}
								frame.setBeginTime(new Date());	
							} catch (Exception e) {
								
								log.error("---write data to socket error: make the socket disconnect!---");
//								log.error("write data to socket error!",e);
								
								// remove sent message from sendMsgMap
								taskCell.removeSendMsg(frame.getSynchnizationId(true));    							
								
								mp.setErrCode(ErrorMessageTable.COMM_WIRTE_DATA_ERROR);
								
//								msgPool.removeScheduledSendedMsg(mp);

								msgPool.addScheduledProcessedMsg(mp);
									        							
								
								this.socketMgr.remove(sc);
								this.socketMgr.doConnect();        								
							}    						
	
								
							FileTool.write("FrameInfo", "PollCommand", frameB);
						}							
					}
					
					// remove sended msg
					taskCell.getMsgList().removeAll(sendedMsgList);		
						
				} catch(Exception e) {
					log.error("write frame exception:", e);
				}
			}
			
			if(mp.isErr()) {
				msgPool.removeScheduledSendedMsg(mp);
			}
			
			return isSyncMode;
//		}
	}	
	
	/* 按网关排队 */
	public boolean canScheduleMsgBeSended(SocketChannel sc) {
		
		synchronized(msgPool.getLockScheduledSendedMsg()) {
			Iterator<MessagePair> it = ((AdapterMessagePool)msgPool).getScheduledSendedMsgPairs().iterator();
			while(it.hasNext()) {
				AdapterMessagePair mp = (AdapterMessagePair)it.next();
				
				try {
					if(this.getSocketChannel(mp.getManagedElement()) == sc 
							&& mp.haveUnResponsedFrames()) {
						return false;
					}
				} catch (Exception e) {
					log.error("canScheduleMsgBeSendAgain error:",e);
				}
			}
			
			return true;
		}
	}
	
	protected ReadSession getReadSession(ManagedElement me) throws Exception {
		if(!me.isServeAsEthGateway()) {
			return null;
		}
		
		EthGate gate = this.topoManager.getEthGate(me.getId());
		
		//String strIp = ToolUtil.longToIp(me.getAddress().getIpAddress());
		return this.socketMgr.getReadSession(gate);
	}
}
