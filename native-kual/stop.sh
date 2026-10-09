#!/bin/sh
PID=/var/run/kindle-remote.pid
if [ -r "$PID" ]; then
    pid=$(cat "$PID")
    case "$pid" in ''|*[!0-9]*) ;; *)
        if [ -r "/proc/$pid/cmdline" ] && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q '/mnt/us/extensions/kindle-remote/server.lua'; then
            kill "$pid" 2>/dev/null
        fi ;;
    esac
    : > "$PID"
fi
iptables -D INPUT -p tcp --dport 8080 -j ACCEPT 2>/dev/null
iptables -D OUTPUT -p tcp --sport 8080 -j ACCEPT 2>/dev/null
