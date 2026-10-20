"""Copy the vanilla item icons and font atlas the mockups use out of a local Minecraft 26.3 client jar.

Mojang's textures are not committed to this repo. Run this once before opening the mockup HTML:
    python extract-mc-assets.py path/to/minecraft-26.3-client.jar
"""
import sys
import zipfile
from pathlib import Path

ITEMS = [
    "armor_stand", "book", "clock_00", "comparator", "compass_16", "ender_pearl", "feather", "fishing_rod",
    "glowstone_dust", "golden_helmet", "iron_sword", "map", "name_tag", "oak_sign", "painting", "potion",
    "recovery_compass_16", "redstone", "spyglass", "totem_of_undying", "writable_book", "gold_ingot", "lead",
]


def main(jar_path: str) -> None:
    out = Path(__file__).parent / "mc"
    out.mkdir(exist_ok=True)
    with zipfile.ZipFile(jar_path) as jar:
        for name in ITEMS:
            (out / f"{name}.png").write_bytes(jar.read(f"assets/minecraft/textures/item/{name}.png"))
        (out / "ascii.png").write_bytes(jar.read("assets/minecraft/textures/font/ascii.png"))
    print(f"extracted {len(ITEMS) + 1} files to {out}")


if __name__ == "__main__":
    main(sys.argv[1])
