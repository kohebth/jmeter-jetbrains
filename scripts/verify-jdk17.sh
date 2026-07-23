#!/usr/bin/env bash
set -euo pipefail

readonly JMETER_VIEWER_JAVA_HOME="${JMETER_VIEWER_JAVA_HOME:-/home/duync/toolchains/jdk-17.0.4.1}"
export JAVA_HOME="$JMETER_VIEWER_JAVA_HOME"
export PATH="$JMETER_VIEWER_JAVA_HOME/bin:$PATH"

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "JDK 17 was not found at $JAVA_HOME. Set JMETER_VIEWER_JAVA_HOME to a JDK 17 home." >&2
    exit 2
fi

cd "$PROJECT_DIR"
./gradlew test verifyPluginRuntime "$@" --console=plain
"$SCRIPT_DIR/gui-smoke-jdk17.sh"
exec "$SCRIPT_DIR/ide-ui-smoke-jdk17.sh"
