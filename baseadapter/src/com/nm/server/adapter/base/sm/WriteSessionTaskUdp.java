package com.nm.server.adapter.base.sm;

import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.nm.error.ErrorMessageTable;
import com.nm.nmm.res.EthGate;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.MonitorLinkType;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.base.comm.UdpMessage;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class WriteSessionTaskUdp extends WriteSessionTask{
	
	protected static byte[] udp_lock = new byte[0];

	public WriteSessionTaskUdp(MessagePool msgPool, Object o) {
		super(msgPool, o);
	}
	
	@Override
	public void run() {
		{
			AdapterMessagePair mp = (AdapterMessagePair)o;
			if(mp.getScheduleType() == ScheduleType.ST_NEPOLL){
				mp.setBeginTime();
			}
			/*send manual cmd's progress to client*/
			this.sendProgressToClient(mp);
			
			try{
				if(mp.isRepliedAll() || mp.isTimeout() || mp.isErr() 
						|| mp.getManagedElement() == null) {
					return;
				}
				ManagedElement me = mp.getManagedElement();
				if (me.getMonitorLinkType() == MonitorLinkType.MLT_ETH)	{
					DatagramChannel dc = this.getDatagramChannel(me);
	    			if (null == dc)	{
	    				mp.setErrCode(ErrorMessageTable.COMM_PORT_NOEXISTS);
	    				this.addMsgToProcessedPoolAndCancelProgress(mp);
	    				return;
	    			}
	    			
	    			synchronized(mp){
	    				if(!mp.isSendedMsgPair() || mp.isSendAgin()){
	    					msgPool.addSendedMsg(mp);
	    					mp.setSendedMsgPair(true);
	    				}
	    			}
	    			
	    			boolean isSyncMode = false;
	    			Iterator<AsynTask> taskIt = mp.getAsynTaskList().getTaskList().iterator();
	    			
	    			while(!mp.isCancel() && !mp.isErr()&& taskIt.hasNext())	{
	    				AsynTask asynTask = taskIt.next();
	    				String srcAddr = ToolUtil.bytesToIp(true, dc.socket().getLocalAddress().getAddress());
						EthGate ethGate = this.topoManager.getEthGate(this.getEthGwId(me));
						String destAddr = ToolUtil.longToIp(ethGate.getIpAddress());
	    				for(int ii = 0; !mp.isCancel() && !mp.isErr() && 
	    					ii < asynTask.getTaskCellList().size(); ii++) {
	    					TaskCell taskCell = asynTask.getTaskCellList().get(ii);
		    				//比对协议类型，如果不通则跳过
							if(taskCell.getProtocolType() != null 
									&& !CommProtocol.CP_UDP.equals(taskCell.getProtocolType())){
								continue;
							}
	    					if(isSyncMode) {
	    						break;
	    					}
		    				isSyncMode = this.addSendMsg(mp,taskCell, dc, srcAddr, destAddr);
	    				}
	    				
	    				if(isSyncMode) {
	    					break;
	    				}
	    			}
	    			if(mp != null) {
						mp.setIsSend(true);
					} else {
						log.error("mp is null, may be caused by cancel!");
					}
				}
			} catch (Exception e) {
				mp.setErrCode(ErrorMessageTable.SERVER_EXEC_ERROR);
				this.addMsgToProcessedPoolAndCancelProgress(mp);
				log.error("write frame exception:", e);
			}
		}
	}
	
	protected boolean addSendMsg(AdapterMessagePair mp, TaskCell taskCell, DatagramChannel dc, String srcAddr, String destAddr){
		synchronized (udp_lock) {
			boolean isSyncMode = this.getSyncSendMode() == 1;
			synchronized(taskCell.getLockRawMsg()) {
				try	{
					List<HiSysMessage> msgList = taskCell.getMsgList();
					if(msgList == null || msgList.isEmpty()) {
						return false;
					}					
					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();
					int msgId = 1;
					for(int iii = 0; iii < msgList.size(); iii++) {
						HiSysMessage msg = msgList.get(iii);
						UdpMessage message = (UdpMessage)msg;
						message.setMsgId(srcAddr + "_" + destAddr + "_udp" + (msgId++));
						int port = dc.socket().getPort();
						sendedMsgList.add(message);
						if(msg.isReplied()) {
							continue;
						}
						
						taskCell.addSendMsg(message.getSynchnizationId(), message); 	
						
						Thread.sleep(this.getWriteFrameInterval());
						
						byte[] frame = message.getUserData();
						ByteBuffer bb = ByteBuffer.wrap(frame);
						try	{
							synchronized(dc) {
								dc.write(bb);
							}
							message.setBeginTime(new Date());
						} catch (Exception e) {
							log.error("---write data to socket error: make the udp socket disconnect!---");
							taskCell.removeSendMsg(message.getSynchnizationId(true));
							mp.setErrCode(ErrorMessageTable.COMM_WIRTE_DATA_ERROR);								
							this.addMsgToProcessedPoolAndCancelProgress(mp);
							this.socketMgr.remove(dc);
							this.socketMgr.doConnect();
						}
						if(this.isRecordFrame(mp.getScheduleType())) {
							StringBuilder frameData = new StringBuilder();
							String ip = destAddr;
							frameData.append("ip=").append(ip).append(" ").append("port=").append(port).append("\n");
							for(int i=0; i<frame.length; i++){
								String hv = Integer.toHexString(frame[i] & 0xff);
								if(hv.length()<2){
									frameData.append(0);
								}
								frameData.append(hv);
								if(i<frame.length-1){
									frameData.append(" ");
								}
							}
							if(message.getReTransmittedTimes() == 0) {
								FileTool.writeSimple("FrameInfo", this.getRecordFileName(mp), frameData.toString());
							} else	{
								FileTool.writeSimple("FrameInfo", "ReTransmit", frameData.toString());
							}
						}
					}
					taskCell.getMsgList().removeAll(sendedMsgList);
				} catch(Exception e) {
					log.error("write frame exception:", e);
				}
			}
			
			if(mp.isErr()) {
				this.msgPool.removeSendedMsg(mp);
			}	
			return isSyncMode;
		}
	}
}
