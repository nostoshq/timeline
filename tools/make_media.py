# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 NOSTOS, LLC
"""
Makes the pictures in docs/media from Timeline's own typeface and from screenshots taken on a device.

    python3 tools/make_media.py wordmark            the wordmark, for GitHub's light and dark themes
    python3 tools/make_media.py signals A B C OUT [X Y]  one frame through composite, S-video and RGB, a detail of each
    python3 tools/make_media.py card SHOT OUT       the 1280 x 640 social preview card
    python3 tools/make_media.py icon OUT            the app icon as a 512 x 512 square
    python3 tools/make_media.py feature SHOT OUT    the 1024 x 500 banner for app listings

Only Space Grotesk (SIL Open Font License) is used for type. Colors are Timeline's own: near-black, cream and gold.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "app", "src", "main", "res", "font")
MEDIA = os.path.join(HERE, "..", "docs", "media")

BLACK = (13, 13, 13)
CREAM = (242, 239, 233)
GOLD = (254, 189, 31)
MUTED = (136, 136, 136)
LINE = (31, 31, 31)


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"space_grotesk_{weight}.ttf"), size)


def tracked(draw, xy, text, fnt, fill, tracking):
    """Draws text with extra space between letters, as the launcher sets its headings. Returns the end x."""
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill)
        x += draw.textlength(ch, font=fnt) + tracking
    return x - tracking


def scanlines(img, pitch, strength):
    """Fine dark lines across what is drawn, as the launcher's headlines have them. Transparency is left alone."""
    px = img.load()
    w, h = img.size
    for y in range(h):
        if y % pitch != pitch - 1:
            continue
        for x in range(w):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (int(r * (1 - strength)), int(g * (1 - strength)), int(b * (1 - strength)), a)


