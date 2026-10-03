"""Export Android launcher densities from the approved imagegen PNG (requires Pillow)."""
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
MASTER = Image.open(Path(__file__).with_name("SenseKey-sensei-master.png")).convert("RGBA")
# Ignore near-invisible alpha noise when centering; retain original antialiasing inside the crop.
CROP = MASTER.crop(MASTER.getchannel("A").point(lambda v: 255 if v >= 8 else 0).getbbox())
IVORY = (245, 240, 229, 255)


def foreground(size, fraction):
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    subject = ImageOps.contain(CROP, (round(size * fraction), round(size * fraction)), Image.Resampling.LANCZOS)
    out.alpha_composite(subject, ((size - subject.width) // 2, (size - subject.height) // 2))
    return out


for density, legacy, adaptive in [
    ("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432),
]:
    folder = ROOT / "app/src/main/res" / f"mipmap-{density}"
    fg = foreground(adaptive, .56)
    fg.save(folder / "ic_launcher_foreground.png", optimize=True)
    ink = ImageChops.multiply(ImageOps.invert(ImageOps.grayscale(fg)), fg.getchannel("A"))
    mono = Image.new("RGBA", fg.size, (0, 0, 0, 0))
    mono.putalpha(ink)
    mono.save(folder / "ic_launcher_monochrome.png", optimize=True)
    old = Image.new("RGBA", (legacy, legacy), IVORY)
    old.alpha_composite(foreground(legacy, .84))
    old.save(folder / "ic_launcher.png", optimize=True)
    mask = Image.new("L", (legacy, legacy))
    ImageDraw.Draw(mask).ellipse((0, 0, legacy - 1, legacy - 1), fill=255)
    old.putalpha(mask)
    old.save(folder / "ic_launcher_round.png", optimize=True)
