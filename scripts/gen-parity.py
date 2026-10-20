#!/usr/bin/env python3
"""Regenerate the status column of docs/alpine-parity.md from the module registry.

Run manually after changing what the client registers:

    python scripts/gen-parity.py

The script walks the real module registry (AxolotlClient.addBuiltinModules, the common
registerModule list and both HudManager add() lists), maps each registry id onto the
parity table's rows, and rewrites ONLY the status column of the module map table.
Everything else in the doc is left alone; statuses are never guessed green:

  - a row marked open (X) whose subject resolves to a registered module flips to "shipped";
  - rows already partial keep their status (the generator cannot judge completeness),
    with an HTML comment pointing at the registered ids for human re-review;
  - a green row whose named equivalent cannot be found anywhere in the code is demoted
    to "?" with a comment instead of silently staying green;
  - superseded duplicate rows (same Alpine subject, later row exists) are dropped.

Statuses used in the table: "shipped" exists in Axolotl (may still need Alpine-style
settings), "partial" some of it exists, "open" build new, "server-gated", and "?"
mapping unverifiable from the registry. Plain stdlib only.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs" / "alpine-parity.md"

MODULE_SOURCES = [
    ROOT / "versions/latest/src/main/java/io/github/axolotlclient/AxolotlClient.java",
    ROOT / "common/src/main/java/io/github/axolotlclient/AxolotlClientCommon.java",
]
HUD_SOURCES = [
    ROOT / "versions/latest/src/main/java/io/github/axolotlclient/modules/hud/HudManager.java",
    ROOT / "common/src/main/java/io/github/axolotlclient/modules/hud/HudManagerCommon.java",
]
CODE_ROOTS = [ROOT / "common/src", ROOT / "versions/latest/src"]

STATUS_SHIPPED, STATUS_PARTIAL, STATUS_OPEN = "shipped", "partial", "open"
STATUS_GATED, STATUS_UNKNOWN = "server-gated", "unknown"
EMOJI = {
    STATUS_SHIPPED: "✅",
    STATUS_PARTIAL: "🟡",
    STATUS_OPEN: "❌",
    STATUS_GATED: "🔒",
    STATUS_UNKNOWN: "?",
}
EMOJI_TO_STATUS = {v: k for k, v in EMOJI.items()}

# Registry arguments that are not plain Class.getInstance() calls.
SPECIAL_REGISTER_ARGS = {"getApiOptions": "APIOptions"}

# Alpine (sub)feature names whose implementing identifier is not derivable from the
# name. Empty list = known to NOT be implemented; absent from the map = derive mechanically.
ALIASES = {
    "factions": ["LegionHud"],
    "breadcrumbs": ["BreadcrumbsHud"],
    "cannon_playback": ["CannonTrajectory"],
    "cannon_view": ["CannonTrajectory"],
    "patchcrumbs": [],
    "status hud": ["LegionHud"],
    "boosts": ["BoostsHud"],
    "cps": ["CPSHud"],
    "fps": ["FPSHud"],
    "tps": ["TPSHud"],
    # The block outline options live on the common config (outlineMode/block_outlines.*).
    "block_overlay": ["outlineMode"],
}

CAMELCASE_ID = re.compile(r"\b[A-Z][A-Za-z0-9]*[a-z][A-Za-z0-9]*\b")
BACKTICK_ID = re.compile(r"`([A-Za-z0-9_.]+)`")


def walk_registry() -> set[str]:
    registry = set()
    for path in MODULE_SOURCES:
        for arg in re.findall(r"registerModule\(([^)]+)\)", path.read_text(encoding="utf-8")):
            arg = arg.strip()
            if arg.startswith("new "):
                registry.add(arg[4:].strip("() ").split(".")[-1])
            elif "(" in arg:
                name = re.sub(r"\(\)$", "", arg).split(".")[-1]
                registry.add(SPECIAL_REGISTER_ARGS.get(name, name))
            else:
                registry.add(arg.split(".")[-1])
    for path in HUD_SOURCES:
        for name in re.findall(r"\badd\(new ([A-Za-z0-9_.]+)\(\)\)", path.read_text(encoding="utf-8")):
            registry.add(name.split(".")[-1])
    return registry


def source_word_index() -> set[str]:
    words = set()
    for root in CODE_ROOTS:
        for path in root.rglob("*"):
            if path.suffix in (".java", ".kt"):
                words.update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", path.read_text(encoding="utf-8", errors="replace")))
    return words


def camel(token: str) -> str:
    return "".join(part.capitalize() for part in re.split(r"[/_ ]+", token) if part)


def alpine_tokens(alpine_cell: str) -> list[str]:
    text = alpine_cell.replace("**", "")
    text = re.sub(r"\(.*?\)", lambda m: " " + m.group(0)[1:-1] + " ", text)  # expand parenthesized subfeatures
    text = text.replace("(+ ", " ").replace(" / ", " , ")
    tokens = []
    for token in re.split(r"[,/]", text):
        token = token.strip().strip("+").strip()
        if token and not token.startswith("(") and len(token) > 2:
            tokens.append(token.lower())
    return tokens


def candidates_for(token: str) -> list[str]:
    if token in ALIASES:
        return list(ALIASES[token])
    base = camel(token)
    return [base, base + "Hud"]


class Row:
    def __init__(self, alpine: str, equiv: str, status: str, detail: str, line_no: int):
        self.alpine = alpine
        self.equiv = equiv
        self.status = status
        self.detail = detail
        self.line_no = line_no
        self.comment = None
        self.flipped = False
        self.dropped = False

    @property
    def key(self) -> str:
        return re.sub(r"\s+", " ", self.alpine.replace("**", "").strip().lower())


def parse_rows(doc: str) -> tuple[list[Row], int, int]:
    lines = doc.splitlines()
    rows, header_idx, table_end = [], None, None
    for i, line in enumerate(lines):
        if line.startswith("| Alpine module |"):
            header_idx = i
        elif header_idx is not None and line.startswith("|") and not set(line) <= set("|- "):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) >= 3:
                emoji = cells[2][0] if cells[2] else ""
                status = EMOJI_TO_STATUS.get(emoji, STATUS_UNKNOWN)
                detail = cells[2][1:].strip()
                rows.append(Row(cells[0], cells[1], status, detail, i))
        elif header_idx is not None and rows and not line.startswith("|"):
            table_end = i
            break
    return rows, header_idx, table_end


def resolve(row: Row, registry: set[str], code_words: set[str]) -> tuple[list[str], list[str], list[str]]:
    ids = set(CAMELCASE_ID.findall(row.equiv)) | set(BACKTICK_ID.findall(row.equiv))
    named = set()
    for token in alpine_tokens(row.alpine):
        for cand in candidates_for(token):
            named.add(cand)

    registered = sorted(i for i in ids | named if i in registry)
    in_code = sorted(i for i in ids | named if i not in registry and i in code_words)
    # An alias with an explicitly empty target list marks a known-missing subfeature.
    missing = []
    for token in alpine_tokens(row.alpine):
        if token in ALIASES and not ALIASES[token]:
            missing.append(token)
    return registered, in_code, missing


def regenerate(row: Row, registry: set[str], code_words: set[str]) -> None:
    registered, in_code, missing = resolve(row, registry, code_words)
    equiv_is_dash = row.equiv.strip() in {"—", "-", ""}

    if row.status == STATUS_OPEN and registered:
        row.status = STATUS_SHIPPED
        row.detail = ""
        row.flipped = True
        row.comment = "gen: " + ", ".join(registered) + " registered"
        return
    if row.status == STATUS_SHIPPED and missing:
        # A green row naming equivalents the code does not have is a contradiction the
        # generator refuses to keep: flag it instead of guessing.
        row.status = STATUS_UNKNOWN
        row.comment = "gen: previously green but " + ", ".join(missing) + " not found"
        return
    if row.status in (STATUS_SHIPPED, STATUS_PARTIAL) and not registered and not in_code and not equiv_is_dash:
        row.status = STATUS_UNKNOWN
        row.comment = "gen: no mapping to the registry or the code found"
        return
    if row.status == STATUS_PARTIAL and registered:
        row.comment = "gen: " + ", ".join(registered) + " registered; re-review partial note"
        return
    if equiv_is_dash and registered:
        row.comment = "gen: " + ", ".join(registered) + " registered"


def dedupe(rows: list[Row]) -> int:
    by_key: dict[str, list[Row]] = {}
    for row in rows:
        by_key.setdefault(row.key, []).append(row)
    dropped = 0
    for group in by_key.values():
        # Several rows can share a subject through append-style editing; keep the most
        # informative one (a real equivalent beats a bare dash), preferring the latest.
        informative = [r for r in group if r.equiv.strip() not in {"—", "-", ""}]
        keeper = (informative or group)[-1]
        for row in group:
            if row is not keeper:
                row.dropped = True
                dropped += 1
    # A combined row (several subjects in one cell) is superseded when every one of its
    # subjects has a later dedicated row.
    for row in rows:
        names = [t.strip().lower() for t in row.alpine.replace("**", "").split(",") if t.strip()]
        if len(names) > 1:
            later_rows = [other for other in rows if other.line_no > row.line_no and not other.dropped]
            if all(any(n == other.alpine.replace("**", "").strip().lower() for other in later_rows) for n in names):
                if not row.dropped:
                    row.dropped = True
                    dropped += 1
    return dropped


def main() -> None:
    registry = walk_registry()
    code_words = source_word_index()
    doc = DOC.read_text(encoding="utf-8")
    rows, header_idx, table_end = parse_rows(doc)
    if header_idx is None or not rows:
        raise SystemExit("could not find the module map table in " + str(DOC))

    for row in rows:
        regenerate(row, registry, code_words)
    dropped = dedupe(rows)

    kept = [row for row in rows if not row.dropped]
    out_lines = []
    for row in kept:
        status = EMOJI[row.status]
        if row.status == STATUS_UNKNOWN:
            status = "?"
        cell = status + ((" " + row.detail) if row.detail else "")
        if row.comment:
            cell += " <!-- " + row.comment + " -->"
        out_lines.append("| " + row.alpine + " | " + row.equiv + " | " + cell + " |")

    lines = doc.splitlines()
    # The regenerated block replaces the rows between the table header and the next section.
    doc = "\n".join(lines[: header_idx + 2] + out_lines + lines[table_end:]) + "\n"
    DOC.write_text(doc, encoding="utf-8")

    flipped = sum(1 for r in kept if r.flipped)
    unknown = sum(1 for r in kept if r.status == STATUS_UNKNOWN)
    partial_flagged = sum(1 for r in kept if r.comment and "re-review" in r.comment)
    print("registry ids:", len(registry))
    print("rows: kept", len(kept), "dropped (superseded)", dropped)
    print("flipped open->shipped:", flipped)
    print("demoted to '?':", unknown)
    print("partial rows flagged for re-review:", partial_flagged)


if __name__ == "__main__":
    main()
