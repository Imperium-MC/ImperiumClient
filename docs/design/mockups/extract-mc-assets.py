"""Copy the vanilla assets the mockups use out of a local Minecraft 26.3 install.

Mojang's textures are not committed to this repo. Run this once before opening the mockup HTML:
    python extract-mc-assets.py path/to/26.3.jar path/to/assets
where the second path is a launcher's `assets` folder (it holds `indexes/` and `objects/`); the title
panorama lives there, not in the jar. Needs Pillow.
"""
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ITEMS = [
    "book", "clock_00", "compass_16", "ender_pearl", "feather", "fishing_rod", "glowstone_dust",
    "golden_helmet", "iron_sword", "map", "name_tag", "oak_sign", "potion", "recovery_compass_16",
    "redstone", "spyglass", "totem_of_undying", "writable_book",
]


def main(jar_path: str, assets_dir: str) -> None:
    out = Path(__file__).parent / "mc"
    out.mkdir(exist_ok=True)
    with zipfile.ZipFile(jar_path) as jar:
        for name in ITEMS:
            (out / f"{name}.png").write_bytes(jar.read(f"assets/minecraft/textures/item/{name}.png"))
        (out / "ascii.png").write_bytes(jar.read("assets/minecraft/textures/font/ascii.png"))

        skin = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/entity/player/wide/steve.png"))).convert("RGBA")
        head = skin.crop((8, 8, 16, 16))
        head.alpha_composite(skin.crop((40, 8, 48, 16)))
        head.resize((64, 64), Image.NEAREST).save(out / "steve_head.png")

    assets = Path(assets_dir)
    index = next(p for p in sorted((assets / "indexes").glob("*.json")) if "panorama_0" in p.read_text())
    objects = json.loads(index.read_text())["objects"]

    def panorama(i: int) -> Image.Image:
        h = objects[f"minecraft/textures/gui/title/background/panorama_{i}.png"]["hash"]
        return Image.open(assets / "objects" / h[:2] / h).convert("RGB")

    # Left, front and right cube faces side by side, cropped to 16:9: a stand-in for the rotating panorama.
    faces = [panorama(i) for i in (3, 0, 1)]
    size = faces[0].width
    strip = Image.new("RGB", (size * 3, size))
    for i, face in enumerate(faces):
        strip.paste(face, (i * size, 0))
    width = size * 16 // 9
    left = size + size // 2 - width // 2
    strip.crop((left, 0, left + width, size)).resize((1920, 1080), Image.LANCZOS).save(out / "title-bg.jpg", quality=90)

    print(f"extracted {len(ITEMS) + 3} files to {out}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
