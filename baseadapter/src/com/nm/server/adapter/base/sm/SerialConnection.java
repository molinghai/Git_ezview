package com.nm.server.adapter.base.sm;

import gnu.io.CommPortIdentifier;
import gnu.io.CommPortOwnershipListener;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TooManyListenersException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.nmm.res.SerialConnectionInfo;
import com.nm.server.adapter.base.comm.AdapterMessagePool;
import com.nm.server.adapter.base.comm.TabsMessage;
import com.nm.server.adapter.base.exception.SerialConnectionException;
import com.nm.server.comm.ClientMessageListener;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.common.util.ToolUtil;

/**
 * A class that handles the details of a serial connection. Reads from one
 * TextArea and writes to a second TextArea. Holds the state of the connection.
 */
public class SerialConnection implements SerialPortEventListener,
		CommPortOwnershipListener {

	protected ClientMessageListener msgListener;
	private SerialConnectionInfo parameters;
	private OutputStream os;
	private InputStream is;

	private CommPortIdentifier portId;
	private SerialPort sPort;

	private boolean open;
	public ByteBuffer readBuffer = ByteBuffer.allocate(4096);
	private Log log = LogFactory.getLog(SerialConnection.class);

	public SerialConnection(SerialConnectionInfo parameters) {
		this.parameters = parameters;
		open = false;
	}

	/**
	 * Attempts to open a serial connection and streams using the parameters in
	 * the SerialParameters object. If it is unsuccesfull at any step it returns
	 * the port to a closed state, throws a
	 * <code>SerialConnectionException</code>, and returns.
	 * 
	 * Gives a timeout of 2 seconds on the portOpen to allow other applications
	 * to reliquish the port if have it open and no longer need it.
	 */
	public void openConnection() throws SerialConnectionException, NoSuchPortException {

		// Obtain a CommPortIdentifier object for the port you want to open.
		try {
			portId = CommPortIdentifier.getPortIdentifier(parameters
					.getPortName());
			if(!portId.isCurrentlyOwned()){
				sPort = (SerialPort) portId.open("Ezview", 2000);
			}else{
				open = true;
				return;
			}
			setConnectionParameters();
			os = sPort.getOutputStream();
			is = sPort.getInputStream();
			sPort.addEventListener(this);
			
			// Set notifyOnDataAvailable to true to allow event driven input.
			sPort.notifyOnDataAvailable(true);
			
			// Set notifyOnBreakInterrup to allow event driven break handling.
			sPort.notifyOnBreakInterrupt(true);
			
			// Set receive timeout to allow breaking out of polling loop during
			// input handling.
			int timeout_frame = ToolUtil.getEntryConfig().getSingleFrameTimeoutSerial();
			sPort.enableReceiveTimeout(timeout_frame);
			
			// Add ownership listener to allow ownership event handling.
			portId.addPortOwnershipListener(this);

			open = true;
		} catch (NoSuchPortException e) {
			throw e;
		}catch (PortInUseException e) {
			throw new SerialConnectionException(e.getMessage());
		} catch (SerialConnectionException e) {
			sPort.close();
			throw e;
		}catch (IOException e) {
			sPort.close();
			throw new SerialConnectionException("Error opening i/o streams");
		} catch (TooManyListenersException e) {
			sPort.close();
			throw new SerialConnectionException("too many listeners added");
		}catch(Exception e){
			throw new SerialConnectionException(e.getMessage());
		}

		
	}

	/**
	 * Sets the connection parameters to the setting in the parameters object.
	 * If set fails return the parameters object to origional settings and throw
	 * exception.
	 */
	public void setConnectionParameters() throws SerialConnectionException {

		// Save state of parameters before trying a set.
		int oldBaudRate = sPort.getBaudRate();
		int oldDatabits = sPort.getDataBits();
		int oldStopbits = sPort.getStopBits();
		int oldParity = sPort.getParity();
		int oldFlowControl = sPort.getFlowControlMode();

		// Set connection parameters, if set fails return parameters object
		// to original state.
		try {
			int stopBit = 1;
			if(oldStopbits == 2){
				stopBit = SerialPort.STOPBITS_1_5;
			}else if(oldStopbits == 3){
				stopBit = SerialPort.STOPBITS_2;
			}
			sPort.setSerialPortParams((int)parameters.getBaudRate(),
					parameters.getDataBit(), stopBit,
					parameters.getParity().ordinal());
		} catch (UnsupportedCommOperationException e) {
			parameters.setBaudRate(oldBaudRate);
			parameters.setDataBit(oldDatabits);
			parameters.setStopBit(oldStopbits);
			parameters.setParity(oldParity);
			throw new SerialConnectionException("Unsupported parameter");
		}

		// Set flow control.
		try {
			sPort.setFlowControlMode(oldFlowControl);
		} catch (UnsupportedCommOperationException e) {
			throw new SerialConnectionException("Unsupported flow control");
		}
	}

	/**
	 * Close the port and clean up associated elements.
	 */
	public void closeConnection() {
		// If port is alread closed just return.
		if (!open) {
			return;
		}

		// Check to make sure sPort has reference to avoid a NPE.
		if (sPort != null) {
			try {
				// close the i/o streams.
				os.close();
				is.close();
			} catch (IOException e) {
				System.err.println(e);
			}

			// Close the port.
			sPort.close();

			// Remove the ownership listener.
			portId.removePortOwnershipListener(this);
		}

		open = false;
	}

	/**
	 * Send a one second break signal.
	 */
	public void sendBreak() {
		sPort.sendBreak(1000);
	}

	/**
	 * Reports the open status of the port.
	 * 
	 * @return true if port is open, false if port is closed.
	 */
	public boolean isOpen() {
		return open;
	}

	/**
	 * Handles SerialPortEvents. The two types of SerialPortEvents that this
	 * program is registered to listen for are DATA_AVAILABLE and BI. During
	 * DATA_AVAILABLE the port buffer is read until it is drained, when no more
	 * data is availble and 30ms has passed the method returns. When a BI event
	 * occurs the words BREAK RECEIVED are written to the messageAreaIn.
	 * BI -通讯中断.
	　　CD -载波检测.
	　　CTS -清除发送.
	　　DATA_AVAILABLE -有数据到达.
	　　DSR -数据设备准备好.
	　　FE -帧错误.
	　　OE -溢位错误.
	　　OUTPUT_BUFFER_EMPTY -输出缓冲区已清空.
	　　PE -奇偶校验错.
		RI -　振铃指示.
	 */
	
	@Override
	public void serialEvent(SerialPortEvent e) {
		// Create a StringBuffer and int to receive input data.
		int newData = 0;

		// Determine type of event.
		switch (e.getEventType()) {

		// Read data until -1 is returned. If \r is received substitute
		// \n for correct newline handling.
		case SerialPortEvent.DATA_AVAILABLE:
			synchronized(this.readBuffer){
				while (newData != -1) {
					try {
						newData = is.read();
						if (newData == -1) {
							break;
						}
						readBuffer.put((byte) newData);
					} catch (IOException ex) {
						LogFactory.getLog("SerialPort Read Error : " + this.getClass()).error(ex.getMessage());
						return;
					}
				}
			}

			// Append received data to messageAreaIn.
			break;

		// If break event append BREAK RECEIVED message.
		case SerialPortEvent.BI:
			log.info("#####e.getEventType() == SerialPortEvent.BI");
			this.closeConnection();
		default:
			log.info("#####e.getEventType() == " + e.getEventType());
		}
		
	}

	/**
	 * Handles ownership events. If a PORT_OWNERSHIP_REQUESTED event is received
	 * a dialog box is created asking the user if they are willing to give up
	 * the port. No action is taken on other types of ownership events.
	 */
	public void ownershipChange(int type) {
		if (type == CommPortOwnershipListener.PORT_OWNERSHIP_REQUESTED) {

		}
	}
	
	public String getPortName(){
		if(sPort != null){
			return sPort.getName();
		}else{
			return parameters.getPortName();
		}
	}

	public SerialConnectionInfo getParameters() {
		return parameters;
	}

	public void setParameters(SerialConnectionInfo parameters) {
		this.parameters = parameters;
	}

	public OutputStream getOs() {
		return os;
	}

	public InputStream getIs() {
		return is;
	}

	public SerialPort getsPort() {
		return sPort;
	}

	public void reply() {
		try
		{
			
			List<HiSysMessage> msgList = this.fetch();
			
			if(msgList != null)
			{
//				log.info("msgList.size()=" + msgList.size());
//				this.filterTrapCmds(msgList);
				
				AdapterMessagePool msgPool = (AdapterMessagePool)this.getMsgListener().getMsgPool();
				msgPool.addRevMsgList(msgList);
			}
			

		}
		catch (Exception e)
		{
			log.error("SerialConnection reply exception:", e);
		}		
	}

	public List<HiSysMessage> fetch()
	{
	
		List<HiSysMessage> msgList = null;
		
		synchronized(this.readBuffer)
		{		
    		int dataLen = readBuffer.capacity() - readBuffer.remaining();
    		byte[] array = readBuffer.array();
//    		int checkSum = array[dataLen - 1] << 8 | array[dataLen - 2];
    		TabsMessage msg = new TabsMessage();
    		if(dataLen > 3 && getPositive(array[2]) == dataLen - 5){
    			msgList = new ArrayList<HiSysMessage>();
    			List<String> dd = new ArrayList<String>();
    			for(int i = 0; i< dataLen; i++){
    				dd.add(Integer.toHexString(getPositive(array[i])));
    			}
    			log.info("--------------" + Arrays.toString(dd.toArray()));
    			
    			try {
    				msg.setHeadData(array, 3);
					msg.setFixedData(array, 3, dataLen - 5);
					msg.setCheckSum(array, dataLen - 2, 2);
					List<String> ddd = new ArrayList<String>();
	    			for(int i = 0; i< msg.getHeadData().length; i++){
	    				ddd.add(Integer.toHexString(getPositive(msg.getHeadData()[i])));
	    			}
	    			log.info("--------head------" + Arrays.toString(ddd.toArray()));
	    			if(msg.getFixedData().length > 2){
	    				String tabsAddr = Integer.toString(getPositive(msg.getHeadData()[1]) >> 3) + "-" + 
						Integer.toString(getPositive(msg.getFixedData()[2]));
	    				msg.setTabsAddr(tabsAddr);
	    			}
					msgList.add(msg);
				} catch (Exception e) {
					LogFactory.getLog(SerialConnection.class).error("setFixedData Error:", e);
				}finally{
					readBuffer.clear();
				}
				
    		}
		}
		return msgList;

	}	

	public static int getPositive(byte btVal) {
		return btVal >= 0 ? btVal : 256 + btVal;
	}
	
	public ClientMessageListener getMsgListener() {
		return msgListener;
	}

	public void setMsgListener(ClientMessageListener msgListener) {
		this.msgListener = msgListener;
	}
	
}
