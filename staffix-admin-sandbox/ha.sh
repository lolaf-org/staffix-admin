#!/bin/sh
# The venue-y group: a primos witness in a container and three engines, each in the background logging to target/ha/logs.
#
#   ha.sh start                       the witness and the three nodes, afresh: stores and elections forgotten
#   ha.sh start 1|2|3|witness         one process again, carrying on from its stores
#   ha.sh stop 1|2|3|leader|follower|witness|all [-9]  SIGTERM stops cleanly, -9 kills as a crash would; follower
#                                     is the first follower found
#   ha.sh status

cd "$(dirname "$0")" || exit 1

HA=target/ha
JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
WITNESS_IMAGE=ghcr.io/lolaf-org/primos-witness:0.9.0
WITNESS_CONTAINER=staffix-admin-sandbox-witness
NODES="1 2 3"

running() {
  if [ "$1" = witness ]; then
    docker container inspect "$WITNESS_CONTAINER" > /dev/null 2>&1
  else
    [ -f "$HA/$1.pid" ] && kill -0 "$(cat "$HA/$1.pid")" 2>/dev/null
  fi
}

rotate_log() {
  mkdir -p "$HA/logs"
  [ -f "$HA/logs/$1.log" ] && mv "$HA/logs/$1.log" "$HA/logs/$1.previous.log"
}

launch() {
  name=$1
  shift
  if running "$name"; then
    echo "$name is already running, pid $(cat "$HA/$name.pid")" >&2
    return 1
  fi
  rotate_log "$name"
  # shellcheck disable=SC2046
  nohup "$JAVA_CMD" $(cat ../.mvn/jvm.config) -XX:ErrorFile="$HA/logs/hs_err_%p.log" -cp "target/classes:$(cat target/classpath.txt)" "$@" > "$HA/logs/$name.log" 2>&1 &
  echo $! > "$HA/$name.pid"
  echo "$name started, pid $!, logging to $HA/logs/$name.log"
}

start_witness() {
  if running witness; then
    echo "witness is already running, container $WITNESS_CONTAINER" >&2
    return 1
  fi
  chmod -R go-rwx witness
  rotate_log witness
  mkdir -p "$HA/witness"
  # --user: the votes in target/ha/witness belong to whoever runs the sandbox, so ha.sh start and mvn clean can remove them.
  nohup docker run --rm --name "$WITNESS_CONTAINER" \
    --user "$(id -u):$(id -g)" \
    -p 127.0.0.1:7340:7340/udp \
    -v "$PWD/$HA/witness:/var/lib/primos-witness" \
    -v "$PWD/witness:/etc/primos-witness:ro" \
    --read-only --tmpfs /tmp \
    --memory 512m \
    --stop-timeout 30 \
    "$WITNESS_IMAGE" > "$HA/logs/witness.log" 2>&1 &
  echo "witness started, container $WITNESS_CONTAINER, logging to $HA/logs/witness.log"
}

start_node() {
  launch "venue-y-$1" -DadminApiPort="869$1" org.lolaf.staffix.admin.sandbox.SandboxHaNode "$1"
}

stop_witness() {
  if ! running witness; then
    echo "witness is not running" >&2
    return
  fi
  if [ "$1" = "-9" ]; then
    docker kill "$WITNESS_CONTAINER" > /dev/null
  else
    docker stop "$WITNESS_CONTAINER" > /dev/null
  fi
  while running witness; do
    sleep 0.2
  done
  echo "witness stopped"
}

stop_process() {
  if ! running "$1"; then
    echo "$1 is not running" >&2
    return
  fi
  pid=$(cat "$HA/$1.pid")
  kill ${2:+"$2"} "$pid"
  while kill -0 "$pid" 2>/dev/null; do
    sleep 0.2
  done
  rm -f "$HA/$1.pid"
  echo "$1 stopped"
}

role() {
  if ! running "venue-y-$1"; then
    echo STOPPED
    return
  fi
  sed -n "s/.* Node $1 is now \([A-Z]*\) at epoch.*/\1/p" "$HA/logs/venue-y-$1.log" | tail -1 | grep . || echo STARTING
}

node_with_role() {
  for id in $NODES; do
    if [ "$(role $id)" = "$1" ]; then
      echo "$id"
      return
    fi
  done
  echo "no node is $1 now" >&2
  exit 1
}

if [ ! -f target/classpath.txt ]; then
  echo "target/classpath.txt is missing: build the sandbox first" >&2
  exit 1
fi

case "$1 $2" in
  "start ")
    for id in $NODES; do
      if running "venue-y-$id"; then
        echo "the group is running: ha.sh stop all first" >&2
        exit 1
      fi
    done
    if running witness; then
      echo "the group is running: ha.sh stop all first" >&2
      exit 1
    fi
    rm -rf "$HA"
    start_witness
    for id in $NODES; do
      start_node "$id"
    done
    ;;
  "start witness") start_witness ;;
  "start 1" | "start 2" | "start 3") start_node "$2" ;;
  "stop all")
    for id in $NODES; do
      stop_process "venue-y-$id" "$3"
    done
    stop_witness "$3"
    ;;
  "stop witness") stop_witness "$3" ;;
  "stop 1" | "stop 2" | "stop 3") stop_process "venue-y-$2" "$3" ;;
  "stop leader")
    node=$(node_with_role LEADER) || exit 1
    stop_process "venue-y-$node" "$3"
    ;;
  "stop follower")
    node=$(node_with_role FOLLOWER) || exit 1
    stop_process "venue-y-$node" "$3"
    ;;
  "status "*)
    for id in $NODES; do
      echo "venue-y-$id: $(role "$id")"
    done
    running witness && echo "witness: RUNNING" || echo "witness: STOPPED"
    ;;
  *)
    sed -n '2,9s/^# \{0,1\}//p' "$0" >&2
    exit 1
    ;;
esac
