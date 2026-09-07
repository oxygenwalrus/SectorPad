#!/usr/bin/env python3
"""Build and verify SectorPad without writing anywhere in the game installation."""
from __future__ import annotations
import argparse, hashlib, json, os, pathlib, shutil, subprocess, sys, zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
VENDOR_SHA256={
    'jamepad-2.30.0.0.jar':'65b90692053b2a0a799840c06e9c31b5bfb648c0cf50cb6b238f99747fbcb9f2',
    'jamepad-2.30.0.0-sources.jar':'3a5afbd8854d33bac8a0ebc316355d18fa5301096f9ecdc3a7ba06daf45ab644',
    'gdx-jnigen-loader-2.2.0.jar':'5a48459f9d95c2599c8c36e23708737a296465f465f64929b87a1687ae613bdb',
    'jna-5.18.1.jar':'260c4b1e22b1db9e110ee441c4f13ce115f841fa48c41d78750986214b395557',
    'jna-platform-5.18.1.jar':'ad14c1b1ec4f43d396231219dfa635ebf828f738eac9f890ea1bc07795892d9a'
}

def verified_vendor(name):
    path=ROOT/'vendor'/name
    if name not in VENDOR_SHA256 or hashlib.sha256(path.read_bytes()).hexdigest()!=VENDOR_SHA256[name]:
        raise SystemExit(f'Vendor checksum mismatch: {name}')
    return path

