package com.nm.server.adapter.base.sm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.base.security.algorithm.BASE64EnsureImpl;
import com.nm.base.security.algorithm.KeyManager;
import com.nm.nmm.res.EthGate.GateState;
import com.nm.server.comm.ClientMessageListener;





public class SSLConnectSession extends ConnectSession{
	private static final Log log = LogFactory.getLog(ConnectSession.class);
	
	public static SSLConnectSession instance = null;
	
	int SSL = 3;
	ByteBuffer clientIn, clientOut, cTOs, sTOc, wbuf;
	SocketChannel sc = null;
	SSLEngineResult res;
	SSLEngine sslEngine = null;

	private static String CLIENT_KEY_STORE = "";
	//private static String CLIENT_KEY_STORE_PASSWORD = "changeme1";
	
	private boolean handshakeDone = false;
	
	private ClientMessageListener msgListener;
	
	int appBufferMax;

	int netBufferMax;
    
	public SSLConnectSession(SocketChannel sc,ClientMessageListener msgListener) {
		super(sc);
		// TODO Auto-generated constructor stub
		this.sc = sc;
		this.msgListener = msgListener;
		createSSLEngine();	
	}
	
	 
	@Override
	public void process() {
		if (_key == null) {

			log.info("key is null!");
			return;
		}

		if (!_key.isValid()) {
			_key.cancel();

			log.info("key is not valid!");

			return;
		}

		if (_key.isConnectable()) {
			SocketChannel sc = (SocketChannel) _key.channel();
			if (sc.isConnectionPending()) {

				try {
					sc.finishConnect();
				} catch (Exception e) {
					super.updateGwState(GateState.NOT_CONNECT, false);

					_key.cancel();

					try {
						sc.close();
					} catch (IOException eL) {
						log.error("sokect close exception:", e);
					}

				}

				if (sc.isConnected()) {

					if(SSL != 4){
						try {
							SSL = tryTLS(SSL);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					try
					{
						SSLReadSession sslrs = new SSLReadSession(sc,this);
	
						sslrs.setMsgListener(this.msgListener);
						sslrs.setSm(this.sm);
						
						this.sm.add(sslrs);
						
						if(sc.isOpen())
						{
							SelectionKey key = sc.register(this.sm._sel, sslrs.interestOps(), sslrs);						
							sslrs.registered(key);							
						}
						else
						{
							this.sm.remove(sslrs);						
							log.info("channel is closed!");											
						}					
						InetAddress addr = sc.socket().getInetAddress();						
						log.info("------ Ethernet gate connected: dest=" + addr.toString() + " ------");
						
					}
					catch(Exception e)
					{
						log.error("add readSession exception:",e);
					}
					
					//this.sm.addReadSession(sc);

					this.updateGwState(GateState.CONNECTED, true);

				} else {
					_key.cancel();

					try {
						sc.close();
					} catch (IOException e) {
						log.error("sokect close exception:", e);
					}
				}

				sm.removeConnectSession(this);
				super.gw = null;

			}
		}
	}
	
	public void createSSLEngine(){
		try {
			// create SSLContext
			String svrHome = System.getProperty("server.home");
	    	CLIENT_KEY_STORE =  svrHome + File.separator + "config" + File.separator  + "common" + File.separator + "security" + File.separator + "certificate" + File.separator + "forEthNe" + File.separator +"client.jks";
	    	
	    	System.setProperty("javax.net.ssl.trustStore", CLIENT_KEY_STORE);
	    	System.setProperty("javax.net.ssl.keyStoreType", "JKS");
	    	System.setProperty("javax.net.debug", "ssl,handshake");
	    	
	    	SSLContext context;
			context = SSLContext.getInstance("TLS");
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream(CLIENT_KEY_STORE), null);
			
			String passwdFile = svrHome + File.separator + "config" + File.separator  + "common" + File.separator + "security" + File.separator + "certificate" + File.separator + "forEthNe" + File.separator +"cakey";
			BASE64EnsureImpl b64impl = new BASE64EnsureImpl();
			String passwd = b64impl.decode(KeyManager.getInstance().getProperty(passwdFile, "key"));
			//String passwd = "8#87e1~2";
			
			//BASE64Decoder base64Decode = new BASE64Decoder();
			//String passwd = new String(base64Decode.decodeBuffer(KeyManager.getInstance().getProperty(passwdFile, "key")));
			//ks.load(null, null);
			
			KeyManagerFactory kf = KeyManagerFactory.getInstance("SunX509");
			
			if(kf != null){
				kf.init(ks, passwd.toCharArray());
				context.init(kf.getKeyManagers(), null, null);
			}
			
			
			// create Engine
			sslEngine = context.createSSLEngine();
			// begin
			sslEngine.setUseClientMode(true);

			sslEngine.setEnableSessionCreation(true);
		} catch (Exception e) {
			log.error(e, e);
		}
	}
	
	public int tryTLS(int pSSL) throws IOException {
		SSL = pSSL;
		if (SSL == 0)
			return 0;

		//SSLContext sslContext = null;
		try {
		
			SSLSession session = sslEngine.getSession();
			createBuffers(session);
			// wrap
			clientOut.clear();
			sc.write(wrap(clientOut));
			while (res.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED) {
				if (res.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
					// unwrap
					sTOc.clear();
					while (sc.read(sTOc) < 1)
						Thread.sleep(20);
					sTOc.flip();
					unwrap(sTOc);
					if (res.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED) {
						clientOut.clear();
						sc.write(wrap(clientOut));
					}
				} else if (res.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
					// wrap
					clientOut.clear();
					sc.write(wrap(clientOut));
				} else {
					Thread.sleep(1000);
				}
			}
			clientIn.clear();
			clientIn.flip();
			SSL = 4;
			//Utilities.print("SSL established\n");
			System.out.println("SSL Communication established------------------------------------------------------------------------------------------------------------------------------\n");
		} catch (Exception e) {
			e.printStackTrace(System.out);
			SSL = 0;
		}
		return SSL;
	}

	private void createBuffers(SSLSession session) {

		appBufferMax = session.getApplicationBufferSize();
		netBufferMax = session.getPacketBufferSize();

		clientIn = ByteBuffer.allocate(65536);
		clientOut = ByteBuffer.allocate(appBufferMax);
		wbuf = ByteBuffer.allocate(65536);

		cTOs = ByteBuffer.allocate(netBufferMax);
		sTOc = ByteBuffer.allocate(netBufferMax);
	}
	
	private synchronized ByteBuffer wrap(ByteBuffer b) throws SSLException {
		cTOs.clear();
		res = sslEngine.wrap(b, cTOs);
		cTOs.flip();
		return cTOs;
	}

	private synchronized ByteBuffer unwrap(ByteBuffer b) throws SSLException {
		clientIn.clear();
		//int pos;
		clientIn = ByteBuffer.allocate(65536);
		
		while (b.hasRemaining()) {

			res = sslEngine.unwrap(b, clientIn);

			if (res.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
				// Task
				Runnable task;
				while ((task = sslEngine.getDelegatedTask()) != null) {
					task.run();
				}
			} else if (res.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
				return clientIn;
			} else if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
				return clientIn;
			}
		}
		return clientIn;
	}


