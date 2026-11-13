#!/system/bin/sh
# Microwave: makes sure the restarted power HAL is up (system_server waits for it), then,
# once Android is up, writes the state to module.prop (shown in the manager) and to game-boost.log.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

# post-mount.sh did not run (a ksud without that stage): restart the power HAL here. It is late,
# system_server may have asked the old one for its modes already
case $(active) in
  alto|maximo|custom)
    if [ -z "$(hal_state)" ]; then
      restart_hal
      [ "$(hal_state)" = ok ] && echo late > "$HAL_FILE"
    fi ;;
esac
watch_hal

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
sleep 10

lang_init
s=$(state)
set_desc
{
  date
  echo "$s"
  echo "level: $(level); post-fs-data: $(active); power HAL: $(hal_state), $(getprop init.svc.$HAL_SVC)"
  echo "mounts: $(grep -cF "$HINTS" /proc/self/mountinfo)"
  echo "$HINTS: $(ls -Z "$HINTS" 2>/dev/null)"
  echo "GAME: $(game_row)"
  echo "sconfig: $(cat /sys/class/thermal/thermal_message/sconfig 2>/dev/null)"
} > "$MODDIR/game-boost.log" 2>&1