def wordmark(ink, scale=4):
    """TIMELINE and its gold cursor, on transparency, drawn large and brought down so the edges are clean."""
    size = 92 * scale
    f = font("bold", size)
    probe = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
    tracking = int(size * 0.16)
    width = sum(probe.textlength(c, font=f) for c in "TIMELINE") + tracking * 8 + size * 0.62
    img = Image.new("RGBA", (int(width + size * 0.2), int(size * 1.25)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    top = int(size * 0.02)
    end = tracked(d, (0, top), "TIMELINE", f, ink + (255,), tracking)
    asc, desc = f.getmetrics()
    base = top + asc
    d.rectangle([end + tracking, base - int(size * 0.13), end + tracking + int(size * 0.52), base], fill=GOLD + (255,))
    scanlines(img, 4 * scale // 2, 0.22)
    img = img.crop(img.getbbox())
    return img.resize((img.width // scale * 2, img.height // scale * 2), Image.LANCZOS)


def make_wordmark():
    os.makedirs(MEDIA, exist_ok=True)
    wordmark(CREAM).save(os.path.join(MEDIA, "wordmark-dark.png"))
    wordmark(BLACK).save(os.path.join(MEDIA, "wordmark-light.png"))


def rounded(size, radius, fill):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=fill)
    return img


def make_signals(a, b, c, out, x=600, y=300):
    """
    Three renders of one frame, side by side: a 280-pixel square of each, from (x, y) of a 1280 x 960 render, shown at
    twice its size so the color fringes, the softness and the scanlines can be seen. 1760 pixels wide, to be shown at
    880, which is the render's own pixels on a high-density screen.
    """
    panels = [("Composite", "NES, SNES, Genesis, PlayStation, Saturn"),
              ("S-video", "Nintendo 64"),
              ("RGB", "Arcade; Dreamcast VGA and component alike")]
    size, gap, top = 560, 40, 132
    sheet = Image.new("RGB", (3 * size + 2 * gap, top + size), BLACK)
    d = ImageDraw.Draw(sheet)
    head, sub = font("bold", 34), font("medium", 22)
    for i, (path, (name, which)) in enumerate(zip((a, b, c), panels)):
        im = Image.open(path).convert("RGB")
        crop = im.crop((x, y, x + size // 2, y + size // 2)).resize((size, size), Image.NEAREST)
        left = i * (size + gap)
        sheet.paste(crop, (left, top))
        tracked(d, (left, 22), name.upper(), head, CREAM, 6)
        d.text((left, 76), which, font=sub, fill=MUTED)
    sheet.save(out)


def screen(shot, width, radius=14):
    """A screenshot as a lit panel: rounded, a hairline edge, and a soft shadow under it."""
    im = shot.convert("RGB")
    im = im.resize((width, int(im.height * width / im.width)), Image.LANCZOS)
    pad = 60
    out = Image.new("RGBA", (im.width + pad * 2, im.height + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.alpha_composite(rounded((im.width, im.height), radius, (0, 0, 0, 210)), (pad, pad + 16))
    out.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    mask = rounded((im.width, im.height), radius, (255, 255, 255, 255)).getchannel("A")
    panel = Image.new("RGBA", im.size, (0, 0, 0, 0))
    panel.paste(im, (0, 0), mask)
    out.alpha_composite(panel, (pad, pad))
    ImageDraw.Draw(out).rounded_rectangle([pad, pad, pad + im.width - 1, pad + im.height - 1], radius, outline=(52, 52, 52, 255), width=2)
    return out


def make_card(shot, out):
    """GitHub's social preview: 1280 x 640, the wordmark and a line on the left, the screen on the right."""
    card = Image.new("RGBA", (1280, 640), BLACK + (255,))
    d = ImageDraw.Draw(card)
    mark = wordmark(CREAM, scale=4)
    mark = mark.resize((400, int(mark.height * 400 / mark.width)), Image.LANCZOS)
    card.alpha_composite(mark, (76, 222))
    f = font("medium", 26)
    d.text((76, 222 + mark.height + 34), "A home screen for", font=f, fill=CREAM)
    d.text((76, 222 + mark.height + 70), "retro handhelds.", font=f, fill=CREAM)
    tracked(d, (76, 548), "FREE  ·  OPEN SOURCE  ·  FROM NOSTOS", font("bold", 14), GOLD, 4)
    panel = screen(Image.open(shot), 640)
    card.alpha_composite(panel, (1280 - panel.width + 10, (640 - panel.height) // 2))
    card.convert("RGB").save(out)


def make_icon(out, size=512):
    """The app's icon as a flat square, from the same shapes as res/drawable/ic_launcher_foreground.xml (108 units)."""
    s = 8
    big = size * s
    u = big / 108
    img = Image.new("RGB", (big, big), (0, 0, 0))
    d = ImageDraw.Draw(img)
    # M30,30 h48 v10 h-19 v40 h-10 v-40 h-19 z
    d.polygon([(30 * u, 30 * u), (78 * u, 30 * u), (78 * u, 40 * u), (59 * u, 40 * u), (59 * u, 80 * u),
               (49 * u, 80 * u), (49 * u, 40 * u), (30 * u, 40 * u)], fill=CREAM)
    d.rectangle([64 * u, 72 * u, 80 * u, 80 * u], fill=GOLD)
    img.resize((size, size), Image.LANCZOS).save(out)


def make_feature(shot, out):
    """The 1024 x 500 banner app stores show above the screenshots."""
    card = Image.new("RGBA", (1024, 500), BLACK + (255,))
    d = ImageDraw.Draw(card)
    mark = wordmark(CREAM, scale=4)
    mark = mark.resize((330, int(mark.height * 330 / mark.width)), Image.LANCZOS)
    card.alpha_composite(mark, (60, 180))
    f = font("medium", 22)
    d.text((60, 180 + mark.height + 28), "A home screen for", font=f, fill=CREAM)
    d.text((60, 180 + mark.height + 58), "retro handhelds.", font=f, fill=CREAM)
    panel = screen(Image.open(shot), 500, radius=12)
    card.alpha_composite(panel, (1024 - panel.width + 14, (500 - panel.height) // 2))
    card.convert("RGB").save(out)


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else ""
    if what == "wordmark":
        make_wordmark()
    elif what == "icon":
        make_icon(sys.argv[2])
    elif what == "feature":
        make_feature(sys.argv[2], sys.argv[3])
    elif what == "signals":
        make_signals(*sys.argv[2:6], *map(int, sys.argv[6:8]))
    elif what == "card":
        make_card(sys.argv[2], sys.argv[3])
    else:
        print(__doc__)
