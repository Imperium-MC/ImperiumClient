# Server-gated features

Some Alpine modules automate input: EnchantedMC **Clicking** (autoclicker) and **Fishing**
(auto-fish). Most servers ban this, and on ImperiumMC it would undercut the economy and the
anticheat. These modules ship **disabled and locked** and only unlock when the server opts in.

## Rules

1. A gated module cannot be enabled unless the current server has granted it in this session.
2. Grants reset on disconnect. Singleplayer counts as granted.
3. The module shows why it is locked ("This server has not enabled auto-fishing").
4. The one-time "I will not AFK" acknowledgement Alpine shows is kept, in addition to the grant.

## Protocol

Plugin channel `imperiumclient:features`, server → client, sent after join:

```json
{ "version": 1, "allow": ["auto_fish"], "deny": ["freelook"] }
```

- `allow`: gated modules the server permits.
- `deny`: any module the server wants force-disabled for fairness (applies to non-gated modules too).
- Clients ignore unknown IDs. Without the packet, everything gated stays locked and nothing is denied.

### Version 2: tiers and rate budgets

```json
{ "version": 2,
  "allow": ["auto_fish"],
  "limited": ["auto_walk"],
  "deny": [],
  "limits": { "auto_walk": { "per_hour": 30 } } }
```

- `limited`: the limited tier — the module is enabled but rate-budgeted. The client keeps a
  token bucket per id (capacity and refill from `limits`; a limited id without a `limits`
  entry is treated as unbounded) and the module spends one token per activation. When the
  budget is spent the module force-offs with its own tooltip until the bucket refills
  (tokens refill continuously at `amount / window`, so long sessions converge on the
  advertised rate; a fresh session starts with a full bucket).
- `limits`: per-id rate hints. `per_hour` is the documented unit; `per_minute` is accepted
  too (and wins when both are given — the finer window).
- v1 payloads keep working unchanged: no `limited`/`limits` means no limited tier and no
  budgets.

The ImperiumMC plugin sends this from its config; other servers can implement the same channel.

## Gated module IDs

| ID | Module | Tier |
|---|---|---|
| `auto_click` | EnchantedMC Clicking | gated (`allow`) |
| `auto_fish` | EnchantedMC Fishing | gated (`allow`) |
| `auto_walk` | Auto Walk | limited (`limited` + budget) |

## QoL module IDs

These ship **enabled** — the singleplayer-style default — and stay that way on v1 servers;
a `deny` (or the `block_mods` channel) is the only thing that force-offs them.

| ID | Module |
|---|---|
| `auto_eat` | Auto Eat |
| `auto_tool` | Auto Tool |
| `auto_armor` | Auto Armor |
| `auto_reconnect` | Auto Reconnect |
