# Microwave: functions shared by the module scripts (sourced, never run on its own).
#
# Android's GameManager tells the power HAL when a game is the app on top (power mode GAME) and,
# for games in performance mode that report it, when one is loading (GAME_LOADING). The ROM's
# powerhint.json has no action for either, so nothing happens. post-fs-data.sh bind-mounts the
# module's system/vendor/etc/powerhint.json (the ROM's plus game actions, one file per level) over
# /vendor/etc/powerhint.json; no metamodule is needed and the partition is not modified.
#
# Boot order in ksud: module post-fs-data.sh (picks the level's file and mounts it) -> system.prop
# -> post-mount.sh, all before zygote. The power HAL read its config at late-fs, before all that,
# so post-mount.sh restarts it (service.sh does, late, if this ksud did not run post-mount.sh);
# system_server asks it which modes it
# supports once, when it starts. A config the power HAL rejects makes it abort before it registers,
# and system_server waits for it forever: post-mount.sh and service.sh watch for that and give it
# the ROM's config back. The caller sets MODDIR.

HAL_SVC=vendor.power-hal-aidl
HAL_BIN=android.hardware.power-service.lineage-libperfmgr
HAL_NAME=android.hardware.power.IPower/default
HINTS=/vendor/etc/powerhint.json
OURS=$MODDIR/system/vendor/etc/powerhint.json   # system/vendor may be a symlink to ../vendor
ROM_COPY=$MODDIR/powerhint-rom.json             # the ROM's file (sha256 BASE_SHA)
LEVEL_FILE=$MODDIR/level     # alto | maximo | custom, picked in the WebUI
CUSTOM_VALS=$MODDIR/custom-values   # "KEY default values...": what each slider may be set to
CUSTOM_SLOTS=7                      # custom profiles: custom-N.conf, "THERMAL PRIME GPU UCLAMP DDR"
SLOT_FILE=$MODDIR/custom.slot       # which of them the custom level uses
ACTIVE_FILE=$MODDIR/.active  # level put in place by post-fs-data.sh this boot, or mismatch
HAL_FILE=$MODDIR/.hal        # what post-mount.sh / service.sh did with the power HAL this boot
CONF_CON=u:object_r:vendor_configs_file:s0
# sha256 of the powerhint.json the module's files are built from (Project Infinity-X 4.1
# 20261017; MistOS 5.1 Beta 20261011 ships the same file)
BASE_SHA=@BASE_SHA@

# The phone's language (first of the languages picked in Settings, e.g. es-CL; system_locales needs
# Android up). Script messages are in Spanish when it is Spanish and in English otherwise, as in
# SIM-APN-Config; the WebUI translates the state codes itself.
sys_locale() {
  l=$(settings get system system_locales 2>/dev/null)
  [ -z "$l" ] || [ "$l" = null ] && l=$(getprop persist.sys.locale)
  [ -z "$l" ] && l=$(getprop ro.product.locale)
  echo "${l%%,*}"
}
ES=0
lang_init() {
  case $(sys_locale) in
    es*) ES=1 ;;
    *) ES=0 ;;
  esac
  echo $ES > "$MODDIR/.lang"
}
# Before Android is up (post-mount): the language seen at install or on the last boot
lang_saved() { [ "$(cat "$MODDIR/.lang" 2>/dev/null)" = 1 ] && ES=1 || ES=0; }
msg() { if [ $ES = 1 ]; then echo "$1"; else echo "$2"; fi; }

mod_version() { sed -n 's/^version=//p' "$MODDIR/module.prop"; }

level() {
  case $(cat "$LEVEL_FILE" 2>/dev/null) in
    maximo) echo maximo ;;
    custom) echo custom ;;
    *) echo alto ;;
  esac
}
active() { cat "$ACTIVE_FILE" 2>/dev/null; }
hal_state() { cat "$HAL_FILE" 2>/dev/null; }

level_name() {
  case $1 in
    maximo) msg 'máximo' 'maximum' ;;
    custom) msg "personalizado $(custom_slot)" "custom $(custom_slot)" ;;
    *) msg 'alto y estable' 'high and steady' ;;
  esac
}

