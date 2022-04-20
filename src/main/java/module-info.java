/*
 * noc-monitor-server - Server for Network Operations Center Monitoring.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
module com.aoindustries.noc.monitor.server {
  exports com.aoindustries.noc.monitor.server;
  // Direct
  requires com.aoapps.concurrent; // <groupId>com.aoapps</groupId><artifactId>ao-concurrent</artifactId>
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoindustries.aoserv.client; // <groupId>com.aoindustries</groupId><artifactId>aoserv-client</artifactId>
  requires com.aoindustries.noc.monitor.api; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-api</artifactId>
  requires com.aoindustries.noc.monitor.impl; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-impl</artifactId>
  // Java SE
  requires java.logging;
  requires java.rmi;
}