def adapt_jamepad(javac):
    """Adapt one bundled library method to Starsector's permitted in-memory stream API.

    The original vendor JAR and native binaries stay intact. The derived JAR is the
    sole Jamepad implementation distributed with this mod; no class shadowing.
    """
    original=verified_vendor('jamepad-2.30.0.0.jar')
    sources=verified_vendor('jamepad-2.30.0.0-sources.jar')
    work=ROOT/'build/jamepad-adapter';clean_generated(work)
    source=work/'src/com/studiohartman/jamepad/ControllerManager.java'
    source.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(sources) as archive:
        content=archive.read('com/studiohartman/jamepad/ControllerManager.java').decode('utf-8')
    start=content.index('    public void addMappingsFromFile(String path)')
    end=content.index('    private native boolean nativeAddMappingsFromFile',start)
    replacement='''    // SectorPad adaptation: classpath resources only, bounded memory, no filesystem extraction.
    public void addMappingsFromFile(String path) throws IOException, IllegalStateException {
        try (InputStream source = getClass().getResourceAsStream(path)) {
            if (source == null) throw new IOException("Controller database resource is missing");
            byte[] bytes = source.readNBytes(2 * 1024 * 1024);
            if (source.read() != -1) throw new IOException("Controller database exceeds 2 MiB");
            if (!nativeAddMappingsFromBuffer(bytes, bytes.length))
                throw new IllegalStateException("SDL rejected the controller database");
        }
    }

'''
    content=content[:start]+replacement+content[end:]
    # Initialization can fail before all ControllerIndex objects exist; still close SDL safely.
    content=content.replace('            c.close();','            if (c != null) c.close();')
    for name in ['java.io.ByteArrayOutputStream','java.io.File','java.io.FileInputStream','java.nio.file.Files','java.nio.file.Path','java.nio.file.StandardCopyOption']:
        content=content.replace('import '+name+';','')
    source.write_text(content,encoding='utf-8')
    cp=os.pathsep.join([str(original),str(verified_vendor('gdx-jnigen-loader-2.2.0.jar'))])
    compile_sources(javac,[source],work/'classes',cp)
    derived=ROOT/'build/jamepad-sectorpad-2.30.0.0.jar'
    with zipfile.ZipFile(original) as archive, zipfile.ZipFile(derived,'w',zipfile.ZIP_DEFLATED) as output:
        for name in sorted(archive.namelist()):
            if name.endswith('/') or name.endswith(('.dll','.so','.dylib')): continue
            patched=work/'classes'/name
            data=patched.read_bytes() if patched.is_file() else archive.read(name)
            info=zipfile.ZipInfo(name,(2026,9,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            output.writestr(info,data)
    return derived

def clean_generated(directory):
    resolved=directory.resolve()
    if not resolved.is_relative_to(ROOT/'build') or resolved==ROOT/'build':
        raise SystemExit('Refusing to clean outside a generated build subdirectory')
    if resolved.exists(): shutil.rmtree(resolved)

def prepare_natives():
    # Unpack at build time, never using runtime extraction or the game's native directory.
    native=ROOT/'build/native'
    with zipfile.ZipFile(ROOT/'vendor/jamepad-2.30.0.0.jar') as archive:
        for platform,name in [('windows-x86_64','jamepad64.dll'),('linux-x86_64','libjamepad64.so')]:
            target=native/platform/name
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(archive.read(name))
    return native

def distribution_jars():
    metadata=json.loads((ROOT/'mod/mod_info.json').read_text(encoding='utf-8'))
    return [ROOT/('build' if 'jamepad-sectorpad-' in name else 'vendor')/pathlib.PurePosixPath(name).name for name in metadata['jars'] if name!='jars/SectorPad.jar']

def run(args):
    result=subprocess.run([str(v) for v in args],cwd=ROOT)
    if result.returncode: raise SystemExit(result.returncode)

def jars_under(directory):
    return sorted(pathlib.Path(directory).rglob('*.jar'))

def classpath(game):
    core=game/'starsector-core' if (game/'starsector-core').exists() else game
    required=['starfarer.api.jar','starfarer_obf.jar','fs.common_obf.jar','lwjgl.jar','lwjgl_util.jar','json.jar','log4j-1.2.9.jar']
    entries=[core/n for n in required]
    for pattern in ('LunaLib-*','LazyLib-*'):
        choices=sorted((game/'mods').glob(pattern))
        if len(choices)!=1: raise SystemExit(f'Expected one {pattern} dependency; found {choices}. Select explicit dependency paths in build configuration.')
        entries.extend(jars_under(choices[0]/'jars'))
    if any(not p.is_file() for p in entries): raise SystemExit('Required local game/dependency JAR is missing')
    for dependency in distribution_jars():
        if dependency.parent==ROOT/'vendor':verified_vendor(dependency.name)
        entries.append(dependency)
    return os.pathsep.join(str(p) for p in entries)

def compile_sources(javac, sources, output, cp):
    output.mkdir(parents=True,exist_ok=True)
    argfile=output.parent/(output.name+'-sources.txt')
    argfile.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8')
    run([javac,'--release','17','-encoding','UTF-8','-Xlint:unchecked','-cp',cp,'-d',output,'@'+str(argfile)])

def jar_tree(destination, source, prefix=''):
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
        for p in sorted(source.rglob('*')):
            if p.is_file():
                info=zipfile.ZipInfo(prefix+p.relative_to(source).as_posix(),(2026,9,7,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                archive.writestr(info,p.read_bytes())


PUBLIC_DOCS=('COMPATIBILITY.md','GAME_INTEGRATION.md','SETTINGS.md','LUNALIB_API_REVIEW.md','LIFECYCLE.md','HANDHELDS_AND_DIAGNOSTICS.md')
SOURCE_VENDOR=('jamepad-2.30.0.0.jar','jamepad-2.30.0.0-sources.jar','jamepad-2.30.0.0.pom','gdx-jnigen-loader-2.2.0.jar','gamecontrollerdb-commit.txt')
NATIVE_PAYLOADS=('windows-x86_64/jamepad64.dll','linux-x86_64/libjamepad64.so','windows-x86_64/sectorpad-input-windows-x86_64.dll')

def copy_public_docs(destination,design_history=False):
    """Explicitly exclude the local installation baseline and copied game material."""
    destination.mkdir(parents=True,exist_ok=True)
    for name in PUBLIC_DOCS+(('VISUAL_DESIGN_OPTIONS.md',) if design_history else ()):
        source=ROOT/'docs'/name
        if source.is_file():shutil.copy2(source,destination/name)
    for directory in ('evidence','visuals') if design_history else ('evidence',):
        source=ROOT/'docs'/directory
        if source.is_dir():
            for path in sorted(source.rglob('*')):
                if path.is_file() and path.suffix.lower() in ('.png','.jpg','.svg','.html'):
                    target=destination/path.relative_to(ROOT/'docs');target.parent.mkdir(parents=True,exist_ok=True)
                    shutil.copy2(path,target)

def file_manifest(directory):
    return {p.relative_to(directory).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(directory.rglob('*')) if p.is_file()}

def package_source():
    """Build a source archive from an allowlist, never recursively from the workspace."""
    parent=ROOT/'build/source-package';clean_generated(parent)
    target=parent/'SectorPad-src';target.mkdir(parents=True)
    for directory in ('src','mod','licenses'):
        shutil.copytree(ROOT/directory,target/directory)
    (target/'tools').mkdir()
    for path in sorted((ROOT/'tools').glob('*.py')):shutil.copy2(path,target/'tools'/path.name)
    for name in ('.gitignore','README.md','LICENSE','THIRD_PARTY_NOTICES.md','build.gradle','settings.gradle'):
        shutil.copy2(ROOT/name,target/name)
    copy_public_docs(target/'docs',design_history=True)
    (target/'vendor').mkdir()
    for name in SOURCE_VENDOR:shutil.copy2(ROOT/'vendor'/name,target/'vendor'/name)
    changed=target/'third_party/jamepad/ControllerManager.java';changed.parent.mkdir(parents=True)
    shutil.copy2(ROOT/'build/jamepad-adapter/src/com/studiohartman/jamepad/ControllerManager.java',changed)
    source_manifest=file_manifest(target)
    encoded=(json.dumps(source_manifest,indent=2)+'\n').encode('utf-8')
    (target/'SOURCE-MANIFEST.json').write_bytes(encoded)
    jar_tree(ROOT/'build/SectorPad-source.zip',parent)
    return hashlib.sha256(encoded).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--game',default=os.environ.get('STARSECTOR_HOME',r'C:\Program Files (x86)\Fractal Softworks\Starsector'))
    parser.add_argument('--logic-only',action='store_true')
    parser.add_argument('--probe',action='store_true',help='Read native controller state once; never sends input')
    args=parser.parse_args()
    javac=shutil.which('javac');java=shutil.which('java')
    if not javac or not java: raise SystemExit('A Java 17+ JDK with javac and java on PATH is required')
    build=ROOT/'build';build.mkdir(exist_ok=True)
    if args.logic_only:
        src=sorted((ROOT/'src/main/java/sectorpad/core').glob('*.java'))
        tests=[p for p in sorted((ROOT/'src/test/java/sectorpad/core').glob('*.java')) if p.name!='BackendProbe.java']
        classes=build/'logic-classes'
        compile_sources(javac,src+tests,classes,'.')
        for test in tests:
            if 'static void main(' in test.read_text(encoding='utf-8'):
                run([java,'-ea','-cp',classes,'sectorpad.core.'+test.stem])
        return
    adapt_jamepad(javac)
    cp=classpath(pathlib.Path(args.game))
    native=prepare_natives()
    if os.name=='nt':run([sys.executable,ROOT/'tools/build-native-windows.py'])
    elif not (native/'windows-x86_64/sectorpad-input-windows-x86_64.dll').is_file():
        raise SystemExit('Release packaging needs the Windows JNI build. Build on Windows with Visual Studio C++ tools; --logic-only is portable.')
    classes=build/'classes'
    # Remove only generated class files so deleted classes never survive a build.
    clean_generated(classes)
    compile_sources(javac,sorted((ROOT/'src/main/java').rglob('*.java')),classes,cp)
    if (ROOT/'src/main/resources').exists(): shutil.copytree(ROOT/'src/main/resources',classes,dirs_exist_ok=True)
    tests=sorted((ROOT/'src/test/java').rglob('*.java'))
    if tests:
        testclasses=build/'test-classes'
        clean_generated(testclasses)
        compile_sources(javac,tests,testclasses,str(classes)+os.pathsep+cp)
        testcp=str(testclasses)+os.pathsep+str(classes)+os.pathsep+cp
        for test in tests:
            if 'static void main(' in test.read_text(encoding='utf-8'):
                testname='.'.join(test.relative_to(ROOT/'src/test/java').with_suffix('').parts)
                native_probe=testname.endswith(('BackendProbe','WindowsInputNativeProbe'))
                if native_probe and not args.probe: continue
                if testname.endswith('WindowsInputNativeProbe') and os.name!='nt': continue
                extra=[ROOT/'mod/data/config/LunaSettings.csv'] if testname.endswith('SettingsTests') else [native] if native_probe else [args.game] if testname.endswith('ConsoleApiTests') else []
                run([java,'-noverify','-ea','-cp',testcp,testname]+extra)
    release=build/'package'/'SectorPad'
    clean_generated(release)
    release.mkdir(parents=True,exist_ok=True)
    shutil.copytree(ROOT/'mod',release,dirs_exist_ok=True)
    (release/'jars').mkdir(exist_ok=True)
    jar_tree(release/'jars/SectorPad.jar',classes)
    for p in distribution_jars():shutil.copy2(p,release/'jars'/p.name)
    for name in NATIVE_PAYLOADS:
        destination=release/'native'/name;destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(native/name,destination)
    for name in ('README.md','LICENSE','THIRD_PARTY_NOTICES.md'):
        if (ROOT/name).exists(): shutil.copy2(ROOT/name,release/name)
    if (ROOT/'licenses').exists():shutil.copytree(ROOT/'licenses',release/'licenses',dirs_exist_ok=True)
    copy_public_docs(release/'docs')
    source_digest=package_source()
    metadata=json.loads((ROOT/'mod/mod_info.json').read_text(encoding='utf-8'))
    info={'mod':metadata['id'],'version':metadata['version'],'gameVersion':metadata['gameVersion'],
          'javaRelease':17,'sourceManifestSha256':source_digest,
          'controllerDatabaseSha256':hashlib.sha256((ROOT/'src/main/resources/sectorpad/gamecontrollerdb.txt').read_bytes()).hexdigest(),
          'validation':'Automated checks passed. See docs/COMPATIBILITY.md for recorded live and physical checks; full controller and SteamOS acceptance remain unverified.'}
    (release/'BUILD-INFO.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
    manifest=file_manifest(release)
    (build/'package-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    jar_tree(build/'SectorPad.zip',release,'SectorPad/')
    (build/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256((build/name).read_bytes()).hexdigest()+'  '+name+'\n'
        for name in ('SectorPad.zip','SectorPad-source.zip')),encoding='utf-8')
    print(f'Package built: {build / "SectorPad.zip"}')
    print(f'Source package built: {build / "SectorPad-source.zip"}')
    print('Game installation was not modified. Runtime/device certification is separate.')

if __name__=='__main__': main()
