package com.nm.server.adapter.base.sm;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.nm.error.ErrorMessageTable;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.res.ManagedElement.MonitorLinkType;
import com.nm.nmm.service.TopoManager;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.TabsMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePool;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class WriteSessionTaskTabs extends WriteSessionTask {
	
	private SerialPortMgrServer serialPortMgr;
	
	public WriteSessionTaskTabs(MessagePool msgPool,Object o) {
		super(msgPool,o);
	}
	

	public void setTopoManager(TopoManager topoManager)	{
		this.topoManager = topoManager;
	}


	public void setSerialPortMgr(SerialPortMgrServer serialPortMgr) {
		this.serialPortMgr = serialPortMgr;
	}

	
	protected int getWriteFrameInterval() {
		return ToolUtil.getEntryConfig().getSendFrameIntervalSerial();
	}
	
	protected int getManualSendFrameInterval() {
		return ToolUtil.getEntryConfig().getManualSendFrameInterval();
	}
	
	protected int getSyncSendMode() {
		return ToolUtil.getEntryConfig().getSyncSendMode();
	}
	
	protected int getManualSyncSendMode() {
		return ToolUtil.getEntryConfig().getManualSyncSendMode();
	}
	
	protected int getManualSingleFrameTimeOut() {
		return ToolUtil.getEntryConfig().getManualSingleFrameTimeout() * 1000;
	}
	
	

	@SuppressWarnings("unused")
	public void run() {
//		log.info("%%%%%%%%%%%%WriteSessionTask  begin%%%%%"+new Date()+"%%%%%%%%%%%");
		{
			
			AdapterMessagePair mp = (AdapterMessagePair)o;
			if(mp.getScheduleType() == ScheduleType.ST_NEPOLL){
				mp.setBeginTime();
			}
			/*send manual cmd's progress to client*/
			this.sendProgressToClient(mp);
			
			try  {

				if(mp.isRepliedAll() || mp.isTimeout() || mp.isErr() 
						|| mp.getManagedElement() == null) {
					return;
				}
				
				ManagedElement me = mp.getManagedElement();
			
				if (me.getMonitorLinkType() == MonitorLinkType.MLT_COM)	{
					
					SerialConnection conn = this.serialPortMgr.getSerialPortConnection();
					
	    			if (null == conn)	{
	    				log.error("Can not find SerialConnection !!!" );
	    				
	    				mp.setErrCode(ErrorMessageTable.COMM_PORT_NOEXISTS);
	    				this.addMsgToProcessedPoolAndCancelProgress(mp);	    				
	    				
	    				return;
	    			}
	    			
	    				msgPool.addSendedMsg(mp);
		    			
		    			boolean isSyncMode = false;
		    			
						Iterator<AsynTask> taskIt = mp.getAsynTaskList().getTaskList().iterator();
						
						while(!mp.isCancel() && !mp.isErr()&& taskIt.hasNext())	{
								
							AsynTask asynTask = taskIt.next();					

							String destIP = ToolUtil.longToIp(me.getAddress().getIpAddress());			
							
							byte [] destAddr = ToolUtil.ipStringToBytes(destIP);		
							
			    			for(int ii = 0; !mp.isCancel() && !mp.isErr()
			    							&& ii < asynTask.getTaskCellList().size(); ii++) {
			    				TaskCell taskCell = asynTask.getTaskCellList().get(ii);		    				
			    				//比对协议类型，如果不通则跳过
//								if(taskCell.getProtocolType() != null && !CommProtocol.CP_TABS.equals(taskCell.getProtocolType())){
//									continue;
//								}
			    				if(isSyncMode) {
			    					break;
			    				}

			    				isSyncMode = this.addSendMsg(mp,taskCell, conn, destAddr);	
		    				}
			    			
		    				if(isSyncMode) {
		    					break;
		    				}
						}					
					
						if(mp != null) {
							// avoid timeout before sended
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
//		log.info("%%%%%%%%%%%%WriteSessionTask  end%%%%%"+new Date()+"%%%%%%%%%%%");
	}
	
	protected boolean addSendMsg(AdapterMessagePair mp,
			TaskCell taskCell,SerialConnection conn,
			byte[] destAddr)	{
	
		boolean isSyncMode = this.getSyncSendMode() == 1;
		
			synchronized(taskCell.getLockRawMsg()) {
				
				try	{
					List<HiSysMessage> msgList = taskCell.getMsgList();				
					
					if(msgList == null || msgList.isEmpty()) {
						return false;
					}
					
					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();
	
					for(int iii = 0; iii < msgList.size(); iii++) {						
						HiSysMessage msg = msgList.get(iii);
						
						TabsMessage frame = (TabsMessage)msg;	
						int tabs = mp.getManagedElement().getAddress().getTabsAddress();
						frame.setDstAddress(tabs);
						sendedMsgList.add(frame);
						
						if(msg.isReplied()) {
							continue;
						}
						
						String priorFrameId = frame.getSynchnizationId();
				
						synchronized(ws_lock) {
							
							// add sent message into sendMsgMap
							taskCell.addSendMsg(priorFrameId, frame);
							
							if(taskCell.getObj() != null &&((ResFunc)taskCell.getObj()).getFrameInterval() > 0){
								Thread.sleep(((ResFunc)taskCell.getObj()).getFrameInterval());
								log.debug("Frame Interval of TaskCell is:"+((ResFunc)taskCell.getObj()).getFrameInterval());
							}else if(msg.getFrameInterval()>0){
								Thread.sleep(msg.getFrameInterval());
								log.debug("Frame Interval of Msg is:"+msg.getFrameInterval());
							}else{
								Thread.sleep(this.getWriteFrameInterval());
								log.debug("Frame Interval of Default is:"+this.getWriteFrameInterval() * 30);
							}
							
							
							
							// 构造完整的帧，并注入帧序号
							
							byte[] frameB = frame.getFrame();	        						
							
							// 发送
							try	{
								
								synchronized(conn) {
									conn.getOs().write(frameB);
								}
								frame.setBeginTime(new Date());	
							} catch (Exception e) {
								
								log.error("---write data to SerialPort error!---");
								
								// remove sent message from sendMsgMap
								taskCell.removeSendMsg(priorFrameId);    							
								
								mp.setErrCode(ErrorMessageTable.COMM_WIRTE_DATA_ERROR);

								this.addMsgToProcessedPoolAndCancelProgress(mp);	        							
								
							}    						
	
							if(this.isRecordFrame(mp.getScheduleType())) {
								
								if(frame.getReTransmittedTimes() == 0) {
									FileTool.writeTabsFrame("FrameInfo",this.getRecordFileName(mp), frameB, frame.getTabsAddr(), frame.getSrcAddr());
								} else	{
									FileTool.writeTabsFrame("FrameInfo","ReTransmit", frameB, frame.getTabsAddr(), frame.getSrcAddr());
								}
							}	
							
							if(iii == 0 && this.getSyncSendMode() == 1) {
								break;
							}
						}							
					}
					
					// remove sended msg
					taskCell.getMsgList().removeAll(sendedMsgList);		
						
				} catch(Exception e) {
					log.error("write frame exception:", e);
				}
			}
			
			if(mp.isErr()) {
				this.msgPool.removeSendedMsg(mp);
			}
			
			
			return isSyncMode;
//		}
	}
	
}
