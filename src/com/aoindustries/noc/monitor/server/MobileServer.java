/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.server;

import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.NodeSnapshot;
import com.aoindustries.noc.common.RootNode;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.RootNodeImpl;
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

    final private MonitorImpl monitor;
    final private String localAddress;

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
    public void run() {
        while(true) {
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
                    final Socket socket = ss.accept();
                    RootNodeImpl.executorService.submitUnbounded(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    try {
                                        DataInputStream in = new DataInputStream(socket.getInputStream());
                                        try {
                                            // Check authentication
                                            String username = in.readUTF();
                                            String password = in.readUTF();
                                            RootNode rootNode; // Will be null if not authenticated
                                            try {
                                                rootNode = monitor.login(Locale.getDefault(), username, password);
                                            } catch(IOException err) {
                                                logger.log(Level.SEVERE, null, err);
                                                rootNode = null;
                                            }
                                            DataOutputStream out = new DataOutputStream(new GZIPOutputStream(socket.getOutputStream()));
                                            try {
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
                                            } finally {
                                                out.close();
                                            }
                                        } finally {
                                            in.close();
                                        }
                                    } finally {
                                        socket.close();
                                    }
                                } catch(Exception err) {
                                    logger.log(Level.SEVERE, null, err);
                                }
                            }
                        }
                    );
                } finally {
                    ss.close();
                }
            } catch(ThreadDeath TD) {
                throw TD;
            } catch(Throwable T) {
                logger.log(Level.SEVERE, null, T);
                try {
                    Thread.sleep(60000);
                } catch(InterruptedException err) {
                    logger.log(Level.WARNING, null, err);
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
        for(int c=0;c<numChildren;c++) writeNodeTree(out, children.get(c));
    }
}
