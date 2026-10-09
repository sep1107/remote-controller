#!/bin/sh
BASE=/mnt/us/extensions/kindle-remote
resume=no
if [ -r /var/run/kindle-remote.pid ]; then
    pid=$(cat /var/run/kindle-remote.pid)
    case "$pid" in ''|*[!0-9]*) ;; *)
        if [ -r "/proc/$pid/cmdline" ] && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q '/mnt/us/extensions/kindle-remote/server.lua'; then
            resume=yes
            sh "$BASE/stop.sh"
        fi ;;
    esac
fi
sh /mnt/us/koreader/koreader.sh "$@"
result=$?
if [ "$resume" = yes ]; then sh "$BASE/start.sh"; fi
exit "$result"
