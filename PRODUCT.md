# Product

## Register

product

## Users
ImperiumMC players: grinders, PvP players, story players and cosmetic collectors on a Rome-themed prison
server (play.imperiummc.net). They open the client's menus mid-session, often between mining runs or fights,
to toggle a module, move a HUD element or change a setting, and want to be back in the game within seconds.
Many play for hours, so the UI has to stay readable on a dimmed game world at any GUI scale.

## Product Purpose
Imperium Client is the server's own Fabric client mod: a complete PvP and quality-of-life feature set (HUD
modules, zoom, waypoints, chat tools, performance options) with a fast module menu and HUD editor, plus
ImperiumMC integration (server-gated features, cosmetics, legions). Success: players pick it over other
clients because it is quicker, cleaner and feels like part of the server, and new players find every
setting without asking in Discord.

## Brand Personality
Clean, confident, imperial. The menus follow the reference layout in `docs/design/README.md` (owner decision, 2026-10-19):
its layout, sizing, motion and polish, carrying ImperiumMC's emblem, name and bronze palette. Copy is
plain British English; Latin appears only in the brand mark and the motto "Roma Aeterna", never as filler.

## Anti-references
- Low-effort AI output: muddy textures, visual noise, low-contrast grey-on-grey text, generic stock art.
- Generic "dark SaaS dashboard with a gold accent": flat cards with no hierarchy and no game behind them.
- Neon gamer clients (RGB edges, aggressive gradients and glows everywhere).
- Cartoon fantasy RPG UI (parchment scrolls, bubbly ornaments).
- Mismatched screens: vanilla grey buttons or old config screens next to the new shell.

## Design Principles
1. **Reference structure, our identity.** Follow the reference layout and proportions faithfully: the title screen,
   the sidebar shell, module cards, setting rows, toasts and HUD editor. Change only the brand (emblem,
   wordmark), the palette (bronze on warm dark) and the icons.
2. **The game stays visible.** Translucent overlay controls with a light blur over the real world or
   panorama, never a flat opaque wallpaper.
3. **Minecraft-native details.** Vanilla item icons in module tiles, the Minecraft font for HUD text and
   in-world numbers, the vanilla panorama (later an ImperiumMC one) behind the title screen.
4. **Back to the game fast.** Search first, one click to toggle, settings one level deep.
5. **One theme system.** Every colour is a token with the names listed in `docs/design/README.md`, so themes can
   be swapped without touching layout.

## Accessibility & Inclusion
- Text meets WCAG AA contrast (4.5:1 body, 3:1 large) against the menu surface, including labels and
  placeholders.
- Works at GUI scales 1–4 and with the vanilla font option.
- "Reduce motion" removes all animation.
- State is never shown by colour alone: enabled cards change border and the power icon, server-blocked cards show a block icon and a reason line.