	/**
	 * DOCUMENT ME!
	 *
	 * @param src DOCUMENT ME!
	 *
	 * @return DOCUMENT ME!
	 *
	 * @throws IOException DOCUMENT ME!
	 */
	public int write(ByteBuffer src) throws IOException {

		int amount = 0;
		int limit;

		if (SSL == 4) {

			// write in clientOut
			clientOut.clear();
			limit = Math.min(clientOut.remaining(), src.remaining());

			for (int i = 0; i < limit; i++) {
				clientOut.put(src.get());
				amount++;
			}

			// write to socket
			clientOut.flip();
			//sc.configureBlocking(true);

			while (clientOut.hasRemaining()) {
				wrap(clientOut);
				clientOut.compact();
				clientOut.flip();

				if (sc.write(cTOs) == -1) {
					
					return -1;
				}
			}

			//sc.configureBlocking(false);

			return amount;
		}

		return sc.write(src);
	}
	
//	 public synchronized int read(ByteBuffer dst) throws IOException
//	    {
//	        if (sc.socket().isInputShutdown())
//	        {
//	            throw new ClosedChannelException();
//	        }
//	        else if(sslEngine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING){
//	        	return -1;
//	        }
//	        else if (sslEngine.isInboundDone())
//	        {
//	            return -1;
//	        }
//	        else if ((fill(sTOc) < 0) && (sTOc.position() == 0))
//	        {
//	            return -1;
//	        }
//
//	        SSLEngineResult result;
//	        Status status;
//	        do
//	        {
//	            if (!prepare(clientIn, netBufferMax))
//	            {
//	                // Overflow!
//	                break;
//	            }
//
//	            sTOc.flip();
//	            try
//	            {
//	                result = sslEngine.unwrap(sTOc, clientIn);
//	            }
//	            finally
//	            {
//	            	sTOc.compact();
//	                clientIn.flip();
//	            }
//
//	            status = result.getStatus();
//	            if ((status == Status.OK) || (status == Status.BUFFER_UNDERFLOW))
//	            {
//	                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
//	                {
//	                    runTasks();
//	                }
//	            }
//	            else
//	            {
//	                if (status == Status.CLOSED)
//	                {
//	                	sslEngine.closeOutbound();
//	                }
//
//	                throw new IOException("Read error '" + result.getStatus()
//	                    + '\'');
//	            }
//	        } while ((sTOc.position() != 0)
//	            && (status != Status.BUFFER_UNDERFLOW));
//
//	        int n = clientIn.remaining();
//	        if (n > 0)
//	        {
//	            if (n > dst.remaining())
//	            {
//	                n = dst.remaining();
//	            }
//	            for (int i = 0; i < n; i++)
//	            {
//	                dst.put(clientIn.get());
//	            }
//	        }
//	        return n;
//	    }
	 
