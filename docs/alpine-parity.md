# Alpine parity spec

Imperium Client targets feature and UX parity with Alpine Client 1.8.0 (26.3), rebuilt on the
AxolotlClient codebase. Alpine's code and art are proprietary; this document records *behaviour and
settings* as observed in its config, and every feature here is implemented independently.

Source: the user's Alpine `profiles.json` (70 modules). **Bold** = enabled in the user's profile, built first.

## Shared HUD settings

Almost every HUD module in Alpine carries the same style block. Implement once in a shared
`HudStyle` and attach it to every text HUD:

`draw_background`, `brackets`, `background_color`, `corner_radius`, `blur_radius`, `draw_border`,
`border_color`, `border_thickness`, `text_color`, `text_shadow`, `width`, `height`, `hide_in_debug`,
`position` (anchor + offset), `z_level`.

Axolotl's `TextHudEntry`/`BoxHudEntry` already cover background, outline, text colour, shadow and
positioning. Missing: corner radius, background blur, `brackets`, explicit width/height, `hide_in_debug`, z-level.

## Client options (Alpine "client" module)

Menu key, menu scale, menu theme, menu/HUD frame limits, tooltip position, frame limiter
(`NONE`/`MINIMIZED`/`UNFOCUSED`/`AFK` with background/AFK/long-AFK FPS and delays), no fog, minimal view
bob, fire/shield height, pumpkin/spyglass/frost opacity, toggles for advancement and toast rendering,
plus the performance group: hologram/mob/sign render distances and hiding, lazy chunk loading,
chunk update limit, hopper/water/lava hiding, chest/spawner optimisation, enchant table book,
display-name optimisation, lightning, cannon (TNT/sand) minimal rendering, font shadow toggle,
model stitching fix, culling fix.

## Module map

Status: ✅ exists in Axolotl (needs Alpine-style settings), 🟡 partial, ❌ build new, 🔒 server-gated.

| Alpine module | Axolotl equivalent | Status |
|---|---|---|
| **cps** | CPSHud | ✅ add right-click toggle |
| **fps** | FPSHud | ✅ |
| **tps** | TPSHud | ✅ add adaptive colour tiers |
| **compass** | CompassHud | 🟡 add waypoint markers, yaw, arrow |
| **scoreboard** | ScoreboardHud | ✅ add server-HUD-safety, score toggle |
| **boss_bar** | BossBarHud | ✅ add health/text toggles |
| **titles** | — | ❌ movable title/subtitle (ActionBarHud is the pattern) |
| **info_hud** | CoordsHud (partial) | 🟡 configurable multi-line info registry |
| **cooldowns** | — | ❌ item cooldown bars/circles |
| **toggle_sneak_sprint** (+ status HUD) | ToggleSprintHud | 🟡 add double-tap, sneak-fly, fly boost |
| **fullbright** | `fullBright` option | ✅ add keybind toggle |
| **environment** | `timeChanger` option (partial) | 🟡 add weather state/intensity, time multiplier |
| **chat_settings** | ChatHud (partial) | 🟡 smooth chat, timestamps, persistent chat, hover highlight, peek, screenshot, IGN highlight/ping, empty filter |
| chat_settings/compact_chat | — | ❌ stack duplicate messages |
| **tab_editor** | — | ❌ numeric ping, self/group highlight |
| **nametag_editor** | `nametagOptions` | 🟡 own nametag, client icon, shadow, background |
| **waypoints** | — | ❌ waypoints + death waypoints, world render, keybind |
| **inventory_tweaks/inventory_search** | — | ❌ search bar with slot highlight |
| **inventory_tweaks/scrollable_tooltips** | ScrollableTooltips | ✅ add smooth scroll, activation key |
| inventory_tweaks | — | ❌ hide potion effects, keep centred |
| **factions** (+ breadcrumbs, cannon_playback, cannon_view, explosion_boxes, patchcrumbs, status HUD) | — | ❌ factions tooling, low priority |
| **enchantedmc** / boosts | — | ❌ boosts HUD (server-specific) |
| **enchantedmc/fishing** | — | 🔒 auto-fish, server opt-in only |
| enchantedmc/clicking | — | 🔒 autoclicker, server opt-in only |
| zoom | Zoom | ✅ scroll, cinematic, smooth, sensitivity |
| keystrokes | KeystrokeHud | ✅ |
| armor_status | ArmorHud | ✅ add durability formats/colours |
| potion_status | PotionsHud | ✅ add blink/collapsed/dynamic colour |
| coordinates | CoordsHud | ✅ add biome, cardinal, shout keybind |
| block_coordinates | — | ❌ |
| ping, memory, speed, reach_display, combo_count, pack_display, server_display | PingHud, MemoryHud, SpeedHud, ReachHud, ComboHud, PackDisplayHud, IPHud | ✅ |
| clock, day_counter | IRLTimeHud, DayCounterHud | ✅ |
| playtime, stopwatch, entity_count, potion_count, obsidian_count | — | ❌ simple text HUDs |
| crosshair | CrosshairHud | ✅ add friendly/enemy dynamic colour |
| block_overlay | block outline options | ✅ add highlight fill mode |
| hit_color | `hitColor` option | ✅ armour/trim toggles |
| tnt_timer | TntTime | ✅ add fuse colour stages |
| particles | Particles | ✅ add disabled list, crit/sharpness multipliers |
| perspective_mod | Freelook | ✅ hold mode, invert pitch, smooth |
| snap_look | — | ❌ |
| hurt_cam, low_hp_tint, clear_water, farming | — | ❌ small render tweaks |
| chunk_borders | — | ❌ |
| minimap | — | ❌ large: chunk-colour map, shapes, rotation |
| worldedit_cui | — | ❌ |
| spotify_state | — | ❌ Spotify Web API HUD |
| discord_rpc | DiscordRPC | ✅ server IP toggle |
| emotes | — | ❌ emote wheel (needs cosmetics backend) |
| motion_blur | MotionBlur | ✅ |

## Menu

Alpine-style layout: full-screen overlay on the menu key (default Right Shift), category sidebar, module
search, module cards with an enable toggle and a gear that opens the settings page, profile switcher
(Alpine profiles: named, auto-switch per server/world/version), and a "HUD editor" button that
drops into drag-and-snap positioning. Theme: Roman/ImperiumMC palette, not Alpine's art.
