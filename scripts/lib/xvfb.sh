#!/usr/bin/env bash

JMETER_VIEWER_XVFB_PID=""
JMETER_VIEWER_XVFB_LOG=""

jmeter_viewer_find_xvfb() {
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

jmeter_viewer_start_xvfb() {
    local purpose="${1:-UI test}"
    if [[ -n "${DISPLAY:-}" ]] && xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
        return
    fi

    local xvfb_bin
    xvfb_bin="$(jmeter_viewer_find_xvfb)" || {
        echo "No working display or Xvfb executable was found for the $purpose." >&2
        return 2
    }
    local xvfb_root
    xvfb_root="$(cd -- "$(dirname -- "$xvfb_bin")/.." && pwd)"
    local xvfb_library_path="${LD_LIBRARY_PATH:-}"
    if [[ -d "$xvfb_root/lib" ]]; then
        xvfb_library_path="$xvfb_root/lib${xvfb_library_path:+:$xvfb_library_path}"
    fi

    local display_number=""
    local candidate_number
    for candidate_number in {90..119}; do
        if [[ ! -e "/tmp/.X11-unix/X$candidate_number" ]]; then
            display_number="$candidate_number"
            break
        fi
    done
    if [[ -z "$display_number" ]]; then
        echo "No unused local X display was available for the $purpose." >&2
        return 2
    fi

    export DISPLAY=":$display_number"
    JMETER_VIEWER_XVFB_LOG="$(mktemp "${TMPDIR:-/tmp}/jmeter-viewer-xvfb.XXXXXX.log")"
    env LD_LIBRARY_PATH="$xvfb_library_path" \
        "$xvfb_bin" "$DISPLAY" -screen 0 1600x1200x24 -nolisten tcp \
        >"$JMETER_VIEWER_XVFB_LOG" 2>&1 &
    JMETER_VIEWER_XVFB_PID="$!"

    local attempt
    for attempt in {1..50}; do
        if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
            return
        fi
        if ! kill -0 "$JMETER_VIEWER_XVFB_PID" >/dev/null 2>&1; then
            echo "Xvfb stopped before the $purpose could start:" >&2
            sed -n '1,120p' "$JMETER_VIEWER_XVFB_LOG" >&2
            return 2
        fi
        sleep 0.1
    done

    echo "Xvfb did not become ready for the $purpose:" >&2
    sed -n '1,120p' "$JMETER_VIEWER_XVFB_LOG" >&2
    return 2
}

jmeter_viewer_stop_xvfb() {
    if [[ -n "$JMETER_VIEWER_XVFB_PID" ]]; then
        kill "$JMETER_VIEWER_XVFB_PID" >/dev/null 2>&1 || true
        wait "$JMETER_VIEWER_XVFB_PID" >/dev/null 2>&1 || true
        JMETER_VIEWER_XVFB_PID=""
    fi
    if [[ -n "$JMETER_VIEWER_XVFB_LOG" ]]; then
        rm -f -- "$JMETER_VIEWER_XVFB_LOG"
        JMETER_VIEWER_XVFB_LOG=""
    fi
}
