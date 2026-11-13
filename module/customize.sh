# Microwave: installer. Runs in the root manager with Android up.
MODDIR=$MODPATH
. "$MODPATH/common.sh"
lang_init

[ "$BOOTMODE" = true ] || abort "$(msg '! Instala el módulo desde la app del root (KernelSU/BakaSU), no desde el recovery.' \
                                       '! Install the module from the root manager app (KernelSU/BakaSU), not from recovery.')"
# The module is made and tested for ksud's boot stages and its Action button and WebUI
{ [ "$KSU" = true ] && [ "$APATCH" != true ]; } ||
  abort "$(msg '! Solo para KernelSU / BakaSU. Magisk y APatch no son compatibles.' \
               '! For KernelSU / BakaSU only. Magisk and APatch are not supported.')"
# The power HAL gets the GAME mode from Android 14 on
[ "$API" -ge 34 ] || abort "! Android 14+"
dev=$(getprop ro.product.vendor.device)
[ -n "$dev" ] || dev=$(getprop ro.product.device)
[ "$dev" = mondrian ] || abort "$(msg "! Solo para mondrian (POCO F5 Pro / Redmi K60); este teléfono es $dev." \
                                      "! For mondrian only (POCO F5 Pro / Redmi K60); this phone is $dev.")"

ui_print " "
ui_print '  _____                      _____'
ui_print ' /     \                    /     \'
ui_print '|  .-.  |  ______________  |  .-.  |'
ui_print '| (   ) |-`              `-| (   ) |'
ui_print '|  `-`  /                  \  `-`  |'
ui_print ' \     / /\/\/\/\/\/\/\/\/\ \     /'
ui_print '  `---|  \  \          /  /  |---`'
ui_print '      |   \  \        /  /   |'
ui_print '      |    \__\      /__/    |'
ui_print '      |              .---.   |'
ui_print '      |   `---`     ( (@) )  |'
ui_print '      |              `---`   |'
ui_print '      |   ///  \_/\_/   ///  |'
ui_print '       \                    /'
ui_print '        `-._            _.-`'
ui_print '            `----------`'
ui_print " "
ui_print "  Microwave $(mod_version)"
ui_print "  by Nanairo8890"
ui_print " "
ui_print "$(msg '  Para POCO F5 Pro / Redmi K60 (mondrian) con Project Infinity-X 4.1 (17.10.2026)' \
                '  For POCO F5 Pro / Redmi K60 (mondrian) on Project Infinity-X 4.1 (17.10.2026)')"
ui_print "$(msg '  o MistOS 5.1 Beta (20261011), con KernelSU/BakaSU.' \
                '  or MistOS 5.1 Beta (20261011), with KernelSU/BakaSU.')"
ui_print " "

# An update keeps the level picked with the Action button
OLD=/data/adb/modules/mondrian_game_boost
[ -f "$OLD/level" ] && cp -f "$OLD/level" "$MODPATH/level"
for f in "$OLD"/custom-[1-7].conf "$OLD/custom.slot"; do
  [ -f "$f" ] && cp -f "$f" "$MODPATH/"
done
[ -f "$MODPATH/level" ] || echo alto > "$MODPATH/level"

# The ROM's file shows at /vendor/etc/powerhint.json here unless an older version is mounted
if rom_ok || cmp -s "$HINTS" "$OLD/system/vendor/etc/powerhint.json"; then
  ui_print "$(msg '  Boost de juegos: listo (power HAL de Infinity-X / MistOS para mondrian).' \
                  '  Game boost: ready (Infinity-X / MistOS power HAL for mondrian).')"
else
  touch "$MODPATH/skip_mount"
  ui_print "$(msg '  ! El power HAL de esta ROM no es el que conoce el módulo: el boost de juegos queda' \
                  "  ! This ROM's power HAL config is not the one the module knows: the game boost stays")"
  ui_print "$(msg '    apagado y solo se aplican los ajustes de ART.' \
                  '    off and only the ART settings apply.')"
fi
ui_print "$(msg "  Nivel: $(level_name "$(level)"). El botón Acción o el panel web lo cambian al instante." \
                "  Level: $(level_name "$(level)"). The Action button or the WebUI change it at once.")"
ui_print "$(msg '  En los juegos pone solo el perfil térmico de juegos de Xiaomi: deja apagado' \
                "  In games it sets Xiaomi's gaming thermal profile by itself: leave")"
ui_print "$(msg '  "Use thermal profiles" (Configuración > Sistema > Thermal profiles).' \
                '  "Use thermal profiles" off (Settings > System > Thermal profiles).')"
ui_print "$(msg '  Ajustes de ART de MistOS (heap de 512 MB, JIT, precarga, USAP): listos.' \
                '  MistOS ART settings (512 MB heap, JIT, readahead, USAP): ready.')"
ui_print " "
# Always in English and in capitals, so nobody misses it in the install log
ui_print "  ============================================"
ui_print "  DISCLAIMER"
ui_print "  ============================================"
ui_print "  I AM NOT RESPONSIBLE IF YOUR DEVICE IS"
ui_print "  DAMAGED BY MISUSE OF THIS MODULE."
ui_print "  IT IS YOUR OWN RESPONSIBILITY FROM THE"
ui_print "  MOMENT YOU INSTALLED IT."
ui_print "  ============================================"
ui_print " "
ui_print "$(msg '  Reinicia para aplicarlo.' '  Reboot to apply it.')"

set_perm_recursive "$MODPATH" 0 0 0755 0644
# Same owner, mode and labels as /vendor, /vendor/etc and the ROM's file (set_perm labels everything
# system_file, and the power HAL may only read vendor config files)
set_perm "$MODPATH/system/vendor" 0 2000 0755 u:object_r:vendor_file:s0
set_perm "$MODPATH/system/vendor/etc" 0 2000 0755 u:object_r:vendor_configs_file:s0
set_perm "$OURS" 0 0 0644 "$CONF_CON"
# The manager shows the level in the phone's language from the start
set_desc
