package com.nm.server.adapter.snmp;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.WorkerTask;

public class SnmpUDPTrasport extends DefaultUdpTransportMapping {

	protected static Log log = LogFactory.getLog(SnmpUDPTrasport.class);
	protected SnmpListenThread listenerThread;
	
	public SnmpUDPTrasport(UdpAddress udpAddress, boolean reuseAddress)
			throws IOException {
		super(udpAddress, reuseAddress);
		// TODO Auto-generated constructor stub
		this.socket.setSoTimeout(20000);
	}

	public SnmpUDPTrasport() throws IOException {
		super();
		// TODO Auto-generated constructor stub
	}



	public void close() throws IOException {
		log.info("##### Trasport.close()---begin---hashcode = "
				+ this.hashCode());
		boolean interrupted = false;
		WorkerTask l = this.listener;
		if (l != null) {
			l.terminate();
			l.interrupt();
			this.listener = null;
		}
		DatagramSocket closingSocket = this.socket;
		if ((closingSocket != null) && (!(closingSocket.isClosed()))) {
			closingSocket.close();
		}
		this.socket = null;
		if (interrupted)
			Thread.currentThread().interrupt();
		log.info("##### Trasport.close()---end---hashcode = " + this.hashCode());
	}

	class SnmpListenThread implements WorkerTask {
		private byte[] buf;
		private volatile boolean stop = false;

		public SnmpListenThread() throws SocketException {
			this.buf = new byte[SnmpUDPTrasport.this.getMaxInboundMessageSize()];
		}

		public void run() {
			try {
				SnmpUDPTrasport.this.socket.setSoTimeout(SnmpUDPTrasport.this
						.getSocketTimeout());
				if (SnmpUDPTrasport.this.getReceiveBufferSize() > 0) {
					SnmpUDPTrasport.this.socket.setReceiveBufferSize(Math.max(
							SnmpUDPTrasport.this.getReceiveBufferSize(),
							SnmpUDPTrasport.this.maxInboundMessageSize));
				}

				if (SnmpUDPTrasport.log.isDebugEnabled()) {
					SnmpUDPTrasport.log
							.debug("UDP receive buffer size for socket "
									+ SnmpUDPTrasport.this.getAddress()
									+ " is set to: "
									+ SnmpUDPTrasport.this.socket
											.getReceiveBufferSize());
				}

			} catch (SocketException ex) {
				SnmpUDPTrasport.log.error(ex);
				SnmpUDPTrasport.this.setSocketTimeout(0);
			}
			while (!(this.stop)) {
				DatagramPacket packet = new DatagramPacket(this.buf,
						this.buf.length,
						SnmpUDPTrasport.this.udpAddress.getInetAddress(),
						SnmpUDPTrasport.this.udpAddress.getPort());
				try {
					DatagramSocket socketCopy = SnmpUDPTrasport.this.socket;
					if (socketCopy == null) {
						this.stop = true;
					}
					socketCopy.setSoTimeout(20000);
					socketCopy.receive(packet);
				} catch (InterruptedIOException iiox) {
					if (iiox.bytesTransferred > 0)
						;
					if (SnmpUDPTrasport.log.isDebugEnabled())
						SnmpUDPTrasport.log.debug("Received message from "
								+ packet.getAddress()
								+ "/"
								+ packet.getPort()
								+ " with length "
								+ packet.getLength()
								+ ": "
								+ new OctetString(packet.getData(), 0, packet
										.getLength()).toHexString());
					ByteBuffer bis;
					if (SnmpUDPTrasport.this.isAsyncMsgProcessingSupported()) {
						byte[] bytes = new byte[packet.getLength()];
						System.arraycopy(packet.getData(), 0, bytes, 0,
								bytes.length);
						bis = ByteBuffer.wrap(bytes);
					} else {
						bis = ByteBuffer.wrap(packet.getData());
					}
					SnmpUDPTrasport.this.fireProcessMessage(new UdpAddress(
							packet.getAddress(), packet.getPort()), bis, null);

					break ;
				} catch (PortUnreachableException purex) {
					synchronized (SnmpUDPTrasport.this) {
						SnmpUDPTrasport.this.listener = null;
					}
					SnmpUDPTrasport.log.error(purex);
					if (SnmpUDPTrasport.log.isDebugEnabled()) {
						purex.printStackTrace();
					}
					if (SNMP4JSettings.isForwardRuntimeExceptions()) {
						throw new RuntimeException(purex);
					}
					break ;
				} catch (SocketException soex) {
					if (!(this.stop)) {
						SnmpUDPTrasport.log.error(
								"Socket for transport mapping "
										+ super.toString() + " error: "
										+ soex.getMessage(), soex);
					}

					this.stop = true;

					break ;
				} catch (IOException iox) {
					SnmpUDPTrasport.log.warn(iox);
					if (SnmpUDPTrasport.log.isDebugEnabled()) {
						iox.printStackTrace();
					}
					if (SNMP4JSettings.isForwardRuntimeExceptions()) {
						throw new RuntimeException(iox);
					}
					break;
				}
			}
			synchronized (SnmpUDPTrasport.this) {
				SnmpUDPTrasport.this.listener = null;
				this.stop = true;
				DatagramSocket closingSocket = SnmpUDPTrasport.this.socket;
				if ((closingSocket != null) && (!(closingSocket.isClosed()))) {
					closingSocket.close();
				}
			}
			if (SnmpUDPTrasport.log.isDebugEnabled())
				SnmpUDPTrasport.log.debug("Worker task stopped:"
						+ super.getClass().getName());
		}

		public void close() {
			this.stop = true;
		}

		public void terminate() {
			close();
			if (SnmpUDPTrasport.log.isDebugEnabled())
				SnmpUDPTrasport.log.debug("Terminated worker task: "
						+ super.getClass().getName());
		}

		public void join() throws InterruptedException {
			if (SnmpUDPTrasport.log.isDebugEnabled())
				SnmpUDPTrasport.log.debug("Joining worker task: "
						+ super.getClass().getName());
		}

		public void interrupt() {
			if (SnmpUDPTrasport.log.isDebugEnabled()) {
				SnmpUDPTrasport.log.debug("Interrupting worker task: "
						+ super.getClass().getName());
			}
			close();
		}
	}
	
	@Override
	public synchronized void listen() throws IOException {
		if (this.listener != null) {
			throw new SocketException("Port already listening");
		}
		ensureSocket();
		this.listenerThread = new SnmpListenThread();
		this.listener = SNMP4JSettings.getThreadFactory().createWorkerThread(
				"SnmpUDPTrasport_" + this.hashCode(),
				this.listenerThread, true);

		this.listener.run();
	}

	private synchronized DatagramSocket ensureSocket() throws SocketException {
		DatagramSocket s = this.socket;
		if (s == null) {
			s = new DatagramSocket(this.udpAddress.getPort());
			s.setSoTimeout(this.getSocketTimeout());
			this.socket = s;
		}
		return s;
	}
}
