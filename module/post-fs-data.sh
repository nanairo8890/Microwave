#!/system/bin/sh
# Microwave: mounts the chosen level's power HAL config over the ROM's (a bind mount, no
# metamodule needed), or skips it if the ROM's config changed.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

# The WebUI's list of thermal zones is valid for one boot (zone numbers follow the driver load order),
# and its SoC/GPU/driver line is found again in case a ROM update brought another driver
rm -f "$MODDIR"/.zones-* "$MODDIR/.hwinfo"
pick_config
