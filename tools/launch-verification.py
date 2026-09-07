#!/usr/bin/env python3
"""Launch the isolated local game copy with isolated saves/logs/mods."""
import argparse, pathlib, subprocess, json, os, datetime, re
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--game',required=True);p.add_argument('--debug-input',action='store_true');p.add_argument('--resolution',default='1280x800');args=p.parse_args()
if not re.fullmatch(r'[1-9]\d{2,3}x[1-9]\d{2,3}',args.resolution):raise SystemExit('Resolution must be WIDTHxHEIGHT')
installed=pathlib.Path(args.game).resolve();test=ROOT/'build/verification/Starsector';core=test/'starsector-core'
if not (core/'starfarer.api.jar').is_file():raise SystemExit('Run stage-verification.py first')
runtime=installed/'jre/bin/javaw.exe'
jars=['janino.jar','commons-compiler.jar','commons-compiler-jdk.jar','starfarer.api.jar','starfarer_obf.jar','jogg-0.0.7.jar','jorbis-0.0.15.jar','json.jar','lwjgl.jar','jinput.jar','log4j-1.2.9.jar','lwjgl_util.jar','fs.sound_obf.jar','fs.common_obf.jar','xstream-1.4.10.jar','txw2-3.0.2.jar','jaxb-api-2.4.0-b180830.0359.jar','webp-imageio-0.1.6.jar']
command=[str(runtime),'-noverify','-Xms4096m','-Xmx4096m','-Xss4m','-DlaunchDirect=true','-DstartRes='+args.resolution,'-DstartFS=false','-DstartSound=false','-Djava.library.path='+str(core/'native/windows'),'-Djava.util.Arrays.useLegacyMergeSort=true']
if args.debug_input:command.append('-Dsectorpad.debugInput=true')
for module in ['java.base/sun.nio.ch','java.base/java.nio','java.base/jdk.internal.ref','java.base/java.lang','java.base/java.lang.reflect','java.base/java.lang.ref','java.base/java.util','java.base/java.util.concurrent','java.base/java.util.concurrent.locks','java.base/java.text','java.desktop/java.awt','java.desktop/java.awt.font']:
    command+=['--add-opens='+module+'=ALL-UNNAMED']
for kind in ['saves','screenshots','mods','logs']:
    command+=['-Dcom.fs.starfarer.settings.paths.'+kind+'='+str(test/kind)]
command+=['-cp',os.pathsep.join(str(core/j) for j in jars),'com.fs.starfarer.StarfarerLauncher']
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
history=test/'logs/history';history.mkdir(exist_ok=True)
for name in ['starsector.log','launcher-output.log']:
    previous=test/'logs'/name
    if previous.is_file():previous.rename(history/(stamp+'-'+name))
log=open(test/'logs/launcher-output.log','ab')
proc=subprocess.Popen(command,cwd=core,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
(test/'verification-process.json').write_text(json.dumps({'pid':proc.pid,'workingDirectory':str(core),'runtime':str(runtime)},indent=2)+'\n',encoding='utf-8')
print('Isolated Starsector process started:',proc.pid)