	  @SuppressWarnings("unused")
	private SSLEngineResult.HandshakeStatus runTasks()
	    {
	        Runnable runnable;
	        while ((runnable = sslEngine.getDelegatedTask()) != null)
	        {
	            runnable.run();
	        }
	        return sslEngine.getHandshakeStatus();
	    }
	   /**
     * Fills the specified buffer.
     * 
     * @param in the buffer.
     * @return the number of read bytes.
     * @throws IOException on I/O errors.
     */
    @SuppressWarnings("unused")
	private synchronized long fill(ByteBuffer in) throws IOException
    {
        try
        {
            long n = sc.read(in);
            if (n < 0)
            {
                // EOF reached.
                sslEngine.closeInbound();
            }
            return n;
        }
        catch (IOException x)
        {
            // Can't read more bytes...
            sslEngine.closeInbound();
            throw x;
        }
    }
    
    @SuppressWarnings("unused")
	private boolean prepare(ByteBuffer src, int remaining)
    {
        ByteBuffer bb = src;
        if (bb.compact().remaining() < remaining)
        {
            int position = bb.position();
            int capacity = position + remaining;
            if (capacity <= 2 * remaining)
            {
                bb = ByteBuffer.allocate(capacity);
                if (position > 0)
                {
                    src.flip();
                    bb.put(src);
                    src = bb;
                }
            }
            else
            {
                bb.flip();
                bb = null;
            }
        }
        return bb != null;
    }
   
    
    
