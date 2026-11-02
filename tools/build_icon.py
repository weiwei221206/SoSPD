"""Compose official v4.0.2 sprites losslessly into Android launcher layers (Pillow required)."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SIZE = 432
SCALE = Image.Resampling.NEAREST


def main():
    tiles = Image.open(ROOT / "artwork/upstream/environment/tiles_sewers.png").convert("RGBA")
    items = Image.open(ROOT / "artwork/upstream/sprites/items.png").convert("RGBA")
    # DungeonTileSheet.FLOOR, FLOOR_ALT_1 and FLOOR_ALT_2: atlas cells 0, 6 and 12.
    logical = Image.new("RGBA", (108, 108))
    variants = [0, 6, 12]
    for y in range(3):
        for x in range(3):
            index = variants[(x + y * 2) % 3]
            tile = tiles.crop((index * 16, 0, index * 16 + 16, 16)).resize((48, 48), SCALE)
            logical.alpha_composite(tile, (x * 48, y * 48))
    background = logical.resize((SIZE, SIZE), SCALE)
    # ItemSpriteSheet.CRYSTAL_KEY = xy(1,4)+9; official frame is 8 by 14 pixels.
    key = items.crop((144, 48, 152, 62))
    foreground = Image.new("RGBA", (108, 108))
    foreground.alpha_composite(key.resize((32, 56), SCALE), (38, 26))
    foreground = foreground.resize((SIZE, SIZE), SCALE)
    dest = RES / "drawable-nodpi"
    dest.mkdir(parents=True, exist_ok=True)
    background.save(dest / "ic_launcher_background.png")
    foreground.save(dest / "ic_launcher_foreground.png")
    preview = Image.alpha_composite(background, foreground)
    preview.resize((512, 512), SCALE).save(ROOT / "artwork/launcher-preview.png")
    paths = []
    for y in range(14):
        for x in range(8):
            if key.getpixel((x, y))[3] > 128:
                px, py = 38 + x * 4, 26 + y * 4
                paths.append(f"M{px},{py}h4v4h-4z")
    vector = ('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
              'android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
              f'    <path android:fillColor="#FFFFFFFF" android:pathData="{" ".join(paths)}" />\n</vector>\n')
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector, encoding="utf-8", newline="\n")
    print("Generated official sewer-floor and crystal-key launcher layers.")


if __name__ == "__main__":
    main()
