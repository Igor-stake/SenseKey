#!/data/data/com.termux/files/usr/bin/bash
# SPDX-License-Identifier: GPL-3.0-only
set -eu
warmup=$1
shift
if curl -sS --max-time 2 http://127.0.0.1:8080/health >/dev/null 2>&1; then
    printf 'Port 8080 is occupied. Stop the previous model first.\n' >&2
    exit 1
fi
wake_locked=false
server_pid=
cleanup() {
    if [ -n "$server_pid" ] && kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null || true
        # Only the child started by this launcher; do not leave an old server behind.
        for attempt in {1..25}; do
            kill -0 "$server_pid" 2>/dev/null || break
            sleep .2
        done
        kill -KILL "$server_pid" 2>/dev/null || true
    fi
    if [ "$wake_locked" = true ]; then termux-wake-unlock || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if command -v termux-wake-lock >/dev/null && command -v termux-wake-unlock >/dev/null \
        && termux-wake-lock; then wake_locked=true; fi
"$@" &
server_pid=$!
ready=false
for attempt in {1..90}; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
        printf 'Model server exited during loading. Read the error above.\n' >&2
        exit 1
    fi
    if curl -fsS --max-time 2 http://127.0.0.1:8080/health >/dev/null 2>&1; then ready=true; break; fi
    sleep 1
done
if [ "$ready" != true ]; then printf 'Model did not become ready.\n' >&2; exit 1; fi
printf '\nPreparing the keyboard prompt once. This may take several minutes.\n'
# Static production instructions and invented examples only: no phone/editor data.
curl -fsS --max-time 600 -H 'Content-Type: application/json' \
    --data-binary "@$warmup" http://127.0.0.1:8080/v1/chat/completions -o /dev/null
printf '\nSENSEKEY READY - now return to your chat. Keep this session running.\n'
status=0
wait "$server_pid" || status=$?
server_pid=
exit "$status"
