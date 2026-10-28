#!/system/bin/sh
# Root-only, phone-local supervisor. No arbitrary program/command arguments.
# RUN_ID SECONDS [normal|cancel|timeout] [target channel]. Failure leaves an explicit recovery
# status; never restart Android after an unverified Linux down operation.
set -u
umask 077
id=${1:-}
seconds=${2:-}
test_mode=${3:-normal}
target=${4:-}
channel=${5:-}
case "$id" in ''|*[!a-zA-Z0-9_-]*) exit 2;; esac
test "${#id}" -le 64 || exit 2
case "$seconds" in ''|*[!0-9]*) exit 2;; esac
test "$seconds" -ge 1 && test "$seconds" -le 20 || exit 2
case "$test_mode" in normal|cancel|timeout|rfcomm|echo) ;; *) exit 2;; esac
if [ "$test_mode" = rfcomm ] || [ "$test_mode" = echo ]; then
    case "$target" in [0-9A-Fa-f][0-9A-Fa-f]:[0-9A-Fa-f][0-9A-Fa-f]:[0-9A-Fa-f][0-9A-Fa-f]:[0-9A-Fa-f][0-9A-Fa-f]:[0-9A-Fa-f][0-9A-Fa-f]:[0-9A-Fa-f][0-9A-Fa-f]) ;; *) exit 2;; esac
    if [ "$test_mode" = rfcomm ]; then
        case "$channel" in ''|*[!0-9]*) exit 2;; esac
        test "$channel" -ge 1 && test "$channel" -le 30 || exit 2
    fi
fi
test "$(id -u)" = 0 || exit 2
helper=/data/local/tmp/m30-linux-lab
lock=/data/local/tmp/m30-handoff-supervisor.lock
mkdir "$lock" 2>/dev/null || { echo supervisor_busy; exit 1; }
released=0
child=
log() { echo "run_id=$id utc=$(date -u +%Y-%m-%dT%H:%M:%SZ) uptime=$(cut -d ' ' -f1 /proc/uptime) event=$1"; }
cleanup() {
    trap - EXIT HUP INT TERM
    if [ -n "$child" ]; then kill -TERM "$child" 2>/dev/null; wait "$child" 2>/dev/null; fi
    if [ "$released" = 1 ]; then
        if timeout 8 "$helper" hci0 down "$id" 1; then
            start vendor.bluetooth-1-0
            svc bluetooth enable
            tries=0
            while [ "$tries" -lt 8 ]; do
                if dumpsys bluetooth_manager | grep -q 'state: ON'; then break; fi
                sleep 1
                tries=$((tries + 1))
            done
            p=$(pidof android.hardware.bluetooth@1.0-service)
            if [ "$(getprop init.svc.vendor.bluetooth-1-0)" = running ] &&
               dumpsys bluetooth_manager | grep -q 'state: ON' &&
               ls -l /proc/"$p"/fd 2>/dev/null | grep -q /dev/scsc_h4_0; then
                log android_owner_restored
            else log RECOVERY_REQUIRED_android; fi
        else log RECOVERY_REQUIRED_linux_down; fi
    fi
    rmdir "$lock"
}
trap cleanup EXIT
trap 'log supervisor_cancelled; exit 3' HUP INT TERM
test "$(getenforce)" = Enforcing || exit 1
test "$(getprop init.svc.vendor.bluetooth-1-0)" = running || exit 1
dumpsys bluetooth_manager | grep -q 'state: ON' || exit 1
readlink -f /sys/class/bluetooth/hci0 | grep -q /120c0000.scsc_wifibt/ || exit 1
"$helper" hci0 inspect "$id" 1 | grep -q '"value":0' || exit 1
log android_owner_before
svc bluetooth disable
released=1
stop vendor.bluetooth-1-0
test "$(getprop init.svc.vendor.bluetooth-1-0)" = stopped || exit 1
if ls -l /proc/[0-9]*/fd/* 2>/dev/null | grep -q /dev/scsc_h4_0; then
    log transport_still_held
    # Linux was not started; normal restoration is safe.
    exit 1
fi
log android_released
if [ "$test_mode" = rfcomm ] || [ "$test_mode" = echo ]; then
    timeout -k 5 12 "$helper" hci0 "$test_mode" "$id" "$seconds" "$target" "$channel" &
elif [ "$test_mode" = timeout ]; then
    timeout -k 5 3 "$helper" hci0 scan "$id" "$seconds" &
else
    timeout -k 5 32 "$helper" hci0 scan "$id" "$seconds" &
fi
child=$!
if [ "$test_mode" = cancel ]; then
    sleep 3
    # Signal the exact helper child of timeout, not every similarly named task.
    targets=$(ps -A -o PID,PPID,NAME | awk -v parent="$child" '$2 == parent && $3 == "m30-linux-lab" {print $1}')
    if [ -z "$targets" ]; then log cancel_target_missing; else
        for p in $targets; do kill -TERM "$p"; done
        log cancel_sent
    fi
fi
wait "$child"
result=$?
child=
log "helper_exit_$result"
exit "$result"
