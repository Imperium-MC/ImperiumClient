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

The ImperiumMC plugin sends this from its config; other servers can implement the same channel.

## Gated module IDs

| ID | Module |
|---|---|
| `auto_click` | EnchantedMC Clicking |
| `auto_fish` | EnchantedMC Fishing |
