# Imperium Client UI

Every client screen should look like part of one hand-made product: **carved, disciplined, imperial**, and
clearly at home in Minecraft. See [`PRODUCT.md`](../../PRODUCT.md) for who it's for and what it must never look
like. Behaviour is specified in #4; the quality bar is #3. Colours come from the ImperiumMC design system
(`Imperium-MC/imperium-frontend` → `DESIGN_SYSTEM.md`); where they disagree, that CSS wins.

## Mockups

All are drawn at **GUI scale 2** (one GUI pixel = 2 px), so every border and bevel in them is exactly one GUI
pixel.

| | |
|---|---|
| ![Modules](mockups/01-menu.png) **01 Modules**: stone shell, dentil band, item icons in slots, grouped ledger, lever toggles, keybinds in the game font | ![Settings](mockups/02-settings.png) **02 Module settings** inside the menu: live preview in the game font, grouped rows, search hit outlined |
| ![HUD editor](mockups/03-hud-editor.png) **03 HUD editor** over the game: bronze toolbar, snapping guides, pixel handles, context menu with anchor picker | ![Title](mockups/04-title.jpg) **04 Title screen** over the real ImperiumMC map (the Colosseum), Play ImperiumMC with the live player count |
| ![Waypoints](mockups/05-waypoints.png) **05 Any other screen** (Waypoints) in the same shell, with a confirmation and a toast | ![Components](mockups/06-components.png) **06 Components**: materials, buttons, inputs, dropdown, tooltip, toasts |

