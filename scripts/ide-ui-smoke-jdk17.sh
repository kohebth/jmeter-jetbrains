#!/usr/bin/env bash
set -euo pipefail

readonly JMETER_VIEWER_JAVA_HOME="${JMETER_VIEWER_JAVA_HOME:-/home/duync/toolchains/jdk-17.0.4.1}"
export JAVA_HOME="$JMETER_VIEWER_JAVA_HOME"
export PATH="$JMETER_VIEWER_JAVA_HOME/bin:$PATH"

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly REPORT_DIR="$PROJECT_DIR/build/reports/ide-ui-smoke"
readonly IDE_LAUNCH_LOG="$REPORT_DIR/ide-launch.log"
readonly IDE_RUNTIME_LOG="$PROJECT_DIR/build/idea-sandbox/system-uiTest/log/idea.log"
source "$SCRIPT_DIR/lib/xvfb.sh"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "JDK 17 was not found at $JAVA_HOME. Set JMETER_VIEWER_JAVA_HOME to a JDK 17 home." >&2
    exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required to wait for the local Remote Robot endpoint." >&2
    exit 2
fi
if ! command -v setsid >/dev/null 2>&1; then
    echo "setsid is required to isolate and stop the IDE UI test process group." >&2
    exit 2
fi

ide_launcher_pid=""
cleanup() {
    local exit_status=$?
    trap - EXIT

    if [[ -n "$ide_launcher_pid" ]] && kill -0 -- "-$ide_launcher_pid" >/dev/null 2>&1; then
        kill -TERM -- "-$ide_launcher_pid" >/dev/null 2>&1 || true
        for _ in {1..50}; do
            if ! kill -0 -- "-$ide_launcher_pid" >/dev/null 2>&1; then
                break
            fi
            sleep 0.1
        done
        if kill -0 -- "-$ide_launcher_pid" >/dev/null 2>&1; then
            kill -KILL -- "-$ide_launcher_pid" >/dev/null 2>&1 || true
        fi
        wait "$ide_launcher_pid" >/dev/null 2>&1 || true
    fi

    if [[ -f "$IDE_RUNTIME_LOG" ]]; then
        cp -f -- "$IDE_RUNTIME_LOG" "$REPORT_DIR/idea.log"
    fi
    if (( exit_status != 0 )); then
        echo "IDE UI smoke test failed. Artifacts: $REPORT_DIR" >&2
        if [[ -f "$IDE_LAUNCH_LOG" ]]; then
            tail -n 120 "$IDE_LAUNCH_LOG" >&2
        fi
    fi

    jmeter_viewer_stop_xvfb
    exit "$exit_status"
}
trap cleanup EXIT

port_is_listening() {
    local port="$1"
    (exec 3<>"/dev/tcp/127.0.0.1/$port") >/dev/null 2>&1
}

select_robot_port() {
    if [[ -n "${JMETER_IDE_UI_PORT:-}" ]]; then
        if [[ ! "$JMETER_IDE_UI_PORT" =~ ^[0-9]+$ ]] ||
            (( JMETER_IDE_UI_PORT < 1024 || JMETER_IDE_UI_PORT > 65535 )); then
            echo "JMETER_IDE_UI_PORT must be an integer from 1024 through 65535." >&2
            return 2
        fi
        if port_is_listening "$JMETER_IDE_UI_PORT"; then
            echo "JMETER_IDE_UI_PORT $JMETER_IDE_UI_PORT is already in use." >&2
            return 2
        fi
        echo "$JMETER_IDE_UI_PORT"
        return
    fi

    local candidate
    for candidate in {8580..8599}; do
        if ! port_is_listening "$candidate"; then
            echo "$candidate"
            return
        fi
    done
    echo "No unused Remote Robot port was available from 8580 through 8599." >&2
    return 2
}

mkdir -p -- "$REPORT_DIR"
rm -f -- \
    "$REPORT_DIR/component-hierarchy.html" \
    "$REPORT_DIR/failure.txt" \
    "$REPORT_DIR/idea.log" \
    "$REPORT_DIR/swing-keyboard-clipboard-failure.png"

readonly SELECTED_ROBOT_PORT="$(select_robot_port)"
export JMETER_IDE_UI_PORT="$SELECTED_ROBOT_PORT"
jmeter_viewer_start_xvfb "IDE UI smoke test"

cd "$PROJECT_DIR"
./gradlew --no-daemon \
    prepareIdeUiTestProject \
    prepareJMeterTestHome \
    prepareUiTestingSandbox \
    --console=plain

setsid ./gradlew --no-daemon runIdeForUiTests --console=plain \
    >"$IDE_LAUNCH_LOG" 2>&1 &
ide_launcher_pid="$!"

robot_ready=false
for _ in {1..360}; do
    if curl --fail --silent --show-error --max-time 1 \
        "http://127.0.0.1:$SELECTED_ROBOT_PORT/" >/dev/null 2>&1; then
        robot_ready=true
        break
    fi
    if ! kill -0 -- "-$ide_launcher_pid" >/dev/null 2>&1; then
        echo "The IDE stopped before Remote Robot became ready:" >&2
        tail -n 120 "$IDE_LAUNCH_LOG" >&2
        exit 1
    fi
    sleep 0.5
done
if [[ "$robot_ready" != true ]]; then
    echo "Remote Robot did not become ready on port $SELECTED_ROBOT_PORT." >&2
    exit 1
fi

./gradlew --no-daemon ideUiTest "$@" --console=plain

if rg -n \
    -e 'Must not change document outside command or undo-transparent action' \
    -e 'Write-unsafe context' \
    -e 'Read access is allowed from inside read-action only' \
    "$IDE_LAUNCH_LOG" "$IDE_RUNTIME_LOG"; then
    echo "The IDE UI smoke test logs contain a forbidden threading or command error." >&2
    exit 1
fi
