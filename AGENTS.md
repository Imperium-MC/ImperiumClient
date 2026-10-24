# Working agreement for agents (and humans)

**The standing question: "Does this actually do what it says?"** Before calling anything done, prove it works in the real thing. Compiling, type-checking or a green unit run is not proof.

## Evidence, not claims

Every PR that fixes a bug or adds behaviour includes **evidence from a real run** in its description. "Tests pass" alone doesn't count. Evidence means one of:

- a link to a **CI run** that exercised the change, with the relevant log lines or artifacts: the launcher smoke workflow, the client gametest workflow, or a test job
- a screenshot of the changed UI from a CI artifact (gametest screenshots) or a website preview
- a `curl` against the deployed or preview endpoint with its status and body

**Never launch Minecraft, or the launcher's game, on the agent machine.** Only Claude, running on the owner's PC, launches Minecraft. In-game evidence comes from the GitHub Actions workflows (start them with `workflow_dispatch` when they don't run on push). If something can only be checked on a real PC, such as performance numbers, Microsoft sign-in, the installer or a GPU-specific bug, list it under "Not verified" with exact steps. Claude on the owner's PC runs those checks and reports back on the PR.

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

- `./gradlew :latest:build -Pversion=<v>` locally is fine: it builds but doesn't launch the game. **Never run the client (`runClient`, `runClientGameTest`) on the agent machine.**
- **In-game proof** comes from the **`ci.yml` gametest job** (`gh workflow run ci.yml --ref <branch>`; it runs headless under xvfb). Link the run and paste the **count of gametests that ran**. Zero, or "skipped", is a failure (#15). For anything that runs in the client, include a gametest that boots and **joins a world**, and quote `joined the game` from the job log with no `Reported exception`.
- UI changes: screenshots from the gametest artifact (`gametest-run`), next to the mockup they implement. Add a gametest that captures each changed screen.
- Performance: CI uses software rendering, so FPS from it means nothing. Ship the change with a short test plan (scene, settings, what to compare) under "Not verified". Claude on the owner's PC measures on real hardware.

## Kotlin and Mixin rules (learned the hard way)

- **`@Shadow` properties are `private`** (`@Shadow private var x: T? = null`). A public one generates getters and setters that Mixin merges into the target class and that **replace real methods**: `setLevel` overwrote `Minecraft.setLevel` and crashed every world join (fixed in beta.21). If a `@JvmField` must stay public, say why in a comment.
- Shadow initialisers are merged into the target's constructors, so don't give shadows real initial values.
- When unsure, export the mixed classes (`-Dmixin.debug.export=true`) and inspect them with `javap` before claiming a mixin is safe.

## Lessons from past misses

- The titles fix (colour loss, clipping) was reported "done" several times and still isn't on `main`. Done means merged, released and visible in a screenshot.
- The domain cutover left code on `imperiummc.net` behind "cutover" comments. Migrations finish in one pass, with a grep to prove it.
