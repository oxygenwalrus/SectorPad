#!/usr/bin/env python3
"""Make a separate local verification copy; never writes to the supplied installation."""
import argparse, json, pathlib, shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--game',required=True);p.add_argument('--console',action='store_true',help='Include installed Console Commands 4.0.9 in the isolated verification copy');p.add_argument('--no-sectorpad',action='store_true',help='Baseline comparison only; disable SectorPad in the isolated copy');p.add_argument('--target',help='Separate verification directory');args=p.parse_args()
source=pathlib.Path(args.game).resolve();target=pathlib.Path(args.target).resolve() if args.target else (ROOT/'build/verification/Starsector').resolve()
if target==source or source in target.parents:raise SystemExit('Verification must be outside the installed game')
target.mkdir(parents=True,exist_ok=True)
for original in (source/'starsector-core').rglob('*'):
    if not original.is_file() or original.name.startswith('starsector.log') or original.suffix in ('.log','.zip'):continue
    relative=original.relative_to(source/'starsector-core');copy=target/'starsector-core'/relative
    copy.parent.mkdir(parents=True,exist_ok=True)
    if not copy.exists():shutil.copy2(original,copy)
dependencies=['LunaLib-2.0.5','LazyLib-3.0.0']
enabled=['lunalib','lw_lazylib','sectorpad']
if args.no_sectorpad:enabled.remove('sectorpad')
if args.console:
    dependencies.append('Console Commands-4.0.9');enabled.append('lw_console')
for dependency in dependencies:
    shutil.copytree(source/'mods'/dependency,target/'mods'/dependency,dirs_exist_ok=True)
mod_target=(target/'mods/SectorPad').resolve()
if not mod_target.is_relative_to(target) or mod_target==target:raise SystemExit('Refusing to replace a mod outside the isolated verification copy')
if mod_target.exists():shutil.rmtree(mod_target)
if not args.no_sectorpad:shutil.copytree(ROOT/'build/package/SectorPad',mod_target)
(target/'mods/enabled_mods.json').write_text(json.dumps({'enabledMods':enabled},indent=2)+'\n',encoding='utf-8')
for folder in ('saves','screenshots','logs'):(target/folder).mkdir(exist_ok=True)
print(target)
print('Separate saves and logs; existing verification saves are retained. No installed game files or mod enablement were changed.')
