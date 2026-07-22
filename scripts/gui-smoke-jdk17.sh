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

find_xvfb() {
    if command -v Xvfb >/dev/null 2>&1; then
        command -v Xvfb
        return
    fi
    local candidate
    for candidate in /opt/idea-*/plugins/remote-dev-server/selfcontained/bin/Xvfb; do
        if [[ -x "$candidate" ]]; then
            echo "$candidate"
            return
        fi
    done
    return 1
}

xvfb_pid=""
xvfb_log=""
cleanup() {
    if [[ -n "$xvfb_pid" ]]; then
        kill "$xvfb_pid" >/dev/null 2>&1 || true
        wait "$xvfb_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "$xvfb_log" ]]; then
        rm -f -- "$xvfb_log"
    fi
}
trap cleanup EXIT

if [[ -z "${DISPLAY:-}" ]] || ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
    XVFB_BIN="$(find_xvfb)" || {
        echo "No working display or Xvfb executable was found for the Swing smoke test." >&2
        exit 2
    }
    readonly XVFB_BIN
    readonly XVFB_ROOT="$(cd -- "$(dirname -- "$XVFB_BIN")/.." && pwd)"
    xvfb_library_path="${LD_LIBRARY_PATH:-}"
    if [[ -d "$XVFB_ROOT/lib" ]]; then
        xvfb_library_path="$XVFB_ROOT/lib${xvfb_library_path:+:$xvfb_library_path}"
    fi

    display_number=""
    for candidate_number in {90..119}; do
        if [[ ! -e "/tmp/.X11-unix/X$candidate_number" ]]; then
            display_number="$candidate_number"
            break
        fi
    done
    if [[ -z "$display_number" ]]; then
        echo "No unused local X display was available for the Swing smoke test." >&2
        exit 2
    fi

    export DISPLAY=":$display_number"
    xvfb_log="$(mktemp "${TMPDIR:-/tmp}/jmeter-viewer-xvfb.XXXXXX.log")"
    env LD_LIBRARY_PATH="$xvfb_library_path" \
        "$XVFB_BIN" "$DISPLAY" -screen 0 1280x1024x24 -nolisten tcp \
        >"$xvfb_log" 2>&1 &
    xvfb_pid="$!"

    for _ in {1..50}; do
        if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
            break
        fi
        if ! kill -0 "$xvfb_pid" >/dev/null 2>&1; then
            echo "Xvfb stopped before the Swing smoke test could start:" >&2
            sed -n '1,120p' "$xvfb_log" >&2
            exit 2
        fi
        sleep 0.1
    done
    if ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
        echo "Xvfb did not become ready for the Swing smoke test:" >&2
        sed -n '1,120p' "$xvfb_log" >&2
        exit 2
    fi
fi

export JMETER_GUI_SMOKE=true
cd "$PROJECT_DIR"
./gradlew test \
    --tests '*JMeterSwingClassLoaderTest' \
    "$@" \
    --console=plain
