import base64
import io
import json
import os
import zipfile
from flask import Flask, request, jsonify

from renderers.card_renderer import render_all_cards
from renderers.dealer_renderer import render_dealer_package
from renderers.table_renderer import render_poker_table

app = Flask(__name__)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'UP',
        'engine': 'Python Asset Sandbox 1.0',
        'pid': os.getpid()
    }), 200

@app.route('/render', methods=['POST'])
def render_assets():
    try:
        data = request.get_json(force=True) or {}
        category = data.get('category', 'CARD_SKIN')
        style_spec = data.get('style_spec', {})
        ref_b64 = data.get('reference_image_base64')
        poses_b64 = data.get('poses_base64', {})

        ref_bytes = base64.b64decode(ref_b64) if ref_b64 else None
        poses_bytes = {}
        if isinstance(poses_b64, dict):
            for k, v in poses_b64.items():
                if v:
                    try:
                        poses_bytes[k] = base64.b64decode(v)
                    except Exception:
                        pass

        # Select renderer
        if category == 'CARD_SKIN':
            rendered_files = render_all_cards(style_spec, ref_bytes)
        elif category == 'DEALER':
            rendered_files = render_dealer_package(poses_bytes, ref_bytes)
        elif category == 'TABLE':
            rendered_files = render_poker_table(style_spec, ref_bytes)
        else:
            # Fallback to single cosmetic asset
            rendered_files = render_poker_table(style_spec, ref_bytes)

        # Build Manifest
        manifest = {
            'category': category,
            'file_count': len(rendered_files),
            'files': sorted(list(rendered_files.keys())),
            'primary_preview': 'PREVIEW.png' if 'PREVIEW.png' in rendered_files else next(iter(rendered_files.keys())),
            'style_spec': style_spec
        }

        # Package ZIP in memory
        zip_buffer = io.BytesIO()
        with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zf:
            # Write manifest.json
            zf.writestr('manifest.json', json.dumps(manifest, indent=2))
            for fname, fbytes in rendered_files.items():
                zf.writestr(fname, fbytes)

        zip_bytes = zip_buffer.getvalue()
        preview_bytes = rendered_files.get('PREVIEW.png', next(iter(rendered_files.values())))

        poses_preview = {}
        if category == 'DEALER':
            for k in ['idle', 'deal', 'flip', 'win']:
                fname = f"{k}.png"
                if fname in rendered_files:
                    poses_preview[k] = base64.b64encode(rendered_files[fname]).decode('utf-8')

        return jsonify({
            'success': True,
            'category': category,
            'manifest': manifest,
            'zip_base64': base64.b64encode(zip_bytes).decode('utf-8'),
            'preview_image_base64': base64.b64encode(preview_bytes).decode('utf-8'),
            'poses_preview_base64': poses_preview,
            'total_bytes': len(zip_bytes)
        }), 200

    except Exception as e:
        app.logger.error(f"Render failed: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8095))
    app.run(host='0.0.0.0', port=port, debug=False)
