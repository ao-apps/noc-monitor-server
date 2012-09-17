/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.server;

import com.aoindustries.aoserv.client.AOServClientConfiguration;
import com.aoindustries.noc.monitor.common.Monitor;
import com.aoindustries.noc.monitor.common.MonitoringPoint;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.mobile.server.MobileServer;
import com.aoindustries.noc.monitor.rmi.server.RmiServerMonitor;
import java.net.InetAddress;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The RMI server for NOC monitoring.  If a username and password are available in the aoserv-client.properties file,
 * will auto-login at startup to kick-start the monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class MonitorServer {

    private static final Logger logger = Logger.getLogger(MonitorServer.class.getName());

    /**
     * Keeps a reference to factory to avoid complete garbage collection.
     * @{link http://stackoverflow.com/questions/645208/java-rmi-nosuchobjectexception-no-such-object-in-table}
     */
    private static RmiServerMonitor monitorServer;

    private static MobileServer mobileServer;

    public static void main(String[] args) {
        int port = Monitor.DEFAULT_RMI_SERVER_PORT;
        String listenAddress = null;
        String publicAddress = null;
        float latitude = 0;
        float longitude = 0;
        if(args.length>=1) port = Integer.parseInt(args[0].trim());
        if(args.length>=2) listenAddress = args[1].trim();
        if(args.length>=3) publicAddress = args[2].trim();
        if(args.length>=5) {
            latitude = Float.parseFloat(args[3]);
            longitude = Float.parseFloat(args[4]);
        }
        if(args.length==4 || args.length>5) {
            System.err.println("usage: "+MonitorServer.class.getName()+" [port [listen_address [public_address [latitude longitude]]]]");
            System.err.println("\tport                    the server port - defaults to "+Monitor.DEFAULT_RMI_SERVER_PORT);
            System.err.println("\tlisten_address          the local address the server will bind to - the private IP/hostname if behind NAT");
            System.err.println("\tpublic_address          the public address that will reach the server - the external IP/hostname if behind NAT");
            System.err.println("\tlatitude and longitude  the geographical position of the server");
            System.exit(1);
            return;
        }
        if(System.getSecurityManager()==null) {
            System.setSecurityManager(new SecurityManager());
        }
        try {
            // SSL
            /* Will be set by scripts since each server has its own key
            if(System.getProperty("javax.net.ssl.keyStorePassword")==null) {
                System.setProperty(
                    "javax.net.ssl.keyStorePassword",
                    "changeit"
                );
            }
            if(System.getProperty("javax.net.ssl.keyStore")==null) {
                System.setProperty(
                    "javax.net.ssl.keyStore",
                    System.getProperty("user.home")+File.separatorChar+".keystore"
                );
            }
             */

            // Start the RMI server
            System.out.print("Starting MonitorServer: ");
            boolean done = false;
            while(!done) {
                try {
                    Monitor monitor = new MonitorImpl(
                        new MonitoringPoint(
                            publicAddress!=null ? publicAddress
                            : listenAddress!=null ? listenAddress
                            : InetAddress.getLocalHost().getCanonicalHostName(),
                            latitude,
                            longitude
                        )
                    );
                    // TODO: threadlocale wrapper
                    // TODO: trace wrapper
                    // TODO: other wrappers like on aoserv-master (AOServ 2)
                    monitorServer = RmiServerMonitor.getInstance(monitor, publicAddress, listenAddress, port);
                    done = true;
                    System.out.println("Done");
                } catch(Exception err) {
                    logger.log(Level.SEVERE, null, err);
                    try {
                        Thread.sleep(10000);
                    } catch(InterruptedException err2) {
                        logger.log(Level.WARNING, null, err2);
                    }
                }
            }

            // Start up the mobile server
            System.out.print("Starting MobileServer: ");
            done = false;
            while(!done) {
                try {
                    mobileServer = new MobileServer(monitorServer, listenAddress);
                    mobileServer.start();
                    done = true;
                    System.out.println("Done");
                } catch(Exception err) {
                    logger.log(Level.SEVERE, null, err);
                    try {
                        Thread.sleep(10000);
                    } catch(InterruptedException err2) {
                        logger.log(Level.WARNING, null, err2);
                    }
                }
            }

            // Auto-login with a top-level account to kick-off the monitoring if a username/password exist in the aoserv-client.properties file
            String rootUsername = AOServClientConfiguration.getUsername();
            if(rootUsername!=null) rootUsername = rootUsername.trim();
            String rootPassword = AOServClientConfiguration.getPassword();
            if(
                rootUsername!=null && (rootUsername=rootUsername.trim()).length()>0
                && rootPassword!=null && (rootPassword=rootPassword.trim()).length()>0
            ) {
                int attemptsLeft = 120;
                while(attemptsLeft>0) {
                    try {
                        monitorServer.login(Locale.getDefault(), rootUsername, rootPassword);
                        break;
                    } catch(Exception err) {
                        logger.log(Level.SEVERE, null, err);
                        try {
                            Thread.sleep(60000);
                        } catch(InterruptedException err2) {
                            logger.log(Level.WARNING, null, err2);
                        }
                    }
                    attemptsLeft--;
                }
            }

            // Avoid bug: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6597112
            try {
                Thread.sleep(300000); // Wait five minutes before losing reference to factory.
            } catch(InterruptedException err) {
                logger.log(Level.WARNING, null, err);
            }
        } catch(Exception err) {
            logger.log(Level.SEVERE, null, err);
        }
    }

    /**
     * Make no instances.
     */
    private MonitorServer() {
    }
}
