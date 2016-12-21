package com.nm.server.adapter.base.sm;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.adapter.base.comm.OlpMessage;
import com.nm.server.comm.HiSysMessage;

public class ReadSessionOlp extends ReadSession {

	public ReadSessionOlp(SocketChannel sc) {
		super(sc);
	}

	public void reply() {

		try {

			List<HiSysMessage> msgList = this.fetch();

			if (msgList != null) {
				AdapterMessagePool msgPool = (AdapterMessagePool) this
						.getMsgListener().getMsgPool();
				msgPool.addRevMsgList(msgList);
			}

		} catch (Exception e) {
			LogFactory.getLog(ReadSessionOlp.class).error(
					"reply exception:", e);
		}
	}

	public List<HiSysMessage> fetch() {

		List<HiSysMessage> msgs = null;

		synchronized (this.readBuffer) {
			int dataLen = readBuffer.capacity() - readBuffer.remaining();
			byte[] array = readBuffer.array();
			
			int i = 0;
			for (i = 0; i < dataLen; i++) {
				if (array[i] == (byte) 0x3c) {
					for (int index = i + 1; index < array.length; index++) {
						if (array[index] == 0x3e) {
							OlpMessage olp = new OlpMessage(array, i, index - i
									+ 1);
							if (null == msgs) {
								msgs = new ArrayList<HiSysMessage>();
							}
							msgs.add(olp);
							i += (index - i + 1);
							break;
						}
					}

				} 
			}

			if (i < dataLen) {
				byte[] remain = new byte[dataLen - i];
				System.arraycopy(array, i, remain, 0, dataLen - i);
				readBuffer.clear();
				readBuffer.put(remain);
			} else {
				readBuffer.clear();
			}
		}
		return msgs;
	}

}
