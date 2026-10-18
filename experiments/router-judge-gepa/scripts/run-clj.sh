#!/usr/bin/env bash
# Run Clojure for the router-loop consumer with the experiment environment:
#  - Temurin 21 + Clojure CLI from ~/workspace/tools
#  - JVM user.home pinned to /home/hatch (the agent JVM otherwise sees /root,
#    which hides ~/.m2, ~/.gitlibs and model caches — see PREFLIGHT §6)
#  - LMDB module-access flags (from ORC's scripts/nrepl.sh)
#  - ColBERT model dir pinned to the curl-seeded cache under consumer/runtime
#  - CLIPROXY_API_KEY / CLIPROXY_BASE_URL sourced from the gitignored env file
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="$HOME/workspace/tools/jdk21"
export PATH="$JAVA_HOME/bin:$HOME/workspace/tools/clojure/bin:$PATH"
set -a; . "$HERE/data/cliproxy.env"; set +a
export HTTP_BRIDGE_DEBUG="${HTTP_BRIDGE_DEBUG:-}"
cd "$HERE/consumer"
exec clojure \
  -J-Duser.home=/home/hatch \
  -J-Dai.djl.offline=true \
  -J--add-opens=java.base/java.nio=ALL-UNNAMED \
  -J--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  "-J-Dcolbert.model.path=$HERE/consumer/runtime/colbert-model" \
  "$@"
