# Working agreement for agents (and humans)

**The standing question: "Does this actually do what it says?"** Before calling anything done, prove it works in the real thing. Compiling, type-checking or a green unit run is not proof.

## Evidence, not claims

Every PR that fixes a bug or adds behaviour includes **evidence from a real run** in its description. "Tests pass" alone doesn't count. Evidence means one of:

- a log excerpt showing the fixed path running, for example the game reaching the title screen or joining a world, or a launch smoke verdict from a *fresh* log
- a screenshot of the changed UI, at the scales the issue names
- a `curl` against the deployed or preview endpoint with its status and body

If you couldn't verify something, write **"Not verified: <what and why>"** in the PR. Never write "fixed" for something you haven't seen fixed. A wrong "fixed" costs the owner more than an honest "not verified".

## Tests must be able to fail

- A test that would pass with the fix reverted proves nothing. For a bug fix, write the test first and see it fail on the old code. Say so in the PR.
- A CI step that runs zero tests must fail, not go green. Check the reported count.
- Smoke and integration checks read only output from *this* run, never files left over from a previous one.
- Tests never touch real user data (configs, saves, `%APPDATA%`). Use temp directories.

## Finish the job

- Search for every occurrence before saying a rename or migration is complete. Paste the grep that comes back empty.
- When a spec issue lists N items, the PR states which of the N it covers. Leftovers go in a comment on the issue, not silently dropped.
- Don't release, tag or update the distribution index until the evidence above exists for that build.

## Owner rules

- Never rotate, print or commit secrets.
- Kill processes by exact PID only, never by matching a command line.
- Don't commit Mojang assets. Don't copy files or assets from other clients.
- Don't name other clients in commit messages, README or PRODUCT.md (`docs/` is fine).
- The brand is **Romanica**, the domain is **romanica.net**, and the API is **api.romanica.net**. `imperiummc.net` is retired and being transferred. Never reference it in new code.
- Commits use minimal conventional format (`fix:`, `feat:`, `chore:`), one logical change each.

## How to verify in this repo (RomanicaClient)

- `./gradlew :latest:build -Pversion=<v>`. Run the gametests and **paste the count of tests that ran**. Zero, or "skipped", is a failure, not a pass (#15).
- **In-game proof** for anything that runs in the client: boot to the title screen **and** join a world (singleplayer quick-play or play.romanica.net). Paste the log lines that show it (`joined the game`, `Loaded N advancements`) and confirm there are no `Reported exception` lines.
- UI changes: screenshots at GUI scale 2, 3 and Auto, posted next to the mockup they implement.
- Performance changes: a before and after table (average FPS, 1% lows) from the same scene and settings.

## Kotlin and Mixin rules (learned the hard way)

- **`@Shadow` properties are `private`** (`@Shadow private var x: T? = null`). A public one generates getters and setters that Mixin merges into the target class and that **replace real methods**: `setLevel` overwrote `Minecraft.setLevel` and crashed every world join (fixed in beta.21). If a `@JvmField` must stay public, say why in a comment.
- Shadow initialisers are merged into the target's constructors, so don't give shadows real initial values.
- When unsure, export the mixed classes (`-Dmixin.debug.export=true`) and inspect them with `javap` before claiming a mixin is safe.

## Lessons from past misses

- The titles fix (colour loss, clipping) was reported "done" several times and still isn't on `main`. Done means merged, released and visible in a screenshot.
- The domain cutover left code on `imperiummc.net` behind "cutover" comments. Migrations finish in one pass, with a grep to prove it.
