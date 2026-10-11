#!/usr/bin/env python3
"""Generate launcher resources from the unmodified uploaded PsyZine artwork.

Requires Pillow. Run from any directory: python3 tools/generate-launcher-icons.py.
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res'
SOURCE = ROOT / 'artwork/psyzine-icon.png'
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}


def main():
    original = Image.open(SOURCE)
    if original.width != original.height:
        raise SystemExit('Use square source artwork; never crop or stretch it.')
    source = original.convert('RGBA')
    save_options = {'optimize': True}
    if original.info.get('icc_profile'):
        save_options['icc_profile'] = original.info['icc_profile']
    circle = Image.new('L', source.size)
    ImageDraw.Draw(circle).ellipse((0, 0, source.width - 1, source.height - 1), fill=255)
    rounded = source.copy()
    rounded.putalpha(ImageChops.multiply(source.getchannel('A'), circle))
    # Android tints this alpha-only rendition when themed icons are enabled.
    red, green, blue, alpha = source.split()
    brightness = ImageChops.lighter(ImageChops.lighter(red, green), blue)
    monochrome = Image.new('RGBA', source.size, 'white')
    monochrome.putalpha(ImageChops.multiply(brightness, alpha))
    for density, scale in DENSITIES.items():
        directory = RES / f'mipmap-{density}'
        directory.mkdir(exist_ok=True)
        legacy_size = round(48 * scale)
        for name, image in [('ic_launcher', source), ('ic_launcher_round', rounded)]:
            image.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS).save(
                directory / f'{name}.png', **save_options)
        # Full square artwork occupies 72dp of the 108dp adaptive layer.
        # Its circular emblem is ~58dp across, fully inside the 66dp safe circle.
        layer_size, content_size = round(108 * scale), round(72 * scale)
        offset = (layer_size - content_size) // 2
        for name, image in [('ic_launcher_foreground', source), ('ic_launcher_monochrome', monochrome)]:
            layer = Image.new('RGBA', (layer_size, layer_size))
            layer.alpha_composite(image.resize((content_size, content_size), Image.Resampling.LANCZOS),
                                  (offset, offset))
            layer.save(directory / f'{name}.png', **save_options)
        for old in directory.glob('ic_launcher*.webp'):
            old.unlink()
    source.resize((512, 512), Image.Resampling.LANCZOS).save(
        ROOT / 'app/src/main/ic_launcher-playstore.png', **save_options)
    source.resize((512, 512), Image.Resampling.LANCZOS).save(
        ROOT / 'metadata/en-US/images/icon.png', **save_options)


if __name__ == '__main__':
    main()
