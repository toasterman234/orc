#!/usr/bin/env bash
# Manage the SSH local-forward that makes the Mac mini's CLIProxyAPI
# (127.0.0.1:8317 on the Mac) reachable from this VM at 127.0.0.1:18317.
#
# Why: the VM cannot reach the Tailscale Serve URL for cliproxy directly
# (curl timed out, exit 28). The forward rides the same ts-connect
# ProxyCommand route that ~/workspace/bin/mac-ssh.sh uses for SSH.
#
# Usage: cliproxy-tunnel.sh {start|stop|status}
set -u
PORT=18317
RUNTIME="$(cd "$(dirname "$0")/../consumer/runtime" && pwd)"
PIDFILE="$RUNTIME/cliproxy-tunnel.pid"
LOG="$RUNTIME/cliproxy-tunnel.log"
MAC="bencharney@100.71.118.10"

up() { curl -s --noproxy '*' -m 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/v1/models" 2>/dev/null | grep -qE '^(200|401)$'; }

case "${1:-status}" in
  start)
    if up; then echo "tunnel already up (port $PORT answers)"; exit 0; fi
    mkdir -p "$RUNTIME"
    nohup ssh -i /home/hatch/.ssh/id_ed25519 \
      -o BatchMode=yes -o ConnectTimeout=30 \
      -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
      -o StrictHostKeyChecking=yes \
      -o UserKnownHostsFile=/home/hatch/.ssh/known_hosts \
      -o "ProxyCommand=python3 /home/hatch/workspace/bin/ts-connect-proxy.py %h %p" \
      -o ExitOnForwardFailure=yes \
      -N -L "$PORT:127.0.0.1:8317" "$MAC" >>"$LOG" 2>&1 &
    echo $! > "$PIDFILE"
    for i in $(seq 1 20); do up && { echo "tunnel up (pid $(cat "$PIDFILE"))"; exit 0; }; sleep 1; done
    echo "tunnel did not come up; see $LOG"; exit 1;;
  stop)
    if [ -f "$PIDFILE" ]; then kill "$(cat "$PIDFILE")" 2>/dev/null; rm -f "$PIDFILE"; echo "tunnel stopped"; else echo "no pidfile — nothing to stop"; fi;;
  status)
    if up; then echo "up: http://127.0.0.1:$PORT/v1 answers"; else echo "down"; exit 1; fi;;
  *) echo "usage: $0 {start|stop|status}"; exit 2;;
esac
