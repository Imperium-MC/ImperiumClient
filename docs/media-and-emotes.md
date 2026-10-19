# Media & emotes — wave 5 shells (W5-A / W5-B)

Owner directive: our OWN cosmetics, not Alpine's. Both features ship as complete client shells
now, so the UI/UX work is done and the missing server pieces plug into a seam instead of a
rewrite.

## W5-A: emote wheel

**UX shape.** Hold the `key.imperiumclient.emote_wheel` keybind (default G, rebindable in
Controls under the ImperiumClient category — B was the natural pick but the waypoints-add
keybind already holds it) and the wheel opens over the world — no pause,
transparent background. The defined emotes fan out from a carved hub (deepslate panel, engraved
capitals, bronze hairline to the selected slot — the CarvedTheme conventions from
ImperiumMenuScreen). Point at a slot and release the key to send; click a slot to send
immediately; ESC cancels. The hub centre is a dead zone, so an untouched pointer selects
nothing.

**Config** (`emotes` category in the module menu):

- `emotes` — master toggle (default on; the wheel is client-local today, so there is nothing
  to gate yet).
- `emote.1` … `emote.8` — the eight slots. A slot is a built-in id (`wave`, `salute`, `cheer`,
  `sit` — the four built-ins ship in slots 1-4 so the wheel demos out of the box) or a custom
  `id;Display Name;RRGGBB` triple. Empty slots are simply absent from the wheel; the defined
  ones redistribute evenly.

**The dispatcher seam.** Selecting a slot never sends anything directly — it calls
`EmoteDispatch.dispatch(Emote)`:

```
EmoteWheelScreen ──> EmoteDispatch (holder) ──> EmoteDispatcher (interface)
                                                    │
                                                    ├─ LocalEmoteDispatcher   (today: log + self-toast
                                                    │    "Emote sent: wave")
                                                    └─ future: imperiumclient:cosmetics receiver
```

When the plugin's cosmetics channel lands, its payload receiver installs its own dispatcher
(`EmoteDispatch.set(...)`) that turns the `Emote` into the channel payload and the toast
becomes the server's broadcast acknowledgement. Nothing in the wheel changes.

**Out of scope — deliberately (gating piece).** Player-side animation rendering (arm waves,
salutes, sitting poses on the sender and on other players) is NOT in this shell. There is
nothing to animate until the server exists: no `imperiumclient:cosmetics` channel, no emote
wire format, no broadcast to other clients, no permission/tier checks. Building animation now
would mean inventing a protocol the server then has to match. The work items when the channel
lands: (1) the dispatcher swap above, (2) a remote-player animation renderer keyed by the
broadcast emote id, (3) slot validation against the server's emote catalogue.

## W5-B: now-playing HUD

**Shell pattern (same as BoostsHud).** The HUD (`imperiumclient:nowplayinghud`, "Now Playing")
renders "track — artist" over a carved bronze progress bar and reads exactly one thing:
`MediaStatusRegistry`. Nothing published → zero pixels, the entry auto-sizes to nothing. Any
future provider (OS media-session bridge, another player app) writes
`MediaStatusRegistry.update(MediaStatus...)` and the HUD lights up.

**Provider shipped today: Spotify desktop local endpoint (best-effort, default OFF).** A stock
JVM has no system media API, so the pragmatic v1 path is the Spotify desktop app's undocumented
local HTTP status endpoint:

- `GET http://127.0.0.1:<port>/remote/status.json`, ports 4381-4389 (the app binds 4381 and
  shifts upward when taken), with header `Origin: https://open.spotify.com` (required).
- Polled every 5s by a daemon thread while the `media` toggle is on; loopback only. No OAuth,
  no cloud calls, no keys, nothing leaves the machine.
- The document is parsed defensively (`SpotifyLocalStatus`): the classic
  `track_resource`/`artist_resource`/`playing_position` shape and the `*_ms`/`artists[]`
  variant both work; anything unrecognised, plus connection refused/timeout, means the HUD
  stays hidden, silently (one debug log line per state change at most).

**Honest status of the research (2026-10-18):** Spotify is not installed on the dev machine
this was built on, so the endpoint was never live during development. The port window, the
Origin requirement and the response shapes come from the documented community usage of this
interface (it has existed for years and is what every local "now playing" widget uses), and
the code treats every failure as hide-silently precisely because it is undocumented and has
changed before. If it does not work against a live Spotify, the guarantee that still holds is
the registry shell: the HUD is fully wired, tested (seed → visible → clear → hidden), and a
better provider slots in without touching it.

**Not in v1 (explicitly):** OAuth/Web API keys (owner ruled out), cover art, play/pause and
skip controls (the local endpoint is read-only), scrolling text.

## Where things live

| Piece | Path |
|---|---|
| Emote record / built-ins / slot parsing | `imperium/emotes/Emote.java`, `Emotes.java` |
| Dispatcher seam | `imperium/emotes/EmoteDispatcher.java`, `EmoteDispatch.java`, `LocalEmoteDispatcher.java` |
| Wheel screen (carved radial) | `imperium/emotes/EmoteWheelScreen.java` |
| Keybind + open tick + config | `imperium/emotes/EmoteWheel.java`, `EmoteWheelConfig.java` |
| Status snapshot / registry | `imperium/media/MediaStatus.java`, `MediaStatusRegistry.java` |
| Spotify local parse / poll | `imperium/media/SpotifyLocalStatus.java`, `SpotifyLocalPoller.java` |
| Media toggle + wave-5 entrypoint | `imperium/media/MediaConfig.java`, `ImperiumMediaInit.java` |
| HUD entry | `modules/hud/gui/hud/simple/NowPlayingHud.java` |
| Tests | `src/test/.../EmoteWheelGameTest.java`, `MediaStatusGameTest.java` |
