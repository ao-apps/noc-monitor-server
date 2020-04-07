@echo off

rem
rem Requires Windows NT
rem
if "%OS%" == "Windows_NT" goto nt
    echo This script only works with NT-based versions of Windows.
    goto :eof
:nt
setlocal

rem
rem Find installation directory
rem
set "EXE_DIR=%~dp0%"

rem
rem Make persistence directory if missing
rem
IF NOT EXIST "%TEMP%\noc-monitor-server" md "%TEMP%\noc-monitor-server"
cd "%TEMP%\noc-monitor-server"

rem
rem Build CLASSPATH
rem
set "CLASSPATH=%EXE_DIR%\noc-monitor-server.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\aocode-public.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\aoserv-client.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\dnsjava-2.0.7.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\mysql-connector-java-3.1.12-bin.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-common.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-monitor-portmon.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-monitor.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\ostermillerutils_1_06_00.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\postgresql-8.3-605.jdbc3.jar"

rem -Djava.security.debug=access,failure

java ^
    -server ^
    -Xms512M ^
    -Xmx768m ^
    -classpath "%CLASSPATH%" ^
    -ea:com.aoindustries... ^
    -Djava.security.policy="%EXE_DIR%\security.policy.wideopen" ^
    -Djavax.net.ssl.keyStore="%EXE_DIR%\keystore" ^
    -Djavax.net.ssl.trustStore="%EXE_DIR%\truststore" ^
    -Djava.util.logging.config.file="%EXE_DIR%\logging.properties" ^
    com.aoindustries.noc.monitor.server.MonitorServer ^
    4584 0.0.0.0 ^
    192.168.1.36 > "%TEMP%\noc-monitor-server\noc-monitor-server.err"

rem Fuck you, Windows 1986-compatible bullshit batch files. lol   I saw :P