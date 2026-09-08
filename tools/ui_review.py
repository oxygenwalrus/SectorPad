#!/usr/bin/env python3
"""Render the current runtime UI in a hidden LWJGL Pbuffer using local game fonts.

No game process is launched. Resources are read from the local installation and
never copied into source or distribution packages. Run tools/build.py first to
prepare the verified dependencies. Images are local review artifacts only.
"""
from __future__ import annotations
import argparse
import os
import pathlib
import shutil
import subprocess
import build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game', type=pathlib.Path, default=pathlib.Path(os.environ.get(
        'STARSECTOR_HOME', r'C:\Program Files (x86)\Fractal Softworks\Starsector')))
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    output = root / 'build/ui-review'
    classes = output / 'classes'
    cp = build.classpath(args.game)
    sources = sorted((root / 'src/main/java').rglob('*.java'))
    sources.append(root / 'tools/ui-review/OffscreenUiReview.java')
    build.compile_sources(shutil.which('javac'), sources, classes, cp)
    native = args.game / 'starsector-core/native/windows'
    if os.name != 'nt':
        native = args.game / 'starsector-core/native/linux'
    command = [shutil.which('java'), '-Djava.awt.headless=true', '-Dlog4j.defaultInitOverride=true',
               '-Dorg.lwjgl.librarypath=' + str(native),
               '-cp', str(classes) + os.pathsep + cp,
               'sectorpad.review.OffscreenUiReview',
               str(args.game / 'starsector-core'), str(output)]
    result = subprocess.run(command, cwd=root, text=True, capture_output=True)
    (output / 'review.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    print(result.stdout, end='')
    print(result.stderr, end='')
    if result.returncode:
        raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