# The custom level. Only values listed in custom-values (the power HAL nodes' own values) get into
# the config, so a bad custom.conf can not make a config the power HAL rejects
cv_list() { sed -n "s/^$1 [^ ]* //p" "$CUSTOM_VALS" 2>/dev/null; }
cv_default() { sed -n "s/^$1 \([^ ]*\) .*/\1/p" "$CUSTOM_VALS" 2>/dev/null; }
cv_ok() {
  [ -n "$2" ] || return 1
  for cv in $(cv_list "$1"); do [ "$cv" = "$2" ] && return 0; done
  return 1
}
# The custom profile in use, 1 to CUSTOM_SLOTS
custom_slot() {
  cs=
  { read -r cs < "$SLOT_FILE"; } 2>/dev/null
  case $cs in [1-7]) echo "$cs" ;; *) echo 1 ;; esac
}
# "THERMAL PRIME GPU UCLAMP DDR" of custom profile $1 (the one in use if not given): its file if
# every value is allowed, the defaults (those of high and steady) otherwise
custom_values() {
  ct=; cp=; cg=; cu=; cd=
  { read -r ct cp cg cu cd < "$MODDIR/custom-${1:-$(custom_slot)}.conf"; } 2>/dev/null
  if ! { cv_ok THERMAL "$ct" && cv_ok PRIME "$cp" && cv_ok GPU "$cg" && cv_ok UCLAMP "$cu" && cv_ok DDR "$cd"; }; then
    ct=$(cv_default THERMAL); cp=$(cv_default PRIME); cg=$(cv_default GPU)
    cu=$(cv_default UCLAMP); cd=$(cv_default DDR)
  fi
  echo "$ct $cp $cg $cu $cd"
}
# The floor of the big cluster follows the X2 core's: its highest step not above it
big_for() {
  cb=0
  for cv in $(cv_list BIG); do
    [ "$cv" -le "$1" ] && [ "$cv" -gt "$cb" ] && cb=$cv
  done
  echo "$cb"
}
# Writes powerhint-custom.json from the template and the sliders' values
make_custom() {
  set -- $(custom_values)
  [ $# = 5 ] || return 1
  sed -e "s/%THERMAL%/$1/" -e "s/%PRIME%/$2/" -e "s/%BIG%/$(big_for "$2")/" -e "s/%GPU%/$3/" \
      -e "s/%UCLAMP%/$4/" -e "s/%DDR%/$5/" "$MODDIR/powerhint-custom.tpl" > "$MODDIR/powerhint-custom.tmp" 2>/dev/null &&
    [ -s "$MODDIR/powerhint-custom.tmp" ] && ! grep -q '%[A-Z]*%' "$MODDIR/powerhint-custom.tmp" &&
    mv -f "$MODDIR/powerhint-custom.tmp" "$MODDIR/powerhint-custom.json"
}

# Only before the module's mount exists (post-fs-data) does /vendor/etc/powerhint.json show the
# ROM's file
rom_ok() { [ "$(sha256sum "$HINTS" 2>/dev/null | cut -d' ' -f1)" = "$BASE_SHA" ]; }

# post-fs-data: the file mounted over the ROM's is the chosen level's, or nothing if the ROM's config
# is not the one the module's files are built from (they replace it whole)
pick_config() {
  rm -f "$HAL_FILE"
  if rom_ok; then
    # a custom level that can not be built falls back to high and steady
    [ "$(level)" = custom ] && { make_custom || echo alto > "$LEVEL_FILE"; }
    cat "$MODDIR/powerhint-$(level).json" > "$OURS" &&
      chmod 0644 "$OURS" && chcon "$CONF_CON" "$OURS" || { echo error > "$ACTIVE_FILE"; touch "$MODDIR/skip_mount"; return 1; }
    rm -f "$MODDIR/skip_mount"
    level > "$ACTIVE_FILE"
    # A file bind-mounted over a file keeps the source's label (set above). If the mount fails the
    # ROM's file stays and restart_hal reports no-overlay
    mount -o bind "$OURS" "$HINTS" 2>/dev/null
  else
    touch "$MODDIR/skip_mount"
    echo mismatch > "$ACTIVE_FILE"
  fi
}

# Drops whatever is mounted over the ROM's file (the module's bind mount, and a metamodule's if
# there is one): umount only acts on a mount point, so /vendor itself is never touched
unmount_hints() {
  u=0
  while [ $u -lt 4 ] && umount "$HINTS" 2>/dev/null; do u=$((u + 1)); done
}

# The module's file is what /vendor/etc/powerhint.json shows, with the label the power HAL may read
overlay_ok() { [ -f "$OURS" ] && cmp -s "$OURS" "$HINTS"; }
label_ok() { ls -Z "$HINTS" 2>/dev/null | grep -q "$CONF_CON"; }

# Pid of the running power HAL; nothing while init has it stopped or restarting (init publishes
# init.svc_debug_pid.<service>; pidof if it does not)
hal_pid() {
  s=$(getprop "init.svc.$HAL_SVC")
  [ -z "$s" ] || [ "$s" = running ] || return 0
  p=$(getprop "init.svc_debug_pid.$HAL_SVC")
  [ -n "$p" ] && { echo "$p"; return 0; }
  pidof "$HAL_BIN" 2>/dev/null
}
hal_registered() { service check "$HAL_NAME" 2>/dev/null | grep -q ': found'; }

# Watches the power HAL for up to $1 s after a restart ($2: its pid before, if known).
# Up: registered, or the same pid seen running 4 s in a row (on a config it rejects it aborts within
# milliseconds, and init restarts it every 5 s). Down: init shows it restarting, or it comes back
# with yet another pid. Returns 0 if up, 1 if down, 2 if neither could be told (no restart seen yet,
# or no pid to look at): then nothing is touched.
hal_up() {
  n=0; same=0; last=; first=; changed=0; down=0
  [ -z "$2" ] && changed=1
  while [ $n -lt "$1" ]; do
    s=$(getprop "init.svc.$HAL_SVC")
    p=$(hal_pid)
    [ "$p" != "$2" ] && changed=1
    if [ $changed = 1 ]; then
      [ "$s" = restarting ] && down=1
      if [ -n "$p" ]; then
        hal_registered && return 0
        [ -z "$first" ] && first=$p
        [ "$p" != "$first" ] && down=1
        if [ "$p" = "$last" ]; then same=$((same + 1)); else same=0; fi
        [ $same -ge 4 ] && return 0
      fi
    fi
    last=$p
    sleep 1
    n=$((n + 1))
  done
  [ $down = 1 ] && return 1
  return 2
}

# The power HAL keeps aborting with the module's config: unmount it (or, if that fails, serve the
# ROM's content through the mount), restart the power HAL and turn the module off for the next boot
heal() {
  unmount_hints
  cmp -s "$OURS" "$HINTS" && cat "$ROM_COPY" > "$OURS"
  touch "$MODDIR/disable"
  echo "healed-$1" > "$HAL_FILE"
  setprop ctl.restart "$HAL_SVC"
  # A disabled module's service.sh does not run: say it here
  lang_saved
  s=$(state)
  set_desc
  { date; echo "$s"; echo "power HAL: healed in $1"; } > "$MODDIR/game-boost.log" 2>&1
}

# post-mount: restart the power HAL so it reads the injected file, and wait (10 s at most) for the
# new instance to be up. If init has not restarted it yet, service.sh checks it later.
restart_hal() {
  [ -f "$MODDIR/skip_mount" ] && { echo skip > "$HAL_FILE"; return 0; }
  overlay_ok || { echo no-overlay > "$HAL_FILE"; return 0; }
  # A label the power HAL cannot read would make it abort: keep the ROM's config it already runs,
  # and unmount it, because the ROM restarts the power HAL whenever zygote restarts
  if ! label_ok; then
    unmount_hints
    echo bad-label > "$HAL_FILE"
    return 0
  fi
  old=$(hal_pid)
  setprop ctl.restart "$HAL_SVC"
  echo restarted > "$HAL_FILE"
  sleep 1
  hal_up 10 "$old"
  case $? in
    0) echo ok > "$HAL_FILE" ;;
    2) echo pending > "$HAL_FILE" ;;
    *) heal post-mount ;;
  esac
}

