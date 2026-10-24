# Code Review: RomanicaClient (2026-10-23)

> **Repo:** Romanica-MC/RomanicaClient · **Reviewed commit:** `6649e49e0` (main) · **Method:** static review of working tree at HEAD (focus on custom code, not upstream AxolotlClient)
> Severities: **P0** = broken now · **P1** = likely bug / security hole · **P2** = design debt · **P3** = nice-to-have

## Project Snapshot

RomanicaClient (mod id `imperiumclient`, jar `ImperiumClient`) is a Fabric client mod for Minecraft 26.3 (Java 25, Kotlin 2.4.21 via fabric-language-kotlin) built for the Romanica/ImperiumMC Rome-themed prison server. It is a fork of AxolotlClient-mod (Codeberg, LGPL-3.0 preserved, upstream remote configured) — upstream still supplies the config library, bridge API, social API, auth/skin stack, Hypixel/MCCI modules and ~90% of mixins, while ~10.5k lines of custom Kotlin in `io.github.axolotlclient.imperium` plus `legions`/`enchantedmc` modules add server handshake channels, a Mojang-session-check API auth layer, movement/payload "trusted client" enforcement, a custom menu/title screen with a carved-stone theme, and HUD tooling. Size is ~633 source files (~13k lines main, 7.2k lines of gametests); 1,577 commits with 194 since Sept 1 — very active (near-daily, disciplined conventional commits).

## Architecture Assessment

