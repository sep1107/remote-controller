#!/bin/sh
BASE=/mnt/us/extensions/kindle-remote
exec > "$BASE/server.log" 2>&1
echo "KindleRemote startup"
date
PID=/var/run/kindle-remote.pid
if pidof reader.lua >/dev/null 2>&1; then
    echo "KOReader still running: exit KOReader completely before starting native receiver"
    pidof reader.lua
    exit 1
fi
sh "$BASE/stop.sh"
cd /mnt/us/koreader || exit 1
export LD_LIBRARY_PATH="/mnt/us/koreader/libs${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
nohup ./luajit "$BASE/server.lua" "$BASE" > "$BASE/receiver.log" 2>&1 &
echo $! > "$PID"
sleep 1
if kill -0 "$(cat "$PID")" 2>/dev/null; then
    iptables -I INPUT 1 -p tcp --dport 8080 -j ACCEPT
    iptables -I OUTPUT 1 -p tcp --sport 8080 -j ACCEPT
    echo "Receiver process started; IP addresses:"
    ifconfig wlan0
else
    echo 'Receiver failed; see receiver.log'
    cat "$BASE/receiver.log"
fi