# The chosen level, now, without a reboot: its config goes into the mounted file and only the
# power HAL restarts. system_server and SurfaceFlinger reconnect to it by themselves, and these
# configs have no ADPF sessions to lose; the WebUI runs this while the manager,
# not a game, is on screen. Only while the boost works this boot. Returns 0 when the level is active,
# 1 when it waits for the next boot, 2 when the power HAL rejected it (healed, like at boot).
apply_level() {
  lv=$(level)
  [ "$lv" = custom ] && { make_custom || return 1; }
  # nothing to do only if the file being served is already that level's
  [ "$lv" = "$(active)" ] && cmp -s "$MODDIR/powerhint-$lv.json" "$OURS" && return 0
  [ "$(state_code)" = active ] && [ ! -f "$MODDIR/disable" ] || return 1
  overlay_ok && label_ok || return 1
  cat "$MODDIR/powerhint-$lv.json" > "$OURS" || return 1
  old=$(hal_pid)
  setprop ctl.restart "$HAL_SVC"
  sleep 1
  hal_up 10 "$old"
  case $? in
    0) echo "$lv" > "$ACTIVE_FILE"; echo ok > "$HAL_FILE"; return 0 ;;
    1) heal live; return 2 ;;
    # not told apart within 10 s: it reads the new file whenever it comes up
    *) echo "$lv" > "$ACTIVE_FILE"; echo late > "$HAL_FILE"; return 0 ;;
  esac
}

# service: the same watch once more, for a restart init did after post-mount.sh returned
watch_hal() {
  case $(hal_state) in
    restarted|pending) ;;
    *) return 0 ;;
  esac
  hal_up 30 ""
  r=$?
  # post-mount.sh may still be running (past ksud's deadline) and may have healed meanwhile
  case $(hal_state) in healed-*) return 0 ;; esac
  case $r in
    # pending: restarted after post-fs-data, system_server may have asked the old one for its modes
    0) [ "$(hal_state)" = pending ] && echo late > "$HAL_FILE" || echo ok > "$HAL_FILE" ;;
    1) heal service ;;
  esac
}

