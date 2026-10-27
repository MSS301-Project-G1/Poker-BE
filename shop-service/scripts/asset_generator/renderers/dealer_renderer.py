import io
import math
from PIL import Image, ImageDraw, ImageOps, ImageFilter

TARGET_SIZE = (512, 512)

def draw_mini_card(draw, x, y, rank, suit, is_red=False, angle=0):
    w, h = 64, 88
    card_img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    c_draw = ImageDraw.Draw(card_img)
    # Card background & border
    c_draw.rounded_rectangle([(0, 0), (w - 1, h - 1)], radius=6, fill=(248, 248, 250), outline=(212, 175, 55), width=2)
    # Card text
    color = (220, 38, 38) if is_red else (15, 23, 42)
    c_draw.text((6, 6), f"{rank}{suit}", fill=color)
    c_draw.text((w - 24, h - 22), f"{rank}", fill=color)

    if angle != 0:
        card_img = card_img.rotate(angle, expand=True, resample=Image.Resampling.BICUBIC)

    return card_img

def draw_chip_stack(canvas, x, y):
    draw = ImageDraw.Draw(canvas)
    for i in range(5):
        cy = y - (i * 6)
        draw.ellipse([(x, cy), (x + 48, cy + 18)], fill=(245, 158, 11), outline=(254, 240, 138), width=2)
        draw.ellipse([(x + 8, cy + 4), (x + 40, cy + 14)], outline=(255, 255, 255, 180), width=1)

def draw_sparkle(canvas, x, y, size=14):
    draw = ImageDraw.Draw(canvas)
    gold = (255, 223, 0, 220)
    draw.line([(x, y - size), (x, y + size)], fill=gold, width=2)
    draw.line([(x - size, y), (x + size, y)], fill=gold, width=2)
    draw.ellipse([(x - 3, y - 3), (x + 3, y + 3)], fill=(255, 255, 255, 240))

def render_pose(base_img, pose):
    canvas = Image.new('RGBA', TARGET_SIZE, (18, 9, 34, 255))
    draw = ImageDraw.Draw(canvas)

    # Ambient backdrop gradient
    for r in range(256, 0, -8):
        alpha = int(40 * (1 - r / 256))
        draw.ellipse([(256 - r, 220 - r), (256 + r, 220 + r)], fill=(88, 28, 135, alpha))

    # Paste dealer image centered
    if base_img:
        # Scale to fit nicely in 420x420
        thumb = base_img.copy()
        thumb.thumbnail((440, 440), Image.Resampling.LANCZOS)
        ox = (TARGET_SIZE[0] - thumb.width) // 2
        oy = 20
        canvas.paste(thumb, (ox, oy), thumb if thumb.mode == 'RGBA' else None)

    # Bottom felt table rail
    if pose in ['deal', 'flip']:
        felt_color = (13, 64, 40, 230) # Classic Poker Green Felt
    elif pose == 'win':
        felt_color = (70, 25, 30, 230) # Luxury Crimson Felt
    else:
        felt_color = (30, 16, 55, 230) # Royal Purple Felt

    # Draw rounded felt overlay at bottom
    draw.rounded_rectangle([(16, 360), (496, 496)], radius=20, fill=felt_color, outline=(212, 175, 55, 180), width=3)

    # Pose-specific overlays
    if pose == 'deal':
        # Two cards pitching out
        c1 = draw_mini_card(draw, 0, 0, 'A', '♠', is_red=False, angle=15)
        c2 = draw_mini_card(draw, 0, 0, 'K', '♥', is_red=True, angle=-12)
        canvas.paste(c1, (160, 375), c1)
        canvas.paste(c2, (260, 370), c2)

    elif pose == 'flip':
        # 3 community cards (Flop)
        c1 = draw_mini_card(draw, 0, 0, '10', '♠', is_red=False, angle=0)
        c2 = draw_mini_card(draw, 0, 0, 'J', '♦', is_red=True, angle=0)
        c3 = draw_mini_card(draw, 0, 0, 'Q', '♥', is_red=True, angle=0)
        canvas.paste(c1, (135, 375), c1)
        canvas.paste(c2, (215, 375), c2)
        canvas.paste(c3, (295, 375), c3)

    elif pose == 'win':
        # Gold chips and sparkles
        draw_chip_stack(canvas, 50, 420)
        draw_chip_stack(canvas, 410, 420)
        draw_sparkle(canvas, 100, 120, 16)
        draw_sparkle(canvas, 420, 140, 18)
        draw_sparkle(canvas, 80, 260, 12)
        draw_sparkle(canvas, 440, 280, 14)
        draw_sparkle(canvas, 256, 70, 20)

    # Top Status Banner Pill
    title_map = {
        'idle': 'TƯ THẾ 1: CHỜ CHIA BÀI (IDLE)',
        'deal': 'TƯ THẾ 2: ĐANG CHIA BÀI (DEAL)',
        'flip': 'TƯ THẾ 3: LẬT BÀI CHUNG (FLIP)',
        'win': 'TƯ THẾ 4: ĂN MỪNG CHIẾN THẮNG (WIN)'
    }
    color_map = {
        'idle': (245, 158, 11),
        'deal': (6, 182, 212),
        'flip': (16, 185, 129),
        'win': (244, 63, 94)
    }

    # Top title badge
    draw.rounded_rectangle([(24, 16), (280, 44)], radius=10, fill=(0, 0, 0, 190), outline=color_map.get(pose, (245, 158, 11)), width=2)
    draw.text((36, 22), title_map.get(pose, pose.upper()), fill=color_map.get(pose, (255, 255, 255)))

    # Outer border
    draw.rounded_rectangle([(0, 0), (511, 511)], radius=24, outline=(212, 175, 55, 120), width=3)

    buf = io.BytesIO()
    canvas.save(buf, format='PNG')
    return buf.getvalue()

def render_avatar_preview(idle_bytes):
    try:
        img = Image.open(io.BytesIO(idle_bytes)).convert('RGBA')
    except Exception:
        img = Image.new('RGBA', (256, 256), (30, 15, 55, 255))

    img = img.resize((256, 256), Image.Resampling.LANCZOS)
    mask = Image.new('L', (256, 256), 0)
    draw_mask = ImageDraw.Draw(mask)
    draw_mask.ellipse([(0, 0), (256, 256)], fill=255)

    out = ImageOps.fit(img, (256, 256), centering=(0.5, 0.25))
    out.putalpha(mask)

    # Gold circular rim
    draw = ImageDraw.Draw(out)
    draw.ellipse([(2, 2), (254, 254)], outline=(212, 175, 55), width=6)

    buf = io.BytesIO()
    out.save(buf, format='PNG')
    return buf.getvalue()

def render_dealer_package(poses_map, ref_image_bytes=None):
    dealer_files = {}

    base_img = None
    if ref_image_bytes:
        try:
            base_img = Image.open(io.BytesIO(ref_image_bytes)).convert('RGBA')
        except Exception:
            pass

    for k in ['idle', 'deal', 'flip', 'win']:
        # If external pose exists, use it; otherwise render with overlay
        p_bytes = poses_map.get(k) if poses_map else None
        p_img = None
        if p_bytes:
            try:
                p_img = Image.open(io.BytesIO(p_bytes)).convert('RGBA')
            except Exception:
                pass

        dealer_files[f"{k}.png"] = render_pose(p_img or base_img, k)

    # Avatar preview
    dealer_files['avatar_preview.png'] = render_avatar_preview(dealer_files['idle.png'])
    dealer_files['PREVIEW.png'] = dealer_files['avatar_preview.png']

    return dealer_files