The fork was started from a template snapshot, not a git-history fork, and upstream tracking is effectively manual: `upstream` remote exists but nothing has been merged from it since the fork era, and the MC-version chase (26.3 via loom `1.17.+`, Ornithe/ploceus plugin still configured from upstream's multi-version setup) is now entirely this repo's problem. Upstream remnants are trimmed to one version module (`versions/latest`, `axolotlclient.modules.latest=true` in `settings.gradle`).

The custom layer is well-layered itself: `ImperiumIntegration`/`ImperiumChannels` (plugin-message handshake + data push, `imperiumclient:*` channels), `ImperiumClientSession`/`ServerGating` (server-granted feature tiers incl. v2 rate budgets), `imperium.online` (`ClientApiAuth` state machine → bearer JWT for `api.romanica.net`, `ClientApi` fetch layer), `imperium.enforcement` (outgoing-packet chokepoint via a single `@WrapMethod` on `Connection.send`, allowlist-scoped to romanica.net), and `imperium.menu`/`theme` (title screen + module menu). The Java→Kotlin conversion (576 `.kt` vs 57 `.java`, the Java remainder mostly mixins/accessors "by design") was done in 9 documented waves with explicit "seam" fix commits and codified mixin rules in `AGENTS.md` (the public `@Shadow` property that overwrote `Minecraft.setLevel` and crashed world joins is documented and now guarded by tests).

Note: branding is mid-migration. Domain cutover to romanica.net is complete (grep finds only the intentional negative-test string `evilimperiummc.net` in a comment), but the project still identifies as AxolotlClient in gradle (`rootProject.name = "AxolotlClient-mod"`, group `io.github.axolotlclient`), as "ImperiumMC" in logs/lang, and — materially — to upstream's backend (see Issues).

## Strengths

- **Enforcement layer is genuinely well-engineered.** Pure, MC-free decision logic (`MovementEnvelope.kt`, `PayloadRules.kt`, `ChannelGuard.kt` in `versions/latest/src/main/kotlin/io/github/axolotlclient/imperium/enforcement/`) behind a single chokepoint (`ConnectionEnforcementMixin.kt` wraps `Connection.send`); no other mixin targets `Connection`, so no intra-mod conflict. Scope decisions are cached per-connection in a `WeakHashMap`, memory connections are structurally excluded, and the reflective Fabric register-payload rebuild is fail-open with a one-shot log rather than fail-closed.
- **Auth state machine is production-grade.** `ClientApiAuth.kt` keeps the JWT memory-only, uses a generation counter so superseded flows never publish, single-flights concurrent callers, does exactly one 401 retry, and never blocks the client thread. `ClientAuthGameTest.java` exercises all of it against in-memory fakes (challenge→join→verify, offline skip, supersede, 401 refresh, channel-priority of the legion fallback).
- **Server protocol is defensive.** `ImperiumIntegration.RAW_UTF8` documents why NBT codec decoding disconnected players (found in live testing) and all receivers marshal through `ctx.client().execute`, parse leniently, and drop malformed packets without disconnect. `LegionStateRegistry`/`BoostRegistry`/`ServerCooldownsRegistry` are volatile/concurrent with replace-semantics.
- **Tests actually fail-able and behavior-level.** 34 gametests (~7.2k lines), e.g. `LocalEnforcementGameTest` proves the hook fires and is inert out-of-scope *and* that a stale `enabled: false` config key can't disable the layer; `TitlesGameTest` pins colour retention and wrap-growth of long titles; `CarvedThemeGameTest` (merged Oct 23) pins the CARVED icon path in both null-level and in-world contexts — the exact context of a reported NPE.
- **The working agreement is enforced in code**, not just prose: evidence-from-real-run rules, "tests must be able to fail", grep-to-prove-migration-complete (the domain cutover is genuinely clean), and CI artifact screenshots for UI changes.
- **Real bugs get regression tests**: the breadcrumb minutes-as-milliseconds fix (`BreadcrumbManager.kt` `Expiry` now `5 * 60_000L`, commit e13ac771b) came with an enforcement-scope test on the retired domain (efe67a027).

## Issues

- **[P1] Upstream AxolotlClient social API still ships enabled and reports play activity to a third-party backend.** `common/src/main/kotlin/io/github/axolotlclient/api/Constants.kt:27,32` (`api.axolotlclient.com/v1/`, `ENABLED = true`) + `AxolotlClient.kt:114` + `TitleScreenMixin.checkAPIStart`: on every title screen init, any non-offline account triggers `API.startup(account)`, which after the upstream privacy prompt opens a WebSocket and pushes 15-second status updates containing the current server's **name and IP** (`StatusUpdateProviderImpl.kt`, `StatusUpdate.inGameServer(entry.name, entry.ip)`). For a rebranded client distributed to one server's players this is a privacy hole, a trust problem (consent screen is axolotlclient.com/terms, not Romanica's), and an availability dependency on infrastructure you don't operate — and it sits oddly next to an enforcement layer whose stated purpose is to prevent mod fingerprints from reaching servers.
- **[P2] Title-screen account head always renders Steve.** `ImperiumTitleScreen.kt:185-189` does `UUID.fromString(Auth.getInstance().getCurrent()?.uuid)`, but `Account.uuid` is always stored undashed (`Auth.kt:90` → `UUIDHelper.toUndashed` → `API.sanitizeUUID` strips dashes), so `fromString` throws `IllegalArgumentException` every time and the catch falls back to `UUID(0,0)` → Steve, forever (slim/Alex never selected either). The "until the signed skin is loaded" comment is not implemented. The same catch would not cover the NPE from a null `getCurrent()` (only `IllegalArgumentException` is caught) — theoretical today because `Auth.init` always seeds an account.
- **[P2] Release changelog pipeline publishes empty notes.** `CHANGELOG.md` is still the inherited upstream changelog (`### 3.2.2` …); `generateVersionChangelog` (`build.gradle.kts:141-166`) regex-matches `### 0.2.0-beta.25`, finds nothing, writes an empty file, and `publishMods` uploads that as the Modrinth/CurseForge changelog for every beta release.
- **[P2] Silent client-side refusals of sign/book packets have no player feedback, and the book title rule is stricter than vanilla.** `EnforcementService.checkSignPacket/checkBookPacket` drop packets pre-socket with only a once-per-session log. Vanilla's book UI allows 32-char titles; `PayloadRules.BOOK_TITLE_MAX = 16` mirrors the server plugin, so a player signing a book with a 17–32-char title on romanica.net clicks "Sign" and nothing happens, ever, with no message — indistinguishable from a client bug. Same class of issue for the 1-per-2s sign rate hold.
- **[P2] Upstream news is default-on and the version check would point players at the wrong client.** `Options.kt:94` (`displayNotes` default true) renders AxolotlClient's API news on the Romanica title screen; with `updateNotifications` opted in (`Options.kt:91`), fork version `0.2.0-beta.25` is always "older" than upstream `3.2.x` (`SemVer.isNewerThan`), so the button would link players to AxolotlClient's Modrinth page — i.e., instruct your player base to install a different client.
- **[P2] Repo-wide `failOnNoDiscoveredTests = false` contradicts the repo's own rule.** `build.gradle.kts:53-55` disables the zero-tests-must-fail guarantee for every project, while `AGENTS.md` mandates "A CI step that runs zero tests must fail, not go green." A renamed/mis-discovered test suite would go green silently.
- **[P3] Branding/migration debt in identifiers.** `settings.gradle:33` (`rootProject.name = "AxolotlClient-mod"`), group `io.github.axolotlclient`, "ImperiumMC" in log lines (`ImperiumOnline.kt:104,115`) and 17 lang entries, and the shipped package `modules.enchantedmc` (another server's name, from the "Alpine parity" work) leak into the public jar. Mod id/channels (`imperiumclient:*`) are protocol-locked to the plugin and fine.
- **[P3] Build reproducibility gaps.** Floating plugin versions (`fabric-loom "1.17.+"`, `ploceus "1.17.+"`, `licenser "2.0.+"`), `mavenLocal()` and a snapshots repo in every project's repositories, and no committed `gradle.lockfile` (the monthly OSV watch job generates lockfiles ephemerally). MC/FAPI/Kotlin deps are pinned, so this is drift risk, not chaos.
- **[P3] `AGENTS.md` docs rot.** "The titles fix … still isn't on `main`" is no longer true — the wrapped, colour-preserving TitleHud is on main and pinned by `TitlesGameTest.java`. A stale "known failure" teaches future agents the wrong state.

## Testing & Tooling

- **Gametests are the backbone**: 34 suites covering enforcement scope, auth state machine, protocol channels, gating tiers, movement envelope, HUD editor, theme, translations parity, benchmarks (opt-in). Tests run against in-memory fakes where possible and use the real Fabric register payload class for the channel-guard test. A within-mod subset mechanism (`ImperiumGametestSubsets`) cleanly splits GPU-heavy render tests from the headless CI subset.
- **CI is honest about its limits**: `ci.yml` builds on every push/PR with JDK 25; the gametest job is `workflow_dispatch`-only with a documented, measured reason (client boot under mesa softGL exceeds runner budgets). Full suite is a pre-release ritual per `AGENTS.md`. `dependency-watch.yml` does a monthly OSV scan with lockfile generation.
- **Gaps**: the `failOnNoDiscoveredTests=false` blanket (above); no committed lockfile; no unit-test layer below the gametests (pure logic like `MovementEnvelope`/`PayloadRules` is gametest-tested, which works but makes every logic check cost a client boot); publishing targets `maven.axolotlclient.com` (upstream's maven) with the ImperiumClient artifact.

## Top Recommendations

1. **Decide the upstream API question now** (P1): default the social API off (`Constants.ENABLED = false` or `apiOptions.enabled` default false), remove/repurpose the upstream privacy prompt, and repoint news/version-check to romanica.net — or document explicitly that players' server IPs flow to AxolotlClient's backend.
2. **Fix the title-screen head**: parse with `UUIDHelper.fromUndashed` (or store dashed UUIDs), and either load the real skin via the existing SkinManager or drop the "signed skin" comment.
3. **Start a Romanica CHANGELOG** (per-release `### 0.2.0-beta.N` sections or generate from commits) so Modrinth/CurseForge publishes stop shipping empty notes.
4. **Make refusals visible**: a one-line action-bar/chat message on sign/book/payload refusal, and re-evaluate `BOOK_TITLE_MAX = 16` vs vanilla's 32 — silent packet drops will be reported as client bugs.
5. **Harden the test gate**: replace the blanket `failOnNoDiscoveredTests = false` with per-module opt-outs, and commit `gradle.lockfile`s (the watch job already proves they generate).
6. **Finish the identity migration in identifiers**: `rootProject.name`, gradle group, log/lang "ImperiumMC" strings, and the `enchantedmc` package name (rename to something Romanica-neutral); update the stale AGENTS.md titles lesson while there.

---

## Deep-Dive Addendum — RomanicaClient (2026-10-23, pass 2)

### Verification runs

- `./gradlew :latest:compileKotlin` → **BUILD SUCCESSFUL** (committed tree compiles as-is).
- Grep audits: `axolotlclient.com` runtime URLs only in `Constants.kt:32,35`, `fabric.mod.json:17`, `TitleScreenMixin.kt:118` (rest are build-time maven repos). `imperiummc.net`: **zero** runtime refs. `println(`/`printStackTrace`/`TODO/FIXME`: **zero** in custom code.
- Custom mixin surface is exactly 3 classes in `imperiumclient-enforcement.mixins.json`; no tick/render hot-path injections anywhere else in custom code; all Fabric event registrations happen once in `init()` behind `initialized` flags; DISCONNECT handlers clear registries correctly.

### Correction to pass 1 — social API severity

**[P1 → P2, latent] `Constants.ENABLED=true` is only the compile-time gate.** The runtime gate `apiOptions.enabled` defaults **false** (`common/.../api/Options.kt:76`); the socket starts solely via `API.startup` (API.kt:436-465), which requires `apiOptions.enabled` **and** the privacy state machine (UNSET → prompt, ACCEPTED → connect, DENIED → skip). With shipped defaults **no data reaches api.axolotlclient.com** — the 15s server-name/IP status updates are an opt-in capability reachable from the config UI, not an active leak. Minimal hard-disable change set:
1. `Constants.kt:27` — `ENABLED = false`. This alone throws at `API.startup` (API.kt:440) **and** `APIOptions.kt:150` removes the whole `api` category from the config UI, making the opt-in unreachable.
2. `versions/latest/.../mixin/TitleScreenMixin.kt:229-235` — delete the `checkAPIStart` inject body (dead work per title init).
3. `AxolotlClient.kt:114` — drop `API(StatusUpdateProviderImpl())` construction (or keep for minimal diff; construction alone transmits nothing).
4. If ever re-enabled: `Options.kt:94` `displayNotes` → false, `:97` `addShortcutButtons` → false; repoint `TitleScreenMixin.kt:118` Modrinth URL at `https://romanica.net`.

### Root-cause analysis of pass-1 findings

**[P2] Steve head — exact fix, plus a worse bug in the same block.** `Account.uuid` is stored undashed (`Account.kt:40`), so `UUID.fromString` always throws → catch → `UUID(0,0)` → Steve for every account. Worse: when `getCurrent()` is null, `UUID.fromString(null)` throws **NPE, outside the catch** → title-screen render crash for offline/no-account users. Fix (3 lines):
```kotlin
val uuid = Auth.getInstance().getCurrent()?.let { UUIDHelper.fromUndashed(it.uuid) } ?: UUID(0L, 0L)
```
(import `io.github.axolotlclient.api.util.UUIDHelper`; `fromUndashed` at UUIDHelper.kt:53.)

**[P2] Changelog.** `generateVersionChangelog` (`build.gradle.kts:141-162`) regexes root `CHANGELOG.md` (upstream content) for `### 0.2.0-beta.25` → no match → empty file → published as release notes. Fix: replace root `CHANGELOG.md` with a Romanica changelog in the shape `### <version>\n\n- bullet` (≥1 bullet per release), or point line 143 at a new `ROMANICA_CHANGELOG.md`.

**[P2] Sign/book refusals + BOOK_TITLE_MAX — constants verified correct against the plugin.** Title 16 (plugin `DupePreventionListener.kt:241`), sign line 50 (`AntiCheatExpansion.kt:71`), packet 500 (`ClientFingerprintListener.kt:207`), global 2s sign rate (`CheatPreventionListener.kt:1660-1667`) — all match `PayloadRules.kt:43-61` exactly (deliberately tighter than vanilla's 32-char titles). The remaining defect is purely UX: `EnforcementService.kt:404-430` refuses silently; a player editing two signs within 2s has the second edit dropped with no message. Fix: action-bar/chat feedback on refusal.

**[P2] `failOnNoDiscoveredTests=false`** (build.gradle.kts:54, allprojects) — remove the line.

### New findings

1. **[P2] Riptide first-tick false clamp (stale state snapshot).** `snapshotMovementState` (EnforcementService.kt:340-367) samples at END_CLIENT_TICK, but `sanitizeMovement` (:264-317) evaluates packets sent *during* the tick — against the previous tick's state. A trident riptide launch sets `isAutoSpinAttack` and the launch delta in the same tick the packet is sent, so the first flight packet is evaluated with `riptiding=false` → clamped to ~0.41/t while the client continues at full speed → transient server-vs-client position desync at every launch. Plugin anticheat exempts riptide (no VL), but position-based server logic briefly sees the stale spot. Fix: live-read the volatile booleans (`player.isAutoSpinAttack`, `isFallFlying`) in `sanitizeMovement` instead of trusting the snapshot.
2. **[P2] `joinServer` race vs real server login.** `ClientApiAuth.doFlow` step 2 (`ClientApiAuth.kt:264`) calls `sessionService().joinServer(serverId)` on a worker thread; the flow spans seconds (10s timeouts), and `ImperiumOnline` re-mints on every DISCONNECT (ImperiumOnline.kt:102) and launcher token refresh (:142-160). If the player reconnects while a flow is in flight, the client's joinServer can overwrite the vanilla login's join record → "Failed to verify username / Invalid session". Auto-reconnect makes this systematic. Fix: defer the flow while `Minecraft.getInstance().currentServer != null` or a ConnectScreen is up. (Token storage verified memory-only, never logged.)
3. **[P2] Legion API fallback can clobber channel member names / leak state across sessions.** `ImperiumOnline.LegionApiFallback.applyIfVacant` (ImperiumOnline.kt:199-222) applies `memberNames` regardless of ownership; `ImperiumChannels.applyLegion` already accepts them (ImperiumChannels.kt:160-165) — the moment the plugin adds members to the channel, a late DISCONNECT-triggered API response can overwrite the fresh set after rejoin. The disconnect-triggered refill also means the legion HUD renders while in the main menu or on other servers. Fix: gate member-name application on the ownership flag; skip the fallback when `currentServer == null`.
4. **[P3] `minecraft:register` bypass in ChannelGuard** (ChannelGuard.kt:53,58,84-85): raw vanilla `minecraft:register`/`minecraft:unregister` payloads pass unfiltered because namespace `minecraft` is allowed — channel names from mods using the vanilla register packet reach the plugin's payload review. Rebuild channel-name lists for those ids too. The reflective fail-open (140-149) has the same silent effect.
5. **[P3] `applyFeatures` clears grants before validating content** (ImperiumClientSession.kt:128-135): a parseable but structurally wrong payload wipes allowed/limited/denied/budgets mid-session → everything demotes to LOCKED until the next push. Parse into locals, commit only on success.
6. **[P3] Breadcrumbs unbounded + full-config save per drop** (BreadcrumbManager.kt:81-86 → :48,73-78): every drop re-parses and synchronously rewrites the entire config; `nextName()` is O(n); no cap. Cap (e.g. 500) + debounce.
7. **[P3] SpotifyLocalPoller leaks an HttpClient + executor per toggle** (:90-119) — daemon threads, cosmetic.
8. **[P3] Enforcement hot path pays an atomic increment + `synchronized(WeakHashMap)` lookup per outgoing packet** even off-scope (EnforcementService.kt:92, 158-170, 241) — negligible at player scale; a per-Connection boolean would make the common case lock-free. `WATER_CEILING=1.2` is deliberately stricter than the plugin's water allowance — safe direction.
9. **Translation parity test does stale-key detection only** — new features ship untranslated silently; narrower than the name implies.
10. **The `enchantedmc` module** is Alpine/EnchantedMC parity automation (AutoClick/AutoFish), reimplemented clean — no third-party code; both features re-check `ServerGating.enforce` and reset on DISCONNECT. Package name is branding debt.

### Cross-repo notes — the headline finding of pass 2

**MISMATCH: the plugin implements zero `imperiumclient:*` channels on master** (verified against ImperiumMC-Plugin @ master `9692d523`: no hello receiver, no features sender, no legion/boosts/cooldowns pushers; `ImperiumClientIntegrationService` does not exist; the plugin's only client messaging is `labymod3:main` + `lunarclient:pm`, `ClientControlService.kt:25-27`). Consequences on the live server today: `serverResponded()` never true → movement enforcement inert (by design), **auto_fish/auto_click permanently LOCKED for real players**, boost/cooldown HUDs permanently empty, legion HUD fed only by the API fallback. **Context update (2026-10-24): the server half exists on branch `kotlin-migration-collab`** (`ImperiumClientIntegrationService.kt`, `ImperiumClientDataChannels.kt`, contract tests; work-order item 23 on RomanicaClient#12) — the audit verdict applies to master only, and the remaining work is legion/`worldedit:cui`/boosts/cooldowns per RomanicaPlugin#12. Everything else that IS implemented on both sides agrees byte-for-byte (all enforcement constants, grace windows, movement ceiling formula term-for-term, domain allowlists). Full audit: [cross-repo audit doc in RomanicaPlugin](https://github.com/Romanica-MC/RomanicaPlugin/blob/master/docs/reviews/2026-10-23-cross-repo-audit.md).

### Revised recommendations

1. **Close the client↔plugin contract gap**: implement the `imperiumclient:*` channel half in the plugin (hello/features/legion/boosts/cooldowns — payload shapes documented at `ImperiumChannels.kt:31-49`) or deship the dormant client modules behind a build flag.
2. **Fix the two player-visible bugs now**: the Steve-head/NPE block (3-line `UUIDHelper.fromUndashed` fix) and the riptide first-tick clamp (live-read volatile movement state).
3. **Gate `joinServer` auth behind "no active/connecting server"** to remove the invalid-session race.
4. **One-constant social-API hard-disable** (`Constants.ENABLED=false`) + delete `checkAPIStart`; repoint the Modrinth link; replace `CHANGELOG.md`; set `failOnNoDiscoveredTests=true`.
5. **Add refusal feedback + allowlist hardening**: action-bar messages for held/refused sign and book packets; filter raw `minecraft:register` payloads in ChannelGuard.

---

## Pass 3 Addendum — RomanicaClient (2026-10-24)

Reviewed commit `738a41155` (main). Upstream fetched 2026-10-24: **0 commits on upstream/dev since the fork point** — zero drift.

### Correction to pass 1: this IS a git-history fork

`git merge-base HEAD upstream/dev` = `52157c539` = upstream/dev's **tip** (2026-10-18) — commit `861148730` "docs: fork AxolotlClient as ImperiumMC Client" sits directly on top of it. Pass 1's "template snapshot, not a git-history fork" is wrong; the fork point is exact and recent. Since the fork: 26 upstream files modified in place (~1,853 deleted, 661 added) — and **435 upstream Java files were delete+re-added as Kotlin** in 9 conversion waves. All 26 in-place modifications are deliberate (Alpine-parity behavior mixins, rebrand, build); **zero accidental upstream edits found**. The Kotlin conversion, not the 26 files, is the standing merge liability: any future upstream fix to those 435 files must be hand-ported; a `git merge upstream/dev` would be delete/modify conflicts across the codebase. **No upstream-tracking policy exists** (no watch mechanism, no documented process). Upstream history security-grep: only crash fixes, nothing security-shaped to inherit — current exposure nil because fork point == upstream tip; the risk is prospective (upstream commits daily; their HTTP/skin/API-websocket fixes would land unnoticed).

Top in-place diffs worth knowing: `ChatHudMixin` (+430 lines: timestamps, compact chat, smooth scroll, highlights), `fabric.mod.json` (rebrand + enforcement mixins + 2 entrypoints), nametag background modes, `ClientBrandRetrieverMixin` (brand → "ImperiumClient"), lang files (+694 custom strings).

### Distro/version consistency

The served launcher distro pins `imperium-client:0.2.0-beta.24` → the GitHub release jar; SHA256 + size verified byte-exact against the asset. But:

1. **[P1] The distributed jar misreports its own version.** The beta.24 release's `fabric.mod.json` says `"version": "0.1.0-alpha.1+26.3"` — the fix (`1f5ffba28`, "version tracks the release line") landed **after** the tag. Every launcher-installed player runs a mod claiming alpha.1: support triage, the mod list, and the SemVer update-check path all read the wrong value. The tree is fixed (beta.25) but the released artifact predates it. Re-cut beta.25 from the current tree, pin it in `distribution.json`, and add a release workflow asserting `fabric.mod.json` version == tag.
2. **[P2] `publishMods` targets upstream's Modrinth/CurseForge projects** (`versions/latest/build.gradle.kts:157-189` — Modrinth `p2rxzX0q` verified to be AxolotlClient's project, CurseForge 809392 inherited). With tokens it either fails auth or cross-publishes Romanica builds to AxolotlClient's pages. Create own projects or delete the block. (Also: the release asset name `RomanicaClient-…` doesn't match the build output `ImperiumClient-…`, so every release depends on an undocumented manual rename+upload — which is how #1 happened.)
3. Minor: distro pins `fabric-loader 0.20.1` vs gradle's `0.19.5` (works — mod requires ≥0.18 — but the assembled combo isn't what CI builds against).

### New findings

1. **[P2] Minimap retains the last `ClientLevel` across world switches.** `MinimapTexture.level` (`modules/minimap/MinimapTexture.kt:94-98`) is only ever set, never nulled; `MinimapHud.tick()` early-returns when `player == null`, so on disconnect nothing clears it — the stale level (chunk cache, entity lists, block entities) stays strongly reachable from the singleton HUD entry while sitting in the menu, across server switches. Fix: null it in `tick()` when `player == null` or on DISCONNECT.
2. **[P3] Minimap leaks dead `TextureManager` entries** — `rebuild()`/`bakeMask()` close old `DynamicTexture`s but never `textureManager.release()` the versioned ids (`minimap/map/v$N`, `minimap/mask/v$N`); every map-size change/rotation toggle accumulates a dead entry for the session.
3. **[P3] HUD editor saves the whole config category per nudge keypress** (`ImperiumHudEditScreen.kt:478-487` → save on every key event, synchronously) — same class as the breadcrumb finding; debounce or save on close.
4. **[P3] `EmoteDispatch.reset()` has no production caller** — harmless until a `imperiumclient:cosmetics` dispatcher lands; wire it into the DISCONNECT handler then.

**Clean bills:** `media` module (volatile slot, lenient parsing, no Discord RPC shipped), `ClientApi.kt` non-auth endpoints (sealed `FetchResult`, lenient parsers, single 401 retry), `HudEditorHistory` (bounded 64, coalescing), `ImperiumFonts` (Barlow TTFs + OFL license present), translations — **the 4 parity locales (`pt_br`, `de_de`, `es_es`, `fr_fr`) all exist as real translations** (added `f9e4aef38`) — this corrects the work-order issue's housekeeping item claiming only `en_us.json` exists; the remaining work there is a presence test, not translation. Legions/enchantedmc collections capped and disconnect-cleared; WECUI clears on DISCONNECT.

### Recommendations

1. Re-cut the live artifact (beta.25) with a version-asserting release workflow + distro pin — the beta.24 mislabel must not recur.
2. Repoint or remove `publishMods` (upstream's projects).
3. Adopt the one-paragraph upstream-sync policy (fork point `52157c539`, monthly `git log 52157c539..upstream/dev`, "fixes to the 435 Kotlin-converted files are hand-ported") in CONTRIBUTING.md.
4. Fix the two minimap leaks (null `level` on disconnect; `textureManager.release()` old ids).
5. Debounce HUD-editor saves; wire `EmoteDispatch.reset()` into DISCONNECT.

---

## Pass 4 Addendum — RomanicaClient (2026-10-24): hot paths + test coverage map

### Hot-path costs (top items, all verified)

1. **[P2] The title screen throws and catches an exception every frame** — `ImperiumTitleScreen.kt:185-189` (`UUID.fromString` on the undashed uuid, called from `extractRenderState:135-138`): ~60 exception throws/s for every player sitting at the title screen. This is the pass-2 Steve-head bug quantified — the 3-line `UUIDHelper.fromUndashed` fix resolves bug and cost together.
2. **[P2] `CannonTrajectory` scans all rendered entities every frame by default** (`CannonTrajectory.kt:81,85,144-160` — `enabled` and `predictLiveTnt` both default true): O(entities) instanceof scan per frame in-world even with no TNT; with flights, up to ~200 `Point` + `Vec3` allocations + a `BlockPos` per solid-check per frame per flight. Largest default-on per-frame custom loop. Add a cheap early-out (distance check before instanceof) and consider defaulting `predictLiveTnt` off.
3. **[P3] Menu card icons recomputed per card per frame** — `CarvedTheme.specificIconFor` (`:233-324`, called from `ImperiumMenuScreen.kt:919,1220`): string concat + lowercase + ~80 `contains` + a fresh `ItemStack` per card per frame while the menu is open (~40-70 cards). Compute once at menu build.
4. **[P3] `CarvedTheme.drawStoneFill` blits one sprite per 16px tile** (`:75-87`) — a full-screen panel at 1080p ≈ ~8,100 blits/frame; bake to a `DynamicTexture`.
5. **[P3] HUD render loop rebuilds + re-sorts the entry list every frame** (`HudManager.kt:99-101`) — new ArrayList + sort + comparator allocation per frame, paid even when `HudFrameCache` replays. Cache and invalidate on mutation.
6. **[P3] Per-frame allocations in the status HUDs** — `Color(...)` inside the row loop (`CooldownsHud.kt:114`), `String.format` per row (`:105`), `BoostRegistry.active()` stream/sort/`toList` twice per frame (`BoostsHud.kt:63,96`), `NowPlayingHud.formatTime` per frame.
7. **[P3] Minimap `shiftBy` clones 3 arrays per grid-shift** (~1.5 MB/s churn at 256²/zoom 1) + per-frame `Identifier` allocations (`MinimapHud.kt:180,341`).
8. **Quantified non-issues:** the enforcement per-packet cost is ~tens of ns/packet (negligible — pass 2's concern closed with numbers); RAW_UTF8/JSON parsing is strictly off the general packet path; `snapshotMovementState` runs unconditionally every tick (~1-2µs — the clearest "runs with the feature off" item; add the cached-scope early-out).

Config-gating verdict: good overall — HUD entries, gizmos, AutoClick/AutoFish, media/emote/online tick handlers all gate correctly; the offenders are #1, #2, and the unconditional tick snapshot.

### Gametest coverage map

32 real suites cover: enforcement (scope/allowlist/ChannelGuard/sign/book), movement envelope, protocol (hello/features), channels (legion/boosts/cooldowns + malformed drops), session tiers + budgets, the full auth state machine (incl. the legion fallback ownership semantics — pass 2's finding is behavior-pinned today), gated modules, legions/breadcrumbs, cannon trajectory, minimap, fullscreen map, media, emote wheel, titles, HUD editor, menu + CarvedTheme (both contexts), WECUI, chat/inventory/environment/render/hitboxes, AutoEat, performance. **Highest-value missing tests:** CooldownsHud's hotbar `ItemCooldowns` scan + collapse-to-zero; `LegionHud.buildLabel`/`formatPower` (pure pins); the auth session-watch deferral while `currentServer != null` (pass 2's race); the title-screen UUID derivation (would have caught the Steve head); `HudEditorHistory` bounded-64 eviction; SpotifyLocalPoller start/stop idempotence; minimap disconnect regression (level nulling); `minecraft:register` filter (once fixed).

### Docs-vs-implementation

- `server-gated-features.md` rule 4 promises a one-time AFK acknowledgement — none exists in AutoFish/AutoClick (stale).
- `legions-tooling.md` still says the `imperiumclient:legion` receiver is "(when written)" — it shipped and is gametest-pinned.
- `roadmap.md` M5 marked done while its body lists unimplemented deliverables, and "live both sides" is true only of the plugin branch, not master.
- `module-settings.md` rows for shipped modules list Alpine's options, not the client's (add an implemented-marker).
