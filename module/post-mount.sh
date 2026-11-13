#!/system/bin/sh
# Microwave: the module's powerhint.json is mounted; restart the power HAL so it
# reads it, still before zygote (see common.sh).
MODDIR=${0%/*}
. "$MODDIR/common.sh"

restart_hal
