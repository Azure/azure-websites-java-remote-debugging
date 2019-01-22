package org.azure.waws;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.apache.commons.logging.*;

/**
 * @date 3/15/2014
 * @author ranjithr (Ranjith Mukkai Ramachandra)
 * @class DebuggerSession (Java on Azure Web Sites debug session)
 * 
 */

@WebSocket
public class DebugSession implements Runnable {

	ServerSocket server = null;
	WebSocketClient webSocketClient = null;
	Socket debugSessionDebugger = null;
	private CountDownLatch closeLatch = null;
	private Session webSocketSession = null;
	private DebuggerSocketReader debuggerSocketReader = null;
	Semaphore semaphore = null;
	boolean debuggerConnected = false;
	private final int MAX_BUFFER_SIZE = 1024;
	private final int EOF = -1;
	private Exception lastException = null;
	private ConnectionInfo connectionInfo = null;
	private Log log = null;

	public DebugSession(ConnectionInfo connectionInfo) {
		this.connectionInfo = connectionInfo;
		this.log = LogFactory.getLog(DebugSession.class);
	}
	
	/**
	 * Constructor for NON-SSL connections.
	 * 
	 * @param localPort
	 * @param siteUrl
	 */
	public DebugSession(int localPort, String siteUrl)
	{
		this.connectionInfo = new ConnectionInfo();
		this.connectionInfo.setLocalPort(localPort);
		this.connectionInfo.setSiteUrl(siteUrl);
		this.log = LogFactory.getLog(DebugSession.class);
	}
	
	/**
	 * Constructor for NON-SSL connections.
	 * 
	 * @param localPort
	 * @param siteUrl
	 */
	public DebugSession(int localPort, String siteUrl, String affinity)
	{
		this.connectionInfo = new ConnectionInfo();
		this.connectionInfo.setLocalPort(localPort);
		this.connectionInfo.setSiteUrl(siteUrl);
		this.connectionInfo.setAffinity(affinity);
		this.log = LogFactory.getLog(DebugSession.class);
	}
	
	/**
	 * Constructor for SSL connections.
	 * 
	 * @param localPort
	 * @param siteUrl
	 * @param userName
	 * @param password
	 */
	public DebugSession(int localPort, String siteUrl, String userName, String password)
	{
		this.connectionInfo = new ConnectionInfo();
		this.connectionInfo.setLocalPort(localPort);
		this.connectionInfo.setSiteUrl(siteUrl);
		this.connectionInfo.setUserName(userName);
		this.connectionInfo.setPassword(password);
		this.log = LogFactory.getLog(DebugSession.class);
	}
	
	/**
	 * Constructor for SSL connections.
	 * 
	 * @param localPort
	 * @param siteUrl
	 * @param userName
	 * @param password
	 */
	public DebugSession(int localPort, String siteUrl, String userName, String password, String affinity)
	{
		this.connectionInfo = new ConnectionInfo();
		this.connectionInfo.setLocalPort(localPort);
		this.connectionInfo.setSiteUrl(siteUrl);
		this.connectionInfo.setUserName(userName);
		this.connectionInfo.setPassword(password);
		this.connectionInfo.setAffinity(affinity);
		this.log = LogFactory.getLog(DebugSession.class);
	}

	private void Initialize() throws Exception {
		if(connectionInfo == null) 
		{
			throw new Exception("Connection info is required.");
		}
		if (semaphore == null) {
			semaphore = new Semaphore(0);
		}
		if (server == null) {
			server = new ServerSocket(this.connectionInfo.getLocalPort());
		}
		if (closeLatch == null) {
			closeLatch = new CountDownLatch(1);
		}
	}

