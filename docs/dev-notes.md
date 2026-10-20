# Dev notes

Things that have already cost time on 26.3. Read before touching input, networking or rendering.

## Testing

- `./gradlew :latest:runClientGameTest` runs the client gametests in `versions/latest/src/test`
  (registered under the `fabric-client-gametest` entrypoint in the test `fabric.mod.json`). They start a real
  client, so they take about a minute. Screenshots land in `versions/latest/build/run/clientGameTest/screenshots/`.
- `ImperiumMenuGameTest` opens the menu in a test world, searches, toggles a card, opens settings
  and drags a HUD element in the HUD editor.
- `ImperiumProtocolGameTest` feeds the handshake code the exact bytes the ImperiumMC plugin sends.
- `MovementEnvelopeGameTest` / `LocalEnforcementGameTest` cover the W4-C enforcement layer: pure
  movement-envelope math (vanilla speeds never clamp, cheat deltas clamp under the plugin's flag
  edges), the address-scope rule, the channel allowlist + register filtering (against the real
  Fabric register payload), sign/book payload rules, and live evidence that the `Connection.send`
  hook is applied while the integrated server stays out of scope. The policy for which outgoing
  packets the interceptor may replace vs refuse is [enforcement-policy.md](enforcement-policy.md).
- `TitlesGameTest` drives a coloured, over-long title through the real `Hud.setTitle` path and
  screenshots the wrapped result.
- The gametest run directory keeps its config between runs. Pin any state a test depends on, or delete
  `build/run/clientGameTest/config/imperiumclient` for a clean start.
- To stop a dev client, stop the process you started (its PID). Don't kill by matching `KnotClient` in
  command lines: every Fabric client uses Knot, including a player's real game.

## Input

- **Mouse buttons are SDL-numbered in 26.3: left = 1, middle = 2, right = 3.** Use
  `InputConstants.MOUSE_BUTTON_LEFT/RIGHT`, never `0`/`1`. Upstream AxolotlClient code still used `0`/`1`
  and treated every left click as a right click.
- Key codes are SDL too; use `InputConstants.KEY_*`, there is no GLFW in 26.3.
- Keybinds only fire in game with no screen open. On the title screen, Right Shift does nothing; the
  title-screen and pause-menu buttons open the menu instead.
- `HudEditScreen` picks move/resize from the element under the cursor while rendering, so a drag needs
  the cursor to hover for a frame before the button goes down. (Players always do; tests must wait a tick.)

## Rendering

- Screens draw through `GuiGraphicsExtractor` (`extractRenderState`, `extractBackground`). It also
  implements `AxoRenderContext`, so cast to it for rounded rects and outlines (`br$fillRectRound`,
  `br$outlineRectRoundVarying` with a width).
- Draw `Component`s as `(AxoText) component`, not `component.getString()`, or colours and formatting are lost.
- Avatar renderers also draw `ClientMannequin` (servers use them for NPCs). Never cast an avatar's entity to
  a player type without an `instanceof` check.

## Config

- `AxolotlClientCommon.MODID` is still `axolotlclient` because it is also the asset namespace. The config
  folder is `CONFIG_DIR` (`config/imperiumclient`) so we never touch upstream AxolotlClient's config.
- Module toggles are just config options; `MenuEntries` finds them by name (`enabled`, `*.enabled`,
  `<module>Enabled` or the module's own name). New modules should follow that naming to get a menu toggle.
- AxolotlClient's social API (friends, chat, accounts) talks to AxolotlClient's servers and defaults to off.

## Server protocol

- The ImperiumMC plugin sends and reads **bare UTF-8 JSON** with Bukkit plugin messaging: no length prefix,
  no NBT. Payload codecs must read the whole buffer as UTF-8 (`ImperiumIntegration.RAW_UTF8`). A
  `ByteBufCodecs.fromCodec(Codec.STRING)` codec expects NBT, fails to decode, and disconnects the player.
- Spec: [server-gated-features.md](server-gated-features.md). Server side: `ImperiumClientIntegrationService`
  in the plugin repo.

## Running alongside other clients and mods

From debugging the previous client setup on the same server:

- No Chat Reports checks for signing keys once, at startup. Anything that swaps the session later has to
  re-run that check, or secure-chat servers disable chat ("missing profile public key").
- Two frame limiters (ours and Dynamic FPS) fight over the vanilla inactivity option; ship one.
- Two mods mixing into `Screenshot` (for example a screenshot mod plus a built-in screenshot feature) break
  the class transformation.

## Addresses

- Website: `imperiummc.net` (Cloudflare-proxied, not a Minecraft endpoint).
- Minecraft server: `play.imperiummc.net:25565`.
