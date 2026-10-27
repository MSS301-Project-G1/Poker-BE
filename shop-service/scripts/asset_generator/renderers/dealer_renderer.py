import io
from PIL import Image, ImageDraw, ImageOps

TARGET_SIZE = (512, 512)

def process_pose_image(raw_bytes, label):
    try:
        img = Image.open(io.BytesIO(raw_bytes)).convert('RGBA')
    except Exception:
        # Create fallback dealer avatar
        img = Image.new('RGBA', TARGET_SIZE, (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        draw.ellipse([(64, 64), (448, 448)], fill=(45, 20, 80), outline=(212, 175, 55), width=4)
        draw.text((180, 240), f"DEALER {label.upper()}", fill=(245, 158, 11))
        buf = io.BytesIO()
        img.save(buf, format='PNG')
        return buf.getvalue()

    # Fit into 512x512 keeping aspect ratio
    img.thumbnail(TARGET_SIZE, Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', TARGET_SIZE, (0, 0, 0, 0))
    offset_x = (TARGET_SIZE[0] - img.width) // 2
    offset_y = (TARGET_SIZE[1] - img.height) // 2
    canvas.paste(img, (offset_x, offset_y), img)

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

    out = ImageOps.fit(img, (256, 256), centering=(0.5, 0.3))
    out.putalpha(mask)

    # Gold circular rim
    draw = ImageDraw.Draw(out)
    draw.ellipse([(2, 2), (254, 254)], outline=(212, 175, 55), width=6)

    buf = io.BytesIO()
    out.save(buf, format='PNG')
    return buf.getvalue()

def render_dealer_package(poses_map, ref_image_bytes=None):
    dealer_files = {}

    keys = ['idle', 'deal', 'flip', 'win']
    for k in keys:
        raw = poses_map.get(k) if poses_map else None
        if not raw and ref_image_bytes:
            raw = ref_image_bytes
        dealer_files[f"{k}.png"] = process_pose_image(raw, k)

    # Avatar preview
    dealer_files['avatar_preview.png'] = render_avatar_preview(dealer_files['idle.png'])
    dealer_files['PREVIEW.png'] = dealer_files['avatar_preview.png']

    return dealer_files