To render them: `python extract-mc-assets.py <path to minecraft-26.3-client.jar>` (copies the vanilla icons and
font atlas into the git-ignored `mc/` folder, since Mojang's textures are not committed), then serve the folder
(`python -m http.server --directory docs/design/mockups`) and screenshot at 1280×720, for example
`chrome --headless=new --window-size=1280,720 --screenshot=out.png http://localhost:8000/01-menu.html`.

## The language

**Materials, not effects.** Three materials do all the work:

| Material | Built from | Used for |
|---|---|---|
| **Stone** | `--basalt-2` with a very fine, low-contrast grain (alpha ≈ 4.5%) | Panels, sidebar, rows |
| **Sunken well** | Near-black, 1 GUI px dark top-left and light bottom-right bevel (like an inventory slot) | Search, inputs, icon slots, toggle tracks, keybinds |
| **Bronze plate** | Flat `--bronze`, 1 GUI px light top edge, dark bottom edge, dark outline, engraved text shadow | Primary buttons, the selected nav item, toggle knobs, slider thumbs |

No gradients on metal, no glows, no blur, no transparency on panels. The game behind a menu is dimmed flat (as
vanilla does), not frosted.

**Pixel-grid frames.** Every panel has a 1 GUI px outline plus a 1 GUI px bevel and notched corners, so it
9-slices cleanly as a texture at any GUI scale. No rounded corners anywhere.

**One ornament.** A dentil moulding (the toothed band under Roman cornices) under the header, in dark bronze.
Nothing else is decorative.

**Real game assets.** Module and nav icons are vanilla item icons rendered in sunken slots (compass, clock,
spyglass, name tag, ender pearl…), drawn with the game's item renderer at runtime, never copied into this repo.
Control glyphs (close, search, gear, back, lock, check, reset) are hand-drawn 9×9 pixel art (`glyphs.js`).

**Type.** Three faces, each with one job:
- **Barlow** (OFL) for UI labels and descriptions, weights 500/600/700.
- **Cinzel** (OFL) only for the IMPERIUM wordmark.
- **The Minecraft font** for anything the game would show: numbers, counts, coordinates, distances, keybinds,
  hex values and all HUD text.

Ship Barlow and Cinzel as TrueType font providers (`assets/imperiumclient/font/ui.json`, `brand.json`,
`"type": "ttf"`, oversample 2–4) with `OFL.txt`. Settings → Menu → "UI font: Imperium / Minecraft" switches
Barlow to the vanilla font for players who prefer it.

**Contrast.** Every text tier is at least 4.5:1 on stone: `--marble` for primary text, `--text-2` for
descriptions, `--text-3` for counts and hints. Never use gold for body text.

**What's banned** (the things that made the first draft look generated): frosted glass, gold gradients and
glows, soft rounded cards, generic line icons, identical card grids, tiny tracked-caps labels above every
section, coloured side-stripe accents, low-contrast muted text.

## Tokens

One Kotlin `object ImperiumTheme`, mirrored in the theme JSON (#4 §1):

| Token | Value | | Token | Value |
|---|---|---|---|---|
| `basalt` | `#0F0E0C` | | `bronze` | `#C39A58` |
| `basalt2` (stone) | `#171512` | | `bronzeHi` | `#ECD6A6` |
| `basalt3` (hover) | `#211E19` | | `bronzeLo` | `#7E5F2C` |
| `seam` / `seamHi` | `#2E2820` / `#3D352A` | | `bronzeDeep` | `#3B2C14` |
| `marble` (text) | `#F2EDE4` | | `ink` (text on bronze) | `#1A130A` |
| `text2` | `#CDBFA6` | | `blood` / `bloodDeep` | `#B93608` / `#5C1A05` |
| `text3` | `#A3937A` | | | |

Implement the frames, wells and plates as **9-slice GUI sprites** in the client's assets
(`assets/imperiumclient/textures/gui/sprites/…` with `nine_slice` scaling), not as code-drawn rectangles. That
keeps every edge on the pixel grid at every GUI scale and lets a resource pack restyle the client.

## Title screen

Use a **real panorama of the ImperiumMC world** (a cubemap captured at the Forum or the Colosseum) with slow
rotation. The mockup uses the server's top-down map render as a stand-in. Don't use the launcher's illustrated
background art in the client.

## Vanilla screens: the built-in "Imperium UI" pack

Options, video settings, the server list and so on stay vanilla in layout. A built-in resource pack (Fabric
`registerBuiltinResourcePack`, on by default, switchable) reskins vanilla widget sprites in the same
materials: `widget/button*` as stone (normal) and bronze (highlighted), `slider*`, `text_field*`, `scroller*`,
`tab*`, `checkbox*`, toasts, tooltip background and frame, list separators. Inventories and containers are not
touched.

## Screen-by-screen

| Current screen | Target |
|---|---|
| `imperium/menu/ImperiumMenuScreen` | Shell (01, 02) |
| AxolotlClient config screens (`ConfigStyles.createScreen`) | In-menu settings pages (02); a hidden fallback for options with no widget yet |
| `modules/hud/HudEditScreen` | HUD editor (03) |
| `modules/hud/gui/keystrokes/*` (5 screens) | Keystrokes sub-pages in the shell |
| `config/screen/ProfilesScreen` | Shell → Profiles |
| `modules/waypoints/WaypointsScreen` | Shell → Waypoints (05) |
| `modules/auth/*` (accounts, offline, device code) | Account switcher on the title screen, Settings → Accounts, device code in a confirmation-style box |
| `modules/auth/skin/*` | Shell → Cosmetics → Skin |
| `modules/screenshotUtils/*` | Shell → Screenshots (gallery plus the editor from #8) |
| `config/screen/CreditsScreen` | Settings → About (AxolotlClient, fonts, contributors) |
| `api/*` and `api/multiplayer/*` | Social and Legion sections, hidden until the Imperium backend replaces AxolotlClient's (imperium-backend#2) |
| `modules/hypixel/*` config screens | Shell sub-pages under Server, or removed with the Hypixel modules |
| `mixin/TitleScreenMixin` | Title screen (04) |
| `mixin/GameMenuScreenMixin` | Vanilla pause screen plus the built-in pack, with Imperium Client (bronze) and HUD Editor buttons |
| Vanilla Options, Video, Controls, server list, chat | Built-in pack only |
| Toasts, tooltips, context menus | The shared components (06) |

## Acceptance

- Gametests screenshot every shell section, the HUD editor, the title and pause screens at GUI scale 2 and 3,
  with both UI fonts; review compares them against these mockups.
- Every border and bevel lands on the GUI pixel grid at scales 1–4 (no half-pixel blur).
- No client screen uses `ConfigStyles`, plain vanilla-styled `Button.builder` buttons, rounded rectangles or
  AxolotlClient colours, except the documented fallback.
- Switching theme, UI font or the built-in pack updates open screens without a restart.