	public void Terminate() {
		log.info("Cleaning up debug session resources ...");
		if (this.webSocketSession != null) {
			this.webSocketSession.close();
			try {
				this.awaitClose(5000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
			} finally {
				closeLatch = null;
			}
			log.debug("WebSocket session closed.");
			this.webSocketSession = null;
		}

		if (webSocketClient != null) {
			try {
				webSocketClient.stop();
			} catch (Exception e) {
			}
			log.debug("WebSocketClient stopped.");
			webSocketClient = null;
		}

		if (debuggerSocketReader != null) {
			debuggerSocketReader.Stop();
			log.debug("DebuggerSocketReader thread closed.");
			debuggerSocketReader = null;
		}

		if (debugSessionDebugger != null) {
			try {
				debugSessionDebugger.close();
			} catch (IOException e) { /* nothing we can do about this */
			} finally {
				log.debug("DebuggerSocket closed.");
				debugSessionDebugger = null;
			}
		}

		if (server != null) {
			try {
				server.close();
			} catch (IOException e) {
			} finally {
				log.debug("ServerSocket closed.");
				server = null;
			}
		}
		
		connectionInfo.setPassword("");
		connectionInfo = null;
		semaphore = null;
		debuggerConnected = false;
		log.info("Cleanup complete.");
	}

	/**
	 * DebuggerSocketReader thread reads data from the debugger
	 * and writes it to the websocket.
	 */
	public class DebuggerSocketReader extends Thread {
		Socket debugger;
		RemoteEndpoint remoteEndPoint;
		volatile boolean stopped = false;

		DebuggerSocketReader(Socket debugger, RemoteEndpoint remoteEndPoint) {
			this.debugger = debugger;
			this.remoteEndPoint = remoteEndPoint;
			stopped = false;
		}

		@Override
		public void run() {
			int bytesRead = 0;
			byte[] data = new byte[MAX_BUFFER_SIZE];
			Thread.currentThread()
					.setName(DebuggerSocketReader.class.getName());
			try {
				while (!stopped
						&& (bytesRead = debugger.getInputStream().read(data, 0,
								MAX_BUFFER_SIZE)) != EOF) {
					this.remoteEndPoint.sendBytesByFuture(ByteBuffer.wrap(data, 0, bytesRead));
				}
				semaphore.release();
			} catch (IOException e) {
				lastException = e;
				if (semaphore != null) {
					semaphore.release();
				}
			}
		}

		public void Stop() {
			stopped = true;
		}
	}

	public boolean awaitClose(int duration, TimeUnit unit)
			throws InterruptedException {
		return this.closeLatch.await(duration, unit);
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) throws IOException {
		log.info("Remote closed connection with StatusCode: " + statusCode + " reason: "
				+ (reason == null ? "Unknown" : reason));
		semaphore.release();
		this.closeLatch.countDown();
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		log.info("Successfully connected to remote.");
		this.webSocketSession = session;
		try {
			// init debuggerSocketReader
			debuggerSocketReader = new DebuggerSocketReader(debugSessionDebugger, 
															webSocketSession.getRemote());
			debuggerSocketReader.start();
		} catch (Exception e) {
			lastException = e;
			semaphore.release();
		}
	}

	@OnWebSocketMessage
	public void onMessage(byte[] b, int offset, int length) {
		try {
			
			//
			// on data receive from websocket, need to send data to 
			// the debugger
			//
			
			if (length > 0) {
				debugSessionDebugger.getOutputStream().write(b, offset, length);
			}
		} catch (Exception e) {
			lastException = e;
			semaphore.release();
		}
	}
	
	@OnWebSocketError
	public void onError(Session session, Throwable error)
	{
		lastException = new Exception("Error communicating with remote. " + error.getMessage());
		error.printStackTrace();
		semaphore.release();
	}

	/**
	 * startSession
	 * 	starts a debug session - waits for debugger to connect on the port specified.
	 *  once debugger connects, it establishes connection to the remote scm site over
	 *  websockets and forwards packets from debugger to the scm site.
	 * @throws Exception
	 */
	public void startSession() throws Exception {
		try {
			if (debuggerConnected) {
				log.error("Debugger already connected ...");
				return;
			}
			
			Initialize();
			
			log.info("Waiting for debugger to connect on " + this.connectionInfo.getLocalPort() + " ...");
			debugSessionDebugger = server.accept();
			log.info("Debugger connected.");
			debuggerConnected = true;
			
			SslContextFactory sslFactory = null;
			if(connectionInfo.isSsl())
			{
				sslFactory = new SslContextFactory();
				sslFactory.setTrustAll(true);
				webSocketClient = new WebSocketClient(sslFactory);
				sslFactory.start();
			}
			else
			{
				webSocketClient = new WebSocketClient();
			}
			
			webSocketClient.start();
			
			String scheme = this.connectionInfo.isSsl() ? "wss://" : "ws://";
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			URI uri = new URI(scheme + this.connectionInfo.getSiteUrl() + "/DebugSiteExtension/JavaDebugSiteExtension.ashx");
			request.setRequestURI(uri);
			
			log.info("Trying to establish connection with remote: " + uri.toString());
			
			if(connectionInfo.useBasicAuth())
			{
				String userNamePassword = String.format("%s:%s", connectionInfo.getUserName(), connectionInfo.getPassword());
				String authHeaderValue = String.format("Basic %s", new String(Base64.encodeBase64(userNamePassword.getBytes())));
				request.setHeader("Authorization", authHeaderValue);
			}
			
			if(!connectionInfo.getAffinity().isEmpty())
			{
				log.info("Using affinity cookie: '" + connectionInfo.getAffinity() + "'");
				request.setHeader("Cookie", "ARRAffinity="+connectionInfo.getAffinity()+";");				
			}
			
			webSocketClient.setConnectTimeout(15000);
			
			webSocketClient.connect(this, uri, request);

			// wait for Threads to an Exception to happen in either
			// socket reader thread OR webSocket threads.
			semaphore.acquire();

			if (lastException != null) {
				lastException.printStackTrace();
				throw lastException;
			}
		} catch (Exception e) {
			log.error(e);
			e.printStackTrace();
			throw e;
		} finally {
			Terminate();
		}
	}

	@Override
	public void run() {
		try {
			Thread.currentThread().setName(DebugSession.class.getName());
			startSession();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}