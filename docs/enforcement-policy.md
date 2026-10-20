# Enforcement interceptor: replace vs refuse policy

Scope: the local rules enforcement layer (issue #15 item 12), whose single outgoing decision
point is `EnforcementService.processOutgoing` in
`versions/latest/src/main/java/io/github/axolotlclient/imperium/enforcement/EnforcementService.java`,
installed by `ConnectionEnforcementMixin` on `Connection.send`. It only ever acts on connections
that pass the scope rule (real network connection to an allowlisted ImperiumMC host; there is no
player-facing toggle).

The hook returns one of three verdicts per packet:

- pass: the packet is sent unchanged;
- refuse: `null` is returned and the packet is not sent at all;
- replace: a new packet is returned and that is what goes on the wire.

## Principle

Refuse beats rewrite, unless the rewrite preserves server-visible semantics.

A refusal is honest: the packet never left, the server sees nothing, and no state was invented.
A replacement is only acceptable when the server cannot distinguish the rewritten packet from a
legitimate client sending it, and the player's intent survives. Concretely, a replacement is
allowed only when all of the following hold:

1. the packet type is on the allowlist below;
2. the rewrite provably stays inside what the server itself accepts as legal (never at or past a
   threshold the server would flag);
3. refusing instead would itself corrupt visible behaviour (desync, rubber-banding, or a protocol
   invariant violation worse than the rewrite).

Anything that carries player-authored content (chat, signs, books, commands, interactions) is
never rewritten. Truncating a sign line or a book page would put words on the wire the player
never wrote, and the server plus other players would render them as authored text. Those packets
are refused: the player's own client shows the failure, the server sees nothing.

## Packet types that may be REPLACED

### `ServerboundMovePlayerPacket.Pos` / `PosRot` (movement clamping)

`sanitizeMovement` clamps the outgoing position delta to `MovementEnvelope`
(`imperium/enforcement/MovementEnvelope.java`) and returns a rebuilt `Pos`/`PosRot` packet with
the clamped coordinates.

Why replacement, not refusal:

- Movement packets are one per tick while moving. Refusing them freezes the server-side player
  while the client keeps moving: immediate desync, then a teleport correction (lagback) on the
  next server position sync. That is exactly the harm the layer exists to prevent.
- The replacement preserves server-visible semantics by construction: the horizontal vector is
  scaled to the ceiling with direction preserved and dy clamped to the legal arc, so the server
  sees a position a legitimate client could have sent. The envelope mirrors the plugin's own
  `CheatPreventionListener` / `MovementSimulationCheck` thresholds with a 0.98 safety factor, so
  the clamp lands strictly before anything the server would flag. A legit player whose delta is
  inside the envelope is passed through untouched; only cheat-grade deltas get scaled down.
- The clamp is additionally gated on the ImperiumMC handshake having answered this session, so
  the rewrite never runs against a server that did not opt into the trusted-client protocol.

### `ServerboundCustomPayloadPacket` carrying Fabric's channel registration (`c:register`)

`guardCustomPayload` rebuilds the Fabric register payload with non-allowlisted channels removed
(`imperium/enforcement/ChannelGuard.java`) instead of dropping it.

Why replacement, not refusal:

- The register payload is an announcement ("these are the channels I may speak on"). Dropping it
  entirely would break Fabric's own networking negotiation for the channels that ARE allowed,
  which is collateral damage out of proportion to the rule.
- The rewrite preserves semantics because the removed channels are exactly the ones the guard
  refuses anyway: the rebuilt registration remains truthful about what can actually be sent.
  Removing a channel from the announcement changes nothing the server would otherwise receive
  from that channel, because it would have been refused.
- If the rebuilt list would be empty, the packet is refused instead (an empty registration is
  not a meaningful announcement). If Fabric internals change and the reflective rebuild fails,
  the payload is passed through unfiltered and the failure is logged once: fail-open here is the
  lesser risk, since the per-channel refuse rule still holds for actual traffic.

## Packet types that are REFUSED, never rewritten

- `ServerboundSignUpdatePacket` violating `PayloadRules`
  (`imperium/enforcement/PayloadRules.java`): line longer than 50 chars, joined payload over 500
  chars (the crash-exploit signature), or inside the 1-per-2-seconds edit window. Refused, not
  truncated: the server would render whatever bytes arrive as text the player ostensibly wrote.
- `ServerboundEditBookPacket` violating `PayloadRules`: title over 16 chars, over 100 pages, a
  page over 8192 chars, or over 100 non-ASCII chars per page (the unicode book-dupe signature).
  Refused for the same reason.
- `ServerboundCustomPayloadPacket` on a non-allowlisted plugin channel: refused outright, so mod
  fingerprints never reach the server's channel review.
- Every other packet type: passed through untouched. Adding a new replace case requires amending
  this document in the same change, with the three conditions above argued explicitly.
