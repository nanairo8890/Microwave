#!/system/bin/sh
# Microwave: the manager runs this when the module is removed. Takes the overlay app with it.
pm uninstall app.microwave.overlay >/dev/null 2>&1
