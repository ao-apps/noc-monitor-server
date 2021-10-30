/*
 * noc-monitor-server - Server for Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2017, 2018, 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-server.
 *
 * noc-monitor-server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-server.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.server;

import com.aoapps.lang.validation.ValidationException;
import com.aoindustries.aoserv.client.account.User;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NodeSnapshot;
import com.aoindustries.noc.monitor.common.RootNode;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * The RMI server for NOC monitoring.  If a username and password are available in the aoserv-client.properties file,
 * will auto-login at startup to kick-start the monitoring.
 *
 * @author  AO Industries, Inc.
 */
class MobileServer implements Runnable {

	private static final Logger logger = Logger.getLogger(MobileServer.class.getName());

	private static final int PORT = 4585;

	private final MonitorImpl monitor;
	private final String localAddress;

	private Thread thread;

	MobileServer(MonitorImpl monitor, String localAddress) {
		this.monitor = monitor;
		this.localAddress = localAddress;
	}

	synchronized void start() {
		if(thread==null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	/*private static int getNodeCount(NodeSnapshot snapshot) {
		int total = 1;
		for(NodeSnapshot child : snapshot.getChildren()) total += getNodeCount(child);
		return total;
	}*/

	@Override
	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch", "SleepWhileInLoop"})
	public void run() {
		while(!Thread.currentThread().isInterrupted()) {
			try {
				ServerSocketFactory factory = SSLServerSocketFactory.getDefault();
				ServerSocket ss;
				if(localAddress==null) {
					ss = factory.createServerSocket(PORT, 50);
				} else {
					InetAddress address = InetAddress.getByName(localAddress);
					ss = factory.createServerSocket(PORT, 50, address);
				}
				try {
					while(!Thread.currentThread().isInterrupted()) {
						final Socket socket = ss.accept();
						RootNodeImpl.executors.getUnbounded().submit(() -> {
							try {
								try {
									try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
										// Check authentication
										String username = in.readUTF();
										String password = in.readUTF();
										RootNode rootNode; // Will be null if not authenticated
										try {
											rootNode = monitor.login(
												Locale.getDefault(),
												User.Name.valueOf(username),
												password
											);
										} catch(IOException | ValidationException err) {
											logger.log(Level.SEVERE, null, err);
											rootNode = null;
										}
										try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(socket.getOutputStream()))) {
											if(rootNode==null) {
												// Authentication failed
												out.writeBoolean(false);
											} else {
												// Authentication successful
												out.writeBoolean(true);
												// Write snapshot
												NodeSnapshot snapshot = rootNode.getSnapshot();
												//logger.log(Level.INFO, "RootNode snapshot has a total of "+getNodeCount(snapshot)+" nodes");
												writeNodeTree(out, snapshot);
											}
										}
									}
								} finally {
									socket.close();
								}
							} catch(ThreadDeath td) {
								throw td;
							} catch(Throwable t) {
								logger.log(Level.SEVERE, null, t);
							}
						});
					}
				} finally {
					ss.close();
				}
			} catch(ThreadDeath td) {
				throw td;
			} catch(Throwable t) {
				logger.log(Level.SEVERE, null, t);
				try {
					Thread.sleep(60000);
				} catch(InterruptedException err) {
					logger.log(Level.WARNING, null, err);
					// Restore the interrupted status
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private static void writeNodeTree(DataOutputStream out, NodeSnapshot node) throws IOException {
		out.writeUTF(node.getLabel());
		AlertLevel alertLevel = node.getAlertLevel();
		switch(alertLevel) {
			case NONE      : out.writeByte(0); break;
			case LOW       : out.writeByte(1); break;
			case MEDIUM    : out.writeByte(2); break;
			case HIGH      : out.writeByte(3); break;
			case CRITICAL  : out.writeByte(4); break;
			case UNKNOWN   : out.writeByte(5); break;
			default        : throw new AssertionError("Unexpected value for alertLevel: "+alertLevel);
		}
		String alertMessage = node.getAlertMessage();
		if(alertMessage!=null && alertMessage.length()==0) alertMessage = null;
		if(alertMessage!=null) {
			out.writeBoolean(true);
			out.writeUTF(alertMessage);
		} else {
			out.writeBoolean(false);
		}
		out.writeBoolean(node.getAllowsChildren());
		List<NodeSnapshot> children = node.getChildren();
		int numChildren = children.size();
		if(numChildren>Short.MAX_VALUE) throw new IOException("Too many children for current protocol: "+numChildren);
		out.writeShort(numChildren);
		for(int c = 0; c < numChildren; c++) {
			writeNodeTree(out, children.get(c));
		}
	}
}
