import io
from PIL import Image, ImageDraw, ImageFont

SUITS = [('S', '♠', 'black'), ('H', '♥', 'red'), ('D', '♦', 'red'), ('C', '♣', 'black')]
RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A']
CARD_WIDTH = 300
CARD_HEIGHT = 420
CORNER_RADIUS = 24

def hex_to_rgb(hex_code, default=(255, 255, 255)):
    if not hex_code or not isinstance(hex_code, str):
        return default
    hex_code = hex_code.lstrip('#')
    if len(hex_code) == 6:
        try:
            return tuple(int(hex_code[i:i+2], 16) for i in (0, 2, 4))
        except ValueError:
            return default
    return default

def get_font(size):
    try:
        return ImageFont.truetype("arial.ttf", size)
    except Exception:
        return ImageFont.load_default()

def create_rounded_mask(w, h, r):
    mask = Image.new('L', (w, h), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (w, h)], radius=r, fill=255)
    return mask

def render_card_front(rank, suit_code, suit_symbol, suit_color, palette, ref_img=None):
    img = Image.new('RGBA', (CARD_WIDTH, CARD_HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Background
    bg_color = hex_to_rgb(palette.get('card_bg', '#120822'), (18, 8, 34))
    border_color = hex_to_rgb(palette.get('primary', '#d4af37'), (212, 175, 55))
    accent_color = hex_to_rgb(palette.get('accent', '#00e5ff'), (0, 229, 255))
    
    # Draw card base rounded rectangle
    draw.rounded_rectangle([(0, 0), (CARD_WIDTH - 1, CARD_HEIGHT - 1)], radius=CORNER_RADIUS, fill=bg_color, outline=border_color, width=3)
    # Inner border
    draw.rounded_rectangle([(10, 10), (CARD_WIDTH - 11, CARD_HEIGHT - 11)], radius=CORNER_RADIUS - 6, outline=accent_color, width=1)

    # Suit and rank color
    if suit_color == 'red':
        fill_color = hex_to_rgb(palette.get('red_suit', '#ff3366'), (255, 51, 102))
    else:
        fill_color = hex_to_rgb(palette.get('black_suit', '#f8fafc'), (248, 250, 252))

    font_rank = get_font(26)
    font_suit = get_font(24)
    font_center = get_font(72)

    # Top-left corner
    draw.text((18, 16), rank, fill=fill_color, font=font_rank)
    draw.text((18, 48), suit_symbol, fill=fill_color, font=font_suit)

    # Bottom-right corner
    draw.text((CARD_WIDTH - 36, CARD_HEIGHT - 76), suit_symbol, fill=fill_color, font=font_suit)
    draw.text((CARD_WIDTH - 36, CARD_HEIGHT - 46), rank, fill=fill_color, font=font_rank)

    # Center illustration
    if rank in ['J', 'Q', 'K', 'A']:
        # If reference image exists, place in center badge
        if ref_img is not None:
            thumb = ref_img.copy()
            thumb.thumbnail((120, 120), Image.Resampling.LANCZOS)
            x_pos = (CARD_WIDTH - thumb.width) // 2
            y_pos = (CARD_HEIGHT - thumb.height) // 2
            img.paste(thumb, (x_pos, y_pos), thumb if thumb.mode == 'RGBA' else None)
            draw.rectangle([(x_pos - 2, y_pos - 2), (x_pos + thumb.width + 1, y_pos + thumb.height + 1)], outline=border_color, width=2)
        else:
            bbox = font_center.getbbox(rank + suit_symbol) if hasattr(font_center, 'getbbox') else (0, 0, 70, 70)
            text_w = bbox[2] - bbox[0]
            text_h = bbox[3] - bbox[1]
            cx = (CARD_WIDTH - text_w) // 2
            cy = (CARD_HEIGHT - text_h) // 2
            draw.text((cx, cy), rank + suit_symbol, fill=border_color, font=font_center)
    else:
        # Pips
        bbox = font_center.getbbox(suit_symbol) if hasattr(font_center, 'getbbox') else (0, 0, 60, 60)
        text_w = bbox[2] - bbox[0]
        text_h = bbox[3] - bbox[1]
        cx = (CARD_WIDTH - text_w) // 2
        cy = (CARD_HEIGHT - text_h) // 2
        draw.text((cx, cy), suit_symbol, fill=fill_color, font=font_center)

    return img

def render_card_back(palette, ref_img=None):
    img = Image.new('RGBA', (CARD_WIDTH, CARD_HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    bg_color = hex_to_rgb(palette.get('secondary', '#1a0933'), (26, 9, 51))
    border_color = hex_to_rgb(palette.get('primary', '#d4af37'), (212, 175, 55))
    accent_color = hex_to_rgb(palette.get('accent', '#00e5ff'), (0, 229, 255))

    # Outer rounded background
    draw.rounded_rectangle([(0, 0), (CARD_WIDTH - 1, CARD_HEIGHT - 1)], radius=CORNER_RADIUS, fill=bg_color, outline=border_color, width=4)
    # Inner gold border
    draw.rounded_rectangle([(14, 14), (CARD_WIDTH - 15, CARD_HEIGHT - 15)], radius=CORNER_RADIUS - 6, outline=border_color, width=2)
    draw.rounded_rectangle([(20, 20), (CARD_WIDTH - 21, CARD_HEIGHT - 21)], radius=CORNER_RADIUS - 10, outline=accent_color, width=1)

    # Geometric pattern
    step = 25
    for x in range(30, CARD_WIDTH - 30, step):
        for y in range(30, CARD_HEIGHT - 30, step):
            draw.rectangle([(x, y), (x + 2, y + 2)], fill=border_color)

    # Center emblem or reference image
    if ref_img is not None:
        thumb = ref_img.copy()
        thumb.thumbnail((160, 160), Image.Resampling.LANCZOS)
        x_pos = (CARD_WIDTH - thumb.width) // 2
        y_pos = (CARD_HEIGHT - thumb.height) // 2
        img.paste(thumb, (x_pos, y_pos), thumb if thumb.mode == 'RGBA' else None)
        draw.ellipse([(x_pos - 4, y_pos - 4), (x_pos + thumb.width + 4, y_pos + thumb.height + 4)], outline=border_color, width=3)
    else:
        cx, cy = CARD_WIDTH // 2, CARD_HEIGHT // 2
        r = 60
        draw.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=(35, 15, 65), outline=border_color, width=3)
        font = get_font(36)
        draw.text((cx - 18, cy - 24), "♠", fill=border_color, font=font)

    return img

def render_all_cards(style_spec, ref_image_bytes=None):
    palette = style_spec.get('palette', {}) if isinstance(style_spec, dict) else {}
    ref_img = None
    if ref_image_bytes:
        try:
            ref_img = Image.open(io.BytesIO(ref_image_bytes)).convert('RGBA')
        except Exception:
            ref_img = None

    cards = {}

    # Render 52 cards
    for s_code, s_sym, s_col in SUITS:
        for rank in RANKS:
            key = f"{rank}{s_code}.png"
            card_img = render_card_front(rank, s_code, s_sym, s_col, palette, ref_img if rank == 'A' and s_code == 'S' else None)
            buf = io.BytesIO()
            card_img.save(buf, format='PNG')
            cards[key] = buf.getvalue()

    # Render BACK.png
    back_img = render_card_back(palette, ref_img)
    back_buf = io.BytesIO()
    back_img.save(back_buf, format='PNG')
    cards['BACK.png'] = back_buf.getvalue()

    # Render PREVIEW.png
    cards['PREVIEW.png'] = back_buf.getvalue()

    return cards
