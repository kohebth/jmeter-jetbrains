#!/usr/bin/env bash
set -euo pipefail

readonly JMETER_VIEWER_JAVA_HOME="${JMETER_VIEWER_JAVA_HOME:-/home/duync/toolchains/jdk-17.0.4.1}"
export JAVA_HOME="$JMETER_VIEWER_JAVA_HOME"
export PATH="$JMETER_VIEWER_JAVA_HOME/bin:$PATH"

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
source "$SCRIPT_DIR/lib/xvfb.sh"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "JDK 17 was not found at $JAVA_HOME. Set JMETER_VIEWER_JAVA_HOME to a JDK 17 home." >&2
    exit 2
fi

cleanup() {
    jmeter_viewer_stop_xvfb
}
trap cleanup EXIT

jmeter_viewer_start_xvfb "Swing smoke test"

export JMETER_GUI_SMOKE=true
cd "$PROJECT_DIR"
./gradlew test \
    --tests '*JMeterSwingClassLoaderTest' \
    "$@" \
    --console=plain
