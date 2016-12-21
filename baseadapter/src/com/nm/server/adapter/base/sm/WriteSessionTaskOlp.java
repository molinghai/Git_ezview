package com.nm.server.adapter.base.sm;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.nm.error.ErrorMessageTable;
import com.nm.server.adapter.base.comm.AdapterMessagePair;
import com.nm.server.adapter.base.comm.OlpMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.MessagePool;
import com.nm.server.common.util.FileTool;

public class WriteSessionTaskOlp extends WriteSessionTask {
	protected static byte[] olp_lock = new byte[0];

	public WriteSessionTaskOlp(MessagePool msgPool, Object o) {
		super(msgPool, o);
	}

	protected boolean addSendMsg(AdapterMessagePair mp, TaskCell taskCell,
			SocketChannel sc, byte[] srcAddr, byte[] destAddr) {

		synchronized (olp_lock) {

			boolean isSyncMode = this.getSyncSendMode() == 1;
			synchronized (taskCell.getLockRawMsg()) {

				try {
					List<HiSysMessage> msgList = taskCell.getMsgList();

					if (msgList == null || msgList.isEmpty()) {
						return false;
					}

					List<HiSysMessage> sendedMsgList = new ArrayList<HiSysMessage>();

					int msgId = 1;
					for (int iii = 0; iii < msgList.size(); iii++) {
						HiSysMessage msg = msgList.get(iii);

						OlpMessage frame = (OlpMessage) msg;
						frame.setMsgId("olp" + String.valueOf(msgId++));

						sendedMsgList.add(frame);

						if (msg.isReplied()) {
							continue;
						}

						// 构造完整的帧，并注入帧序号
						taskCell.addSendMsg(frame.getSynchnizationId(), frame);

						Thread.sleep(this.getWriteFrameInterval());

						byte[] frameB = frame.getUserData();

						// 发送
						ByteBuffer bb = ByteBuffer.wrap(frameB);
						try {
							synchronized (sc) {
								sc.write(bb);
							}
							frame.setBeginTime(new Date());
						} catch (Exception e) {

							log.error("---write data to socket error: make the socket disconnect!---");
							taskCell.removeSendMsg(frame.getSynchnizationId(true));

							mp.setErrCode(ErrorMessageTable.COMM_WIRTE_DATA_ERROR);
							this.addMsgToProcessedPoolAndCancelProgress(mp);

							this.socketMgr.remove(sc);
							this.socketMgr.doConnect();
						}

						if (this.isRecordFrame(mp.getScheduleType())) {

							if (frame.getReTransmittedTimes() == 0) {
								FileTool.writeSimple("FrameInfo", this
										.getRecordFileName(mp), frameB);
							} else {
								FileTool.writeSimple("FrameInfo", "ReTransmit",
										frameB);
							}
						}
					}

					// remove sended msg
					taskCell.getMsgList().removeAll(sendedMsgList);

				} catch (Exception e) {
					log.error("write frame exception:", e);
				}
			}

			if (mp.isErr()) {
				this.msgPool.removeSendedMsg(mp);
			}

			return isSyncMode;
		}
	}
}
