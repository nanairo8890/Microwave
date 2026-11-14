# Game boost root module for the POCO F5 Pro / Redmi K60 (mondrian)

Da más rendimiento en juegos a través del power HAL de la ROM, con un panel web
para ver qué está pasando.

*English below.*

## Compatibilidad

| | |
| --- | --- |
| **Dispositivo** | POCO F5 Pro / Redmi K60 (`mondrian`). Ningún otro. |
| **Requisito** | KernelSU o un fork (BakaSU, ReSukiSU). |
| **Android** | 14 o más nuevo. |

En otro teléfono el instalador se niega.

### ROMs

El boost depende de que el `/vendor/etc/powerhint.json` de la ROM sea exactamente el que el módulo
conoce. Compara ese archivo, no el nombre de la ROM ni su kernel.

| ROM | Estado |
| --- | --- |
| Project Infinity-X 4.1 (17.10.2026, Android 17) | Funciona: boost verificado con un juego |
| MistOS 5.1 Beta (20261011, Android 17) | Mismo `powerhint.json`, debería funcionar; no probada |
| Cualquier otra ROM | Sin verificar |

En una ROM cuyo archivo es distinto el módulo se instala, pero el boost queda apagado y solo se
aplican los ajustes de ART; el panel web avisa. Para saber si tu ROM coincide, compara este valor
con el que da tu teléfono:

```
adb shell su -c "sha256sum /vendor/etc/powerhint.json"
9b6149a465416e72ba669228c83a52b7c78ca054da9749fdf5b3b2419d76b9f6
```

El módulo no toca el kernel, las particiones ni los drivers. Si lo desinstalas, todo vuelve a como
estaba.

## Qué hace

- **Boost de juegos.** Sube los pisos de CPU, GPU y memoria mientras hay un juego en pantalla y al
  salir todo vuelve a lo normal.
- **Perfil térmico de juegos automático.** El perfil normal de Xiaomi limita el núcleo principal a
  2,25 GHz y los grandes a 2,57 GHz desde 25 °C. Con un juego abierto el módulo cambia al perfil de
  juegos y al salir vuelve al normal.
- **Tres perfiles**, que eliges en el panel web y se
  aplican al instante sin reiniciar el teléfono:
  - *Alto y estable* (por defecto): sin límite de CPU hasta 45 °C, pisos de 1,56 GHz en los núcleos
    grandes, 1,65 GHz en el principal y 580 MHz en la GPU, y memoria y caché más altas.
  - *Máximo*: sin límites hasta 51 °C, CPU y GPU fijas al máximo y sin reposo profundo. Más FPS,
    mucho más calor y gasto de batería.
  - *Personalizado* (hasta 7 perfiles guardados): tus propios valores. En el panel web arrastras las barras (perfil térmico, piso
    de CPU, piso de GPU, prioridad del juego y piso de memoria) y tocas Aplicar, al estilo de MSI
    Afterburner. Valen mientras hay un juego en pantalla.
- **Ajustes de ART de MistOS 5.1**: heap de 512 MB, caché JIT más grande, precarga de código y
  procesos USAP listos de antemano.

## Panel web

Se abre desde el botón del módulo en el manager (o con KsuWebUI), en el idioma del teléfono
(11 idiomas, inglés por defecto). Muestra el estado del boost, procesador, GPU y driver, las
frecuencias y temperaturas, lo que pone cada nivel y un monitor de hardware de los últimos 4 minutos.

## Overlay

El módulo instala la app **Microwave Overlay**, un monitor que se dibuja encima del juego: uso,
frecuencia y temperatura de GPU y CPU, memoria de la GPU, RAM, potencia en vatios, FPS con el
tiempo por fotograma, una barra por núcleo y gráficas del último minuto.

Se enciende y apaga desde el panel web. Antes hay que darle permiso de superusuario a
"Microwave Overlay" en el manager. Se arrastra con el dedo y recuerda su posición.
Con dos dedos se achica o agranda. En la app se ajusta su opacidad y se elige qué partes muestra.

## Instalación

1. Instala `Microwave-v1.2.zip` desde el manager y reinicia.
2. Deja apagado "Use thermal profiles" (Configuración > Sistema > Thermal profiles): el módulo ya
   maneja el perfil térmico en los juegos.

## Si el teléfono no arranca

El módulo se desactiva solo si algo sale mal, pero si aun así no arranca:

- **Modo seguro:** presiona bajar volumen 3 veces durante el arranque, después del logo, y borra el
  módulo desde el manager.
