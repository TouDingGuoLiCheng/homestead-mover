# -*- coding: utf-8 -*-
"""整栋搬家 / homestead_mover 发布封面。

按 封面风格/风格1 规范：青绿底 (3,110,120) + 右侧四行粗白斜体
(附属行 / 主标题两行 / 底行)，1536x1024 RGBA，纯色平涂。
左侧没有人物立绘，改用自绘的低透明度几何母题：几个方块沿虚线收进箱子。
用法：用带 Pillow 的 Python 直接运行，输出同目录 发布封面.png。
"""
from PIL import Image, ImageDraw, ImageFont
import numpy as np
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "发布封面.png"

W, H = 1536, 1024
BG = (3, 110, 120)
SHEAR = 0.14
TEXT_X = 700
MAX_W = W - TEXT_X - 56          # 右边距 56，避免斜切贴边
FONTS = [
    r"C:\Windows\Fonts\Noto Sans SC Bold (TrueType).otf",
    r"C:\Windows\Fonts\msyhbd.ttc",
]

# (文本, 目标行高, 顶部 Y, x)  x=None 表示"居中于主标题块"，稍后算
LINES = [
    ("Minecraft Forge 1.20.1 独立模组", 81, 126, None),
    ("整栋搬家：", 198, 274, TEXT_X),
    ("收纳箱", 215, 501, TEXT_X),
    ("Minecraft模组发布", 96, 777, TEXT_X),
]


def font(size):
    for path in FONTS:
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            continue
    raise SystemExit("找不到可用的中文粗体字体")


def render_line(text, target_h, size=220):
    """在大画布上画字（留足 padding 防裁切），裁墨迹、按行高等比缩放，再整体右斜。"""
    face = font(size)
    pad = size
    canvas = Image.new("RGBA", (len(text) * size * 2 + 2 * pad, size * 3), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.text((pad + 3, pad + 3), text, font=face, fill=(0, 0, 0, 70))
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        draw.text((pad + dx, pad + dy), text, font=face, fill=(255, 255, 255, 255))
    draw.text((pad, pad), text, font=face, fill=(255, 255, 255, 255))

    arr = np.array(canvas)
    ys, xs = np.where(arr[:, :, 3] > 0)
    glyph = canvas.crop((xs.min() - 3, ys.min() - 3, xs.max() + 4, ys.max() + 4))
    scale = target_h / glyph.height
    glyph = glyph.resize((max(1, int(glyph.width * scale)), target_h), Image.Resampling.LANCZOS)

    extra = int(abs(SHEAR) * glyph.height) + 30
    wide = Image.new("RGBA", (glyph.width + extra, glyph.height + 20), (0, 0, 0, 0))
    wide.paste(glyph, (extra // 2, 10))
    cy = wide.height / 2.0
    sheared = wide.transform(wide.size, Image.AFFINE, (1, SHEAR, -SHEAR * cy, 0, 1, 0),
                             resample=Image.BICUBIC)
    arr = np.array(sheared)
    ys, xs = np.where(arr[:, :, 3] > 0)
    return sheared.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def iso_cube(draw, cx, cy, half, alpha):
    """一个等距小方块：顶面 + 左右侧面，纯色低透明度。"""
    top = (255, 255, 255, alpha)
    left = (255, 255, 255, int(alpha * 0.72))
    right = (255, 255, 255, int(alpha * 0.52))
    hq = half // 2
    draw.polygon([(cx, cy - half), (cx + half, cy - hq), (cx, cy), (cx - half, cy - hq)], fill=top)
    draw.polygon([(cx - half, cy - hq), (cx, cy), (cx, cy + hq), (cx - half, cy)], fill=left)
    draw.polygon([(cx + half, cy - hq), (cx, cy), (cx, cy + hq), (cx + half, cy)], fill=right)


def chest(draw, cx, cy, w, h, alpha=235):
    """白描箱体：箱盖 + 箱身 + 锁扣，纯线条不填充。"""
    body = (255, 255, 255, alpha)
    thin = 7
    left, right = cx - w // 2, cx + w // 2
    top, bottom = cy - h // 2, cy + h // 2
    lid = top + h // 3
    draw.rounded_rectangle((left, lid, right, bottom), radius=14, outline=body, width=thin)
    draw.rounded_rectangle((left, top, right, lid + 6), radius=14, outline=body, width=thin)
    draw.line((left + 4, lid + 3, right - 4, lid + 3), fill=body, width=thin)
    latch_w, latch_h = w // 9, h // 6
    draw.rounded_rectangle((cx - latch_w // 2, lid - latch_h // 2, cx + latch_w // 2, lid + latch_h // 2),
                           radius=6, outline=body, width=6)
    draw.ellipse((cx - 7, lid - 7, cx + 7, lid + 7), fill=(255, 255, 255, alpha))


def motif(base):
    """左侧母题：方块沿一条虚线弧收进箱子，暗示"整栋搬走"。"""
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    cubes = [(300, 640, 62, 120), (232, 556, 56, 100), (300, 470, 62, 84),
             (232, 386, 56, 70), (300, 300, 62, 58), (232, 216, 56, 46)]
    for cx, cy, half, alpha in cubes:
        iso_cube(draw, cx, cy, half, alpha)

    # 虚线弧：从最上面的方块一路收进箱盖（二次贝塞尔）
    p0, p1, p2 = (196.0, 196.0), (470.0, 470.0), (268.0, 706.0)
    steps = 44
    for i in range(steps + 1):
        if i % 3 == 2:            # 隔三留一，成虚线
            continue
        t = i / float(steps)
        u = 1.0 - t
        px = u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0]
        py = u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]
        r = 6 - int(2 * t)
        draw.ellipse((px - r, py - r, px + r, py + r), fill=(255, 255, 255, int(80 + 100 * t)))

    chest(draw, 268, 830, 336, 232)
    base.alpha_composite(layer)


def main():
    base = Image.new("RGBA", (W, H), BG + (255,))
    motif(base)

    drawn = []
    for text, target_h, top, x in LINES:
        glyph = render_line(text, target_h)
        if glyph.width > MAX_W:
            scale = MAX_W / glyph.width
            glyph = glyph.resize((MAX_W, max(1, int(glyph.height * scale))), Image.Resampling.LANCZOS)
        drawn.append([glyph, x, top])

    block_left = TEXT_X
    block_right = max(glyph.width + TEXT_X for glyph, _, _ in drawn[1:3])
    first = drawn[0][0]
    drawn[0][1] = (block_left + block_right) // 2 - first.width // 2

    for glyph, x, top in drawn:
        base.alpha_composite(glyph, (max(0, x), top))

    base.save(OUT)
    print("wrote", OUT, base.size)


if __name__ == "__main__":
    main()
