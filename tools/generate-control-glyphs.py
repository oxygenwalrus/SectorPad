"""Regenerate selected, licensed controller prompts locally (Pillow 11+).

No downloads. --check uses only Python's standard library and verifies originals,
output hashes, PNG dimensions, complete control coverage and the Java mapping.
"""
import argparse
import hashlib
import json
import pathlib
import struct

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'vendor/ui-art/prompts'
MANIFEST = SOURCES / 'generated.json'
JAVA = ROOT / 'src/main/java/sectorpad/ui/ControlGlyphs.java'
CONTROLS = set('A B X Y LB RB LT RT L3 R3 MENU VIEW LEFT_STICK RIGHT_STICK DPAD DPAD_UP DPAD_DOWN DPAD_LEFT DPAD_RIGHT'.split())
FAMILIES = {'xbox': 'XBOX', 'steam-deck': 'STEAM_DECK', 'rog-ally': 'ROG_ALLY', 'generic': 'GENERIC'}
START = '    // BEGIN GENERATED ARTWORK MAPPING'
END = '    // END GENERATED ARTWORK MAPPING'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def java_mapping(records):
    text = START + '\n'
    text += '    public static String artworkPath(String control,DeviceVisual visual) {\n'
    text += '        if(!PromptRenderer.recognized(control))return null;\n'
    text += '        if(visual==null)visual=DeviceVisual.GENERIC;\n'
    text += '        String family=switch(visual){case XBOX->"xbox";case STEAM_DECK->"steam-deck";case ROG_ALLY->"rog-ally";default->"generic";};\n'
    text += '        return "graphics/sectorpad/controls/glyphs/"+family+"/"+control.toLowerCase(java.util.Locale.ROOT)+".png";\n    }\n'
    text += '    private static float artworkAspect(String control,DeviceVisual visual) {\n'
    text += '        if(visual==null)visual=DeviceVisual.GENERIC;\n'
    text += '        return switch(visual){\n'
    for family, enum in FAMILIES.items():
        text += '            case ' + enum + ' -> switch(control){\n'
        for r in records:
            if r['family'] == family:
                text += f'                case "{r["control"]}" -> {r["width"]}f/{r["height"]}f;\n'
        text += '                default -> 1f;\n            };\n'
    text += '        };\n    }\n' + END
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    source = json.loads((SOURCES / 'sources.json').read_text(encoding='utf-8'))
    records = source['glyphs']
    assert len(records) == len(CONTROLS) * len(FAMILIES), 'Unexpected glyph count'
    assert {(r['family'], r['control']) for r in records} == {(f, c) for f in FAMILIES for c in CONTROLS}, 'Incomplete glyph mapping'
    generated = []
    for record in records:
        original = SOURCES / record['source']
        assert digest(original) == record['sourceSha256'], f'Original changed: {original}'
        output = ROOT / 'mod' / record['output']
        assert output.resolve().is_relative_to((ROOT / 'mod/graphics/sectorpad/controls/glyphs').resolve())
        if not args.check:
            from PIL import Image
            image = Image.open(original).convert('RGBA')
            bounds = image.getchannel('A').getbbox()
            assert bounds, f'Empty image: {original}'
            crop = image.crop(bounds)
            padded = Image.new('RGBA', (crop.width + 2, crop.height + 2))
            padded.paste(crop, (1, 1))
            output.parent.mkdir(parents=True, exist_ok=True)
            padded.save(output, optimize=False, compress_level=9)
        raw = output.read_bytes()
        assert raw[:8] == b'\x89PNG\r\n\x1a\n'
        width, height = struct.unpack('>II', raw[16:24])
        generated.append(dict(family=record['family'], control=record['control'], output=record['output'], width=width, height=height, sha256=digest(output)))
    block = java_mapping(generated)
    java = JAVA.read_text(encoding='utf-8')
    if args.check:
        assert generated == json.loads(MANIFEST.read_text(encoding='utf-8')), 'Generated prompt image changed'
        assert block in java, 'Java prompt dimensions or path mapping changed'
        expected = {r['output'] for r in generated}
        actual = {p.relative_to(ROOT / 'mod').as_posix() for p in (ROOT / 'mod/graphics/sectorpad/controls/glyphs').rglob('*.png')}
        assert actual == expected, 'Unexpected or missing prompt texture'
    else:
        MANIFEST.write_text(json.dumps(generated, indent=2) + '\n', encoding='utf-8', newline='\n')
        start, end = java.index(START), java.index(END) + len(END)
        JAVA.write_text(java[:start] + block + java[end:], encoding='utf-8', newline='\n')
    print(f'Controller glyph artwork: {len(generated)} verified mappings' if args.check else f'Generated {len(generated)} controller glyph textures')


if __name__ == '__main__':
    main()