- **Desde el recovery** (OrangeFox, TWRP): crea un archivo vacío
  `/data/adb/modules/Microwave/disable`, o borra esa carpeta, y reinicia.

## Aviso

Úsalo bajo tu responsabilidad. El autor no se hace responsable de daños en el dispositivo causados
por un mal uso: subir las frecuencias y soltar los límites térmicos da más calor y gasta más batería.

## Créditos

`powerhint.json` base del árbol de mondrian (LineageOS) · valores de ART de MistOS 5.1.

---

## English

Gives games more performance through the ROM's power HAL, with a WebUI that shows
what is going on.

### Compatibility

| | |
| --- | --- |
| **Device** | POCO F5 Pro / Redmi K60 (`mondrian`) only. |
| **Requirement** | KernelSU or a fork (BakaSU, ReSukiSU). |
| **Android** | 14 or newer. |

On another phone the installer refuses.

#### ROMs

The boost needs the ROM's `/vendor/etc/powerhint.json` to be exactly the one the module knows. It
compares that file, not the ROM's name or its kernel.

| ROM | Status |
| --- | --- |
| Project Infinity-X 4.1 (17.10.2026, Android 17) | Works: boost verified with a game |
| MistOS 5.1 Beta (20261011, Android 17) | Same `powerhint.json`, should work; not tested |
| Any other ROM | Unverified |

On a ROM with a different file the module installs, but the boost stays off and only the ART
settings apply; the WebUI tells you. To know whether your ROM matches, compare this value with the
one your phone gives:

```
adb shell su -c "sha256sum /vendor/etc/powerhint.json"
9b6149a465416e72ba669228c83a52b7c78ca054da9749fdf5b3b2419d76b9f6
```

The module does not touch the kernel, partitions or drivers. Uninstalling it puts everything back.

### What it does

- **Game boost.** Raises CPU, GPU and memory floors while a game is on screen and goes back to
  normal when you leave it.
- **Automatic gaming thermal profile.** Xiaomi's default profile caps the main core at 2.25 GHz and
  the big cores at 2.57 GHz from 25 °C. With a game open the module switches to the gaming profile
  and restores the default afterwards.
- **Three profiles**, picked in the WebUI, applied
  instantly without a reboot:
  - *High and stable* (default): no CPU cap up to 45 °C, 1.56 GHz floors on the big cores, 1.65 GHz
    on the main core and 580 MHz on the GPU, plus higher memory and cache.
  - *Maximum*: no limits up to 51 °C, CPU and GPU pinned to max and no deep sleep. More FPS, much
    more heat and battery drain.
  - *Custom* (up to 7 saved profiles): your own values. In the WebUI you drag the bars (thermal profile, CPU floor, GPU
    floor, game priority and memory floor) and tap Apply, MSI Afterburner style. They hold while a
    game is on screen.
- **MistOS 5.1 ART settings**: 512 MB heap, larger JIT cache, code preloading and USAP processes
  ready in advance.

### WebUI

Opens from the module's button in the manager (or with KsuWebUI), in your phone's language (11
languages, English by default). It shows the boost state, processor, GPU and driver, clocks and
temperatures, what each level sets and a hardware monitor of the last 4 minutes.

### Overlay

The module installs the **Microwave Overlay** app, a monitor drawn over the game: GPU and CPU
usage, clock and temperature, GPU memory, RAM, power in watts, FPS with the frame time, one bar per
core and graphs of the last minute.

Turn it on and off from the WebUI. Grant superuser to "Microwave Overlay" in the manager first.
Drag it with a finger; it remembers its place.
Pinch it with two fingers to resize it. The app sets its opacity and which parts it shows.

### Install

1. Install `Microwave-v1.2.zip` from the manager and reboot.
2. Leave "Use thermal profiles" (Settings > System > Thermal profiles) off.

### If the phone doesn't boot

The module disables itself if something goes wrong, but if it still doesn't boot:

- **Safe mode:** press volume down 3 times during boot, after the logo, then remove the module from
  the manager.
- **From recovery** (OrangeFox, TWRP): create an empty
  `/data/adb/modules/Microwave/disable` file, or delete that folder, and reboot.

### Disclaimer

Use it at your own risk. The author is not responsible for any damage to the device caused by
misuse: raising frequencies and loosening thermal limits means more heat and battery drain.

### Credits

Base `powerhint.json` from the mondrian tree (LineageOS) · ART values from MistOS 5.1.

Author: Nanairo8890.
