/*
 * noc-monitor-server - Server for Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2017, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with noc-monitor-server.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.server;

import com.aoapps.hodgepodge.rmi.RMIClientSocketFactorySSL;
import com.aoapps.hodgepodge.rmi.RMIServerSocketFactorySSL;
import com.aoindustries.aoserv.client.AOServClientConfiguration;
import com.aoindustries.aoserv.client.account.User;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.common.Monitor;
import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The RMI server for NOC monitoring.  If a username and password are available in the aoserv-client.properties file,
 * will auto-login at startup to kick-start the monitoring.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Implement serialization filters to prevent malicious loading of new classes
public final class MonitorServer {

	/** Make no instances. */
	private MonitorServer() {throw new AssertionError();}

	private static final Logger logger = Logger.getLogger(MonitorServer.class.getName());

	@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SleepWhileInLoop", "UseSpecificCatch", "TooBroadCatch"})
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

			// SSL for everything going over the network
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

			RMIClientSocketFactory csf;
			RMIServerSocketFactory ssf;
			if(listenAddress!=null && listenAddress.length()>0) {
				csf = new RMIClientSocketFactorySSL();
				ssf = new RMIServerSocketFactorySSL(listenAddress);
			} else {
				csf = new RMIClientSocketFactorySSL();
				ssf = new RMIServerSocketFactorySSL();
			}
			Registry registry = LocateRegistry.createRegistry(port, csf, ssf); //LocateRegistry.getRegistry();
			MonitorImpl monitor = new MonitorImpl(port, csf, ssf);
			registry.rebind("com.aoindustries.noc.monitor.server.MonitorServer", monitor);

			// Auto-login with a top-level account to kick-off the monitoring if a username/password exist in the aoserv-client.properties file
			User.Name rootUsername = AOServClientConfiguration.getUsername();
			String rootPassword = AOServClientConfiguration.getPassword();
			if(
				rootUsername != null
				&& rootPassword != null && (rootPassword = rootPassword.trim()).length() > 0
			) {
				int attemptsLeft = 120;
				while(attemptsLeft > 0) {
					try {
						monitor.login(Locale.getDefault(), rootUsername, rootPassword);
						break;
					} catch(ThreadDeath td) {
						throw td;
					} catch(Throwable t) {
						logger.log(Level.SEVERE, null, t);
						Thread.sleep(60000);
					}
					attemptsLeft--;
				}
			}

			// Start up the mobile server
			new MobileServer(monitor, listenAddress).start();
		} catch(InterruptedException e) {
			logger.log(Level.WARNING, null, e);
			// Restore the interrupted status
			Thread.currentThread().interrupt();
		} catch(ThreadDeath td) {
			throw td;
		} catch(Throwable t) {
			logger.log(Level.SEVERE, null, t);
		}
	}
}
