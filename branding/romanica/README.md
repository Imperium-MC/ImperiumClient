# Romanica emblem

`romanica-emblem-source.webp` is the owner's master artwork. Everything else is cut from it as a circle with a
transparent outside (centre 511.5, 281, radius 229.5 in the source; edge anti-aliased at 4× supersampling).

| File | Use |
|---|---|
| `romanica-emblem-{1024,512,256,192,180,128,64,48,32,16}.png` | Transparent circle: mod icon, menus, title screen, launcher UI, website logo, server icon |
| `romanica.ico` | Windows icon (16–256): launcher `build/icon.ico`, `SealCircle.ico` |
| `romanica-maskable-{512,192}.png` | PWA maskable icons (emblem at 80 % on navy `#0E1628`) |
| `romanica-apple-180.png` | `apple-icon.png` (iOS has no transparency, so navy background) |

The source is 1024×559, so the circle is natively about 459 px. The 1024 file is upscaled; prefer 512 and
below. Colours for the UI are in `docs/design/README.md` → Theme tokens.
