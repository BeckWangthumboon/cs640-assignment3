#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./run_devices.sh start <topology-file> [--log-dir <dir>] [--kill-first]
  ./run_devices.sh stop

Examples:
  ./run_devices.sh start topos/triangle_rt.topo --kill-first
  ./run_devices.sh start topos/triangle_with_sw.topo --log-dir logs
  ./run_devices.sh stop

Notes:
  - For Lab 3, routers are started in RIP mode without -r.
  - Run Mininet first so arp_cache exists.
  - run_mininet.py may still generate rtable.rX files, but they are ignored.
  - Logs are written to <log-dir>/<device>.log.
EOF
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

cmd="$1"
shift

kill_existing() {
  pkill -f 'VirtualNetwork.jar -v' 2>/dev/null || true
}

case "$cmd" in
  stop)
    kill_existing
    echo "Stopped VirtualNetwork.jar processes."
    exit 0
    ;;
  start)
    if [[ $# -lt 1 ]]; then
      usage
      exit 1
    fi

    topo="$1"
    shift

    log_dir="logs"
    kill_first=false

    while [[ $# -gt 0 ]]; do
      case "$1" in
        --log-dir)
          log_dir="${2:-}"
          if [[ -z "$log_dir" ]]; then
            echo "Missing value for --log-dir"
            exit 1
          fi
          shift 2
          ;;
        --kill-first)
          kill_first=true
          shift
          ;;
        *)
          echo "Unknown argument: $1"
          usage
          exit 1
          ;;
      esac
    done

    if [[ ! -f "$topo" ]]; then
      echo "Topology file not found: $topo"
      exit 1
    fi
    if [[ ! -f "VirtualNetwork.jar" ]]; then
      echo "VirtualNetwork.jar not found. Run: ant"
      exit 1
    fi

    if [[ "$kill_first" == true ]]; then
      kill_existing
      sleep 1
    fi

    mkdir -p "$log_dir"

    mapfile -t switches < <(awk '$1=="switch"{print $2}' "$topo")
    mapfile -t routers  < <(awk '$1=="router"{print $2}' "$topo")

    if [[ ${#switches[@]} -eq 0 && ${#routers[@]} -eq 0 ]]; then
      echo "No switches/routers found in $topo"
      exit 1
    fi

    if [[ ${#routers[@]} -gt 0 && ! -f "arp_cache" ]]; then
      echo "Missing arp_cache. Start Mininet first."
      exit 1
    fi

    pids=()
    for s in "${switches[@]}"; do
      java -jar VirtualNetwork.jar -v "$s" > "$log_dir/$s.log" 2>&1 &
      pids+=("$!")
      echo "Started switch $s (pid=$!, log=$log_dir/$s.log)"
    done
    for r in "${routers[@]}"; do
      java -jar VirtualNetwork.jar -v "$r" -a arp_cache > "$log_dir/$r.log" 2>&1 &
      pids+=("$!")
      echo "Started router $r (pid=$!, log=$log_dir/$r.log)"
    done

    echo
    echo "Active jobs:"
    jobs -l || true
    echo
    echo "Tail logs:"
    echo "  tail -f $log_dir/*.log"
    echo "Stop all:"
    echo "  ./run_devices.sh stop"
    ;;
  *)
    usage
    exit 1
    ;;
esac
