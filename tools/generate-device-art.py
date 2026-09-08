#!/usr/bin/env python3
"""Rasterize the preserved controller illustrations; needs Node.js and sharp 0.35.4.

Set NODE_PATH to a node_modules directory containing sharp, and optionally NODE_BINARY.
Source files are never modified. Output is white RGBA line art for runtime UI tinting.
No downloads are performed during generation or a normal mod build.
"""
import hashlib
import argparse
import json
import os
from pathlib import Path
import subprocess
import struct

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "vendor/ui-art/devices"
OUTPUT = ROOT / "mod/graphics/sectorpad/controls/devices"
SOURCES = {"steam-deck": "steamdeckFront.svg", "rog-ally": "rog-ally.svg", "xbox": "xbox-controller-outline.svg"}

JS = r"""
const sharp = require('sharp');
const fs = require('fs');
const [source, output] = process.argv.slice(1);
(async () => {
  let svg=fs.readFileSync(source,'utf8');
  if(source.endsWith('xbox-controller-outline.svg')) {
    // Source canvas contains wide presentation margins; crop to the art bounds.
    svg=svg.replace(/viewBox="[^"]*"/,'viewBox="1184 479 1729 1202"');
  }
  const {data, info} = await sharp(Buffer.from(svg), {density: 384})
    .resize({height:664}).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  // Preserve source contours/antialiasing. Normalize ink for the game's color tint.
  for(let i=0;i<data.length;i+=4) data[i]=data[i+1]=data[i+2]=255;
  await sharp(data,{raw:info}).png({compressionLevel:9,adaptiveFiltering:false}).toFile(output);
  process.stdout.write(JSON.stringify({width:info.width,height:info.height,sharp:sharp.versions.sharp}));
})().catch(e=>{console.error(e);process.exit(1)});
"""

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Verify packaged source/output digests and PNG dimensions without Node.js')
    args=parser.parse_args()
    if args.check:
        manifest=json.loads((SOURCE / 'generated-manifest.json').read_text(encoding='utf-8'))
        assert set(manifest)==set(SOURCES), 'Device manifest coverage changed'
        for name, item in manifest.items():
            source, output=ROOT/item['source'], ROOT/item['output']
            assert source==SOURCE/SOURCES[name] and output==OUTPUT/(name+'.png'), name+' path'
            assert digest(source)==item['source_sha256'], name+' source changed'
            assert digest(output)==item['output_sha256'], name+' output changed'
            raw=output.read_bytes()
            assert raw[:8]==b'\x89PNG\r\n\x1a\n', name+' PNG signature'
            assert struct.unpack('>II',raw[16:24])==(item['width'],item['height']), name+' dimensions'
            assert item['height']==664 and 1<=item['width']<=2048, name+' texture size'
        print('Device artwork: 3 sources, digests and PNG dimensions verified')
        return
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for name, filename in SOURCES.items():
        source, output = SOURCE / filename, OUTPUT / (name + ".png")
        info = json.loads(subprocess.check_output(
            [os.environ.get("NODE_BINARY", "node"), "-e", JS, str(source), str(output)], text=True))
        manifest[name] = dict(source=source.relative_to(ROOT).as_posix(), source_sha256=digest(source),
                              output=output.relative_to(ROOT).as_posix(), output_sha256=digest(output), **info)
        print(f"{name}: {info['width']} x {info['height']}")
    (SOURCE / "generated-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")

if __name__ == "__main__":
    main()
