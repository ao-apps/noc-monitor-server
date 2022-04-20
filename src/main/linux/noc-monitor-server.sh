#!/bin/bash
set -e

# Java
export JDK_HOME='/opt/jdk1.8.0'
#export JDK_HOME='/usr/lib/jvm/java-11-openjdk-amd64'
export JAVA_HOME="${JDK_HOME}"
export PATH="${JDK_HOME}/bin:${PATH}"

cd /mnt/noc
[ -d persistence ] || mkdir -m 700 persistence

STACKSIZE='512k'
if [ "$HOSTNAME" = 'freedom' ]
then
  STACKSIZE='128k'
fi

export DISPLAY=

export CLASSPATH='/opt/noc-monitor-server/classes'
for JAR in /opt/noc-monitor-server/lib/*.jar
do
  export CLASSPATH="${CLASSPATH}:${JAR}"
done

ulimit -n 40960

# -Djava.security.debug=access,failure \

nohup nice java \
  -server \
  -Xms512M \
  -Xmx768M \
  -Xss"$STACKSIZE" \
  -ea:com.aoindustries... \
  -Djava.awt.headless=true \
  -Djava.security.policy='/opt/noc-monitor-server/security.policy.wideopen' \
  -Dsun.net.maxDatagramSockets=4096 \
  -Djavax.net.ssl.keyStore='/opt/noc-monitor-server/keystore' \
  -Djavax.net.ssl.trustStore='/opt/noc-monitor-server/truststore' \
  -Djava.util.logging.config.file='/opt/noc-monitor-server/logging.properties' \
  'com.aoindustries.noc.monitor.server.MonitorServer' \
  4584 0.0.0.0 \
  'monitor.aoindustries.com' >& 'noc-monitor-server.err' &

# monitor.aoindustries.com >& noc-monitor-server.err &
# monitor.aoindustries.com 2>&1 | grep -v "allowed" > noc-monitor-server.err &
