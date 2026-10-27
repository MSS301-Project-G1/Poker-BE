import io
from PIL import Image, ImageDraw

TABLE_WIDTH = 1920
TABLE_HEIGHT = 1080

def hex_to_rgb(hex_code, default=(20, 10, 40)):
    if not hex_code or not isinstance(hex_code, str):
        return default
    hex_code = hex_code.lstrip('#')
    if len(hex_code) == 6:
        try:
            return tuple(int(hex_code[i:i+2], 16) for i in (0, 2, 4))
        except ValueError:
            return default
    return default

def render_poker_table(style_spec, ref_image_bytes=None):
    palette = style_spec.get('palette', {}) if isinstance(style_spec, dict) else {}
    rail_color = hex_to_rgb(palette.get('secondary', '#1a0933'), (26, 9, 51))
    felt_color = hex_to_rgb(palette.get('background', '#0d041a'), (13, 4, 26))
    line_color = hex_to_rgb(palette.get('primary', '#d4af37'), (212, 175, 55))
    accent_color = hex_to_rgb(palette.get('accent', '#00e5ff'), (0, 229, 255))

    img = Image.new('RGBA', (TABLE_WIDTH, TABLE_HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. Outer Rail (large rounded ellipse)
    draw.ellipse([(40, 40), (TABLE_WIDTH - 40, TABLE_HEIGHT - 40)], fill=rail_color, outline=line_color, width=8)

    # 2. Inner Felt
    felt_margin = 120
    draw.ellipse(
        [(felt_margin, felt_margin), (TABLE_WIDTH - felt_margin, TABLE_HEIGHT - felt_margin)],
        fill=felt_color,
        outline=accent_color,
        width=3
    )

    # 3. Betting boundary line
    bet_margin = 240
    draw.ellipse(
        [(bet_margin, bet_margin), (TABLE_WIDTH - bet_margin, TABLE_HEIGHT - bet_margin)],
        outline=line_color,
        width=4
    )

    # 4. Center emblem
    cx, cy = TABLE_WIDTH // 2, TABLE_HEIGHT // 2
    if ref_image_bytes:
        try:
            ref = Image.open(io.BytesIO(ref_image_bytes)).convert('RGBA')
            ref.thumbnail((360, 360), Image.Resampling.LANCZOS)
            rx = cx - ref.width // 2
            ry = cy - ref.height // 2
            img.paste(ref, (rx, ry), ref)
            draw.ellipse([(rx - 8, ry - 8), (rx + ref.width + 8, ry + ref.height + 8)], outline=line_color, width=4)
        except Exception:
            draw.ellipse([(cx - 100, cy - 100), (cx + 100, cy + 100)], fill=(40, 15, 65), outline=line_color, width=4)
    else:
        draw.ellipse([(cx - 100, cy - 100), (cx + 100, cy + 100)], fill=(40, 15, 65), outline=line_color, width=4)

    buf = io.BytesIO()
    img.save(buf, format='PNG')
    table_bytes = buf.getvalue()

    preview = img.resize((480, 270), Image.Resampling.LANCZOS)
    p_buf = io.BytesIO()
    preview.save(p_buf, format='PNG')

    return {
        'table_felt.png': table_bytes,
        'PREVIEW.png': p_buf.getvalue()
    }