	public int read(ByteBuffer dst) throws IOException {
		int amount = 0, limit;
		
		if (sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)
		{
			if (clientIn.hasRemaining()) {
				limit = Math.min(clientIn.remaining(), dst.remaining());

				for (int i = 0; i < limit; i++) {
					dst.put(clientIn.get());
					amount++;
				}

				return amount;
			}
			
			clientIn.clear();
			clientIn.flip();

			// test if some bytes left from last read (e.g. BUFFER_UNDERFLOW)
			if (sTOc.hasRemaining()) {
				
//				sTOc.clear();
				unwrap(sTOc);
				clientIn.flip();
				limit = Math.min(clientIn.limit(), dst.remaining());

				for (int i = 0; i < limit; i++) {
					dst.put(clientIn.get());
					amount++;
				}

				if (res.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW) {
					sTOc.clear();
					sTOc.flip();

					return amount;
				}
			}

			if (!sTOc.hasRemaining()) {
				sTOc.clear();
			} else {
				sTOc.compact();
			}
			
			if (sc.read(sTOc) == -1) {
				System.out.println("sc read errorl!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				sTOc.clear();
				sTOc.flip();
				return -1;
			}
			

			sTOc.flip();	
			unwrap(sTOc);
			sTOc.compact();
			//sTOc.reset();
			//sTOc.rewind();
			sTOc.flip();
			
			limit = Math.min(clientIn.limit(), dst.remaining());
			clientIn.clear();
			for (int i = 0; i < limit; i++) {
				dst.put(clientIn.get());
				amount++;
			}
		}
	
		return amount;
	}

	

    
	 
	public boolean isConnected() {
		return sc.isConnected();
	}



	public SelectableChannel configureBlocking(boolean b) throws IOException {
		return sc.configureBlocking(b);
	}

	public boolean connect(SocketAddress remote) throws IOException {
		return sc.connect(remote);
	}

	public boolean finishConnect() throws IOException {
		return sc.finishConnect();
	}

	public Socket socket() {
		return sc.socket();
	}

	public boolean isInboundDone() {
		return sslEngine.isInboundDone();
	}

	private HandshakeStatus doTask() {
		Runnable runnable;
		while ((runnable = sslEngine.getDelegatedTask()) != null)
		{
			System.out.println("\trunning delegated task...");
			runnable.run();
		}
		HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
		if (hsStatus == HandshakeStatus.NEED_TASK)
		{
			//throw new Exception("handshake shouldn't need additional tasks");
			System.out.println("handshake shouldn't need additional tasks");
		}
		System.out.println("\tnew HandshakeStatus: " + hsStatus);
		
		return hsStatus;
	}
	
	private HandshakeStatus doUnwrap() throws SSLException{
		HandshakeStatus hsStatus;
		do{//do unwrap until the state is change to "NEED_WRAP"
			//SSLEngineResult engineResult = sslEngine.unwrap(sTOc, clientIn);

			hsStatus = doTask();
		}while(hsStatus ==  SSLEngineResult.HandshakeStatus.NEED_UNWRAP && sTOc.remaining()>0);
		System.out.println("\tnew HandshakeStatus: " + hsStatus);
		sTOc.clear();
		return hsStatus;
	}
	
	private HandshakeStatus doWrap() throws SSLException{
		HandshakeStatus hsStatus;
		//SSLEngineResult engineResult = sslEngine.wrap(clientOut, cTOs);

		hsStatus = doTask();
		System.out.println("\tnew HandshakeStatus: " + hsStatus);
		cTOs.flip();
		return hsStatus;
	}
	
	//close an ssl talk, similar to the handshake steps
	@SuppressWarnings("unused")
	private void doSSLClose(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		key.cancel();
		
		try
		{
			sc.configureBlocking(true);
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
		while(handshakeDone) {
			switch(hsStatus) {
			case FINISHED:
				
				break;
			case NEED_TASK:
				hsStatus = doTask();
				break;
			case NEED_UNWRAP:
				sc.read(sTOc);
				sTOc.flip();
				hsStatus = doUnwrap();
				break;
			case NEED_WRAP:
				hsStatus = doWrap();
				sc.write(cTOs);
				cTOs.clear();
				break;
			case NOT_HANDSHAKING:
				handshakeDone = false;
				sc.close();
				break;
			}
		}
	}
}
