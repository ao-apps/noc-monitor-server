/*
 * Copyright 2008-2011 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.server;

import com.aoindustries.noc.common.Monitor;
import com.aoindustries.rmi.RMIClientSocketFactorySSL;
import com.aoindustries.rmi.RMIClientSocketFactoryTCP;
import com.aoindustries.rmi.RMIServerSocketFactorySSL;
import com.aoindustries.rmi.RMIServerSocketFactoryTCP;
import com.aoindustries.noc.monitor.MonitorImpl;
import java.io.File;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
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

    public static void main(String[] args) {
        int port = Monitor.DEFAULT_RMI_SERVER_PORT;
        String listenAddress = null;
        String publicAddress = null;
        if(args.length>=1) port = Integer.parseInt(args[0].trim());
        if(args.length>=2) listenAddress = args[1].trim();
        if(args.length>=3) publicAddress = args[2].trim();
        if(args.length>3) {
            System.err.println("usage: "+MonitorServer.class.getName()+" [port [listen_address [public_address]]]");
            System.err.println("\tport            the server port - defaults to "+Monitor.DEFAULT_RMI_SERVER_PORT);
            System.err.println("\tlisten_address  the local address the server will bind to - the private IP/hostname if behind NAT");
            System.err.println("\tpublic_address  the public address that will reach the server - the external IP/hostname if behind NAT");
            System.exit(1);
            return;
        }
        if(System.getSecurityManager()==null) {
            System.setSecurityManager(new SecurityManager());
        }
        try {
            // Setup the RMI system properties
            if(publicAddress!=null && publicAddress.length()>0) {
                System.setProperty("java.rmi.server.hostname", publicAddress);
            } else if(listenAddress!=null && listenAddress.length()>0) {
                System.setProperty("java.rmi.server.hostname", listenAddress);
            } else {
                System.clearProperty("java.rmi.server.hostname");
            }
            System.setProperty("java.rmi.server.randomIDs", "true");
            System.setProperty("java.rmi.server.useCodebaseOnly", "true");
            System.setProperty("java.rmi.server.disableHttp", "true");
            // System.setProperty("sun.rmi.server.suppressStackTraces", "true");

            RMIClientSocketFactory csf;
            RMIServerSocketFactory ssf;
            if(
                listenAddress!=null
                && (
                    listenAddress.equalsIgnoreCase("localhost")
                    || listenAddress.equalsIgnoreCase("localhost.localdomain")
                    || listenAddress.equals("127.0.0.1")
                    || InetAddress.getByName(listenAddress).isLoopbackAddress()
                )
            ) {
                // Non-SSL for anything loopback
                csf = new RMIClientSocketFactoryTCP();
                ssf = new RMIServerSocketFactoryTCP(listenAddress);
            } else {
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

                // SSL for anything else
                if(listenAddress!=null && listenAddress.length()>0) {
                    csf = new RMIClientSocketFactorySSL();
                    ssf = new RMIServerSocketFactorySSL(listenAddress);
                } else {
                    csf = new RMIClientSocketFactorySSL();
                    ssf = new RMIServerSocketFactorySSL();
                }
            }
            Registry registry = LocateRegistry.createRegistry(port, csf, ssf); //LocateRegistry.getRegistry();
            MonitorImpl monitor = new MonitorImpl(port, csf, ssf);
            registry.rebind("com.aoindustries.noc.monitor.server.MonitorServer", monitor);

            // Auto-login with a top-level account to kick-off the monitoring if a username/password exist in the aoserv-client.properties file
            /*
            String rootUsername = AOServClientConfiguration.getUsername();
            String rootPassword = AOServClientConfiguration.getPassword();
            if(
                rootUsername!=null && (rootUsername=rootUsername.trim()).length()>0
                && rootPassword!=null && (rootPassword=rootPassword.trim()).length()>0
            ) {
                int attemptsLeft = 120;
                while(attemptsLeft>0) {
                    try {
                        monitor.login(ThreadLocale.get(), rootUsername, rootPassword);
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
             */
            
            // Start up the mobile server
            new MobileServer(monitor, listenAddress).start();
        } catch(Exception err) {
            logger.log(Level.SEVERE, null, err);
        }
    }

    private MonitorServer() {
    }
}