# The running power HAL has the module's config: only it has a GAME hint, and the dump lists every
# hint as "<name>\t<count>\t<duration ms>"
game_row() { dumpsys "$HAL_NAME" 2>/dev/null | grep "^GAME$(printf '\t')"; }

# XiaomiParts' per-app thermal profiles (off by default) write the same thermal scene file on every
# app switch, so it and the module overwrite each other
thermal_profiles_on() { dumpsys activity services org.lineageos.settings 2>/dev/null | grep -q 'thermal.ThermalService'; }

# The state as a code (the WebUI translates it): active, reboot, mismatch, error, mount-failed,
# bad-label, healed, notran, noconfig
state_code() {
  case $(active) in
    mismatch|error) active; return ;;
    alto|maximo|custom) ;;
    *) echo reboot; return ;;
  esac
  case $(hal_state) in
    no-overlay) echo mount-failed; return ;;
    bad-label) echo bad-label; return ;;
    healed-*) echo healed; return ;;
    '') echo notran; return ;;
  esac
  # webui.sh status passes the row it already read in GAME_ROW
  if [ -z "${GAME_ROW-$(game_row)}" ]; then echo noconfig; else echo active; fi
}

# The power HAL restarted after post-fs-data: system_server may have asked the old one for its modes
hal_late() { case $(hal_state) in late|pending|restarted) return 0 ;; esac; return 1; }

# One-line state for the manager; returns 0 if the game boost works
state() {
  case $(state_code) in
    mismatch)
      msg 'Boost de juegos inactivo: el power HAL de esta ROM no es el que conoce el módulo' \
          "Game boost inactive: this ROM's power HAL config is not the one the module knows" ;;
    error)
      msg 'Error: no se pudo preparar el archivo del módulo' 'Error: could not prepare the module file' ;;
    reboot)
      msg 'Boost de juegos inactivo: reinicia el teléfono' 'Game boost inactive: reboot the phone' ;;
    mount-failed)
      msg 'Boost de juegos inactivo: no se pudo montar el archivo del módulo sobre el de la ROM' \
          "Game boost inactive: could not mount the module's file over the ROM's" ;;
    bad-label)
      msg 'Boost de juegos inactivo: el archivo montado no tiene la etiqueta SELinux vendor_configs_file' \
          'Game boost inactive: the mounted file does not have the vendor_configs_file SELinux label' ;;
    healed)
      msg 'El power HAL no aceptó la config del módulo: se le devolvió la de la ROM y el módulo quedó desactivado' \
          "The power HAL did not take the module's config: it got the ROM's back and the module is now disabled" ;;
    notran)
      msg 'Boost de juegos inactivo: el módulo no corrió en este arranque' \
          'Game boost inactive: the module did not run this boot' ;;
    noconfig)
      msg 'Boost de juegos inactivo: el power HAL no tiene la config del módulo' \
          "Game boost inactive: the power HAL does not have the module's config" ;;
    active)
      s=$(msg "Boost de juegos activo, nivel $(level_name "$(active)")" \
              "Game boost active, level $(level_name "$(active)")")
      hal_late && s="$s $(msg '(el power HAL se reinició tarde: si los juegos no lo activan, reinicia)' \
                              '(the power HAL restarted late: if games do not trigger it, reboot)')"
      [ "$(level)" != "$(active)" ] && s="$s $(msg "(pasa a $(level_name "$(level)") al reiniciar)" \
                                                  "(switches to $(level_name "$(level)") on reboot)")"
      thermal_profiles_on && s="$s $(msg '(apaga "Use thermal profiles" en Configuración > Sistema: sobrescribe el perfil térmico del módulo)' \
                                         '(turn off "Use thermal profiles" in Settings > System: it overwrites the module)')"
      echo "$s"
      return 0 ;;
  esac
  return 1
}

# The description in the manager: only the level the user picked while the boost works ("Nivel:
# Máximo"); when it does not, the reason after it, since the manager shows nothing else
set_desc() {
  p=$MODDIR/module.prop
  case $(level) in
    maximo) d=$(msg 'Nivel: Máximo' 'Level: Maximum') ;;
    custom) d=$(msg "Nivel: Personalizado $(custom_slot)" "Level: Custom $(custom_slot)") ;;
    *) d=$(msg 'Nivel: Alto y estable' 'Level: High and steady') ;;
  esac
  if [ "$(state_code)" = active ]; then
    [ "$(level)" = "$(active)" ] || d="$d $(msg '(al reiniciar)' '(after a reboot)')"
  else
    d="$d · $(state)"
  fi
  { grep -v '^description=' "$p"; echo "description=$d"; } > "$p.tmp" &&
    mv -f "$p.tmp" "$p"
}
