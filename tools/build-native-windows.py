"""Build only the mod-owned Windows JNI library. Does not install or launch the game."""
from pathlib import Path
import os
import subprocess
import shutil
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VS = Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Microsoft Visual Studio/2022/Community"
VCVARS = VS / "VC/Auxiliary/Build/vcvars64.bat"
if not VCVARS.is_file():
    raise SystemExit("Visual Studio 2022 C++ x64 tools are required to rebuild the Windows JNI library")

captured = subprocess.run(f'cmd.exe /d /s /c ""{VCVARS}" >nul && set"',
                          check=True, capture_output=True, text=True).stdout
environment = os.environ.copy()
for line in captured.splitlines():
    if "=" in line and not line.startswith("="):
        key, value = line.split("=", 1)
        environment[key.upper()] = value

# A portable, pinned SDK supplies build headers/import libraries when no system SDK is installed.
# These Microsoft packages stay in build/toolchain; no installer or machine configuration runs.
toolchain = ROOT / "build/toolchain"
sdk_version = "10.0.26100.9169"
sdk_packages = {
    "microsoft.windows.sdk.cpp": "475269434dcd808a67853773272f972c3229c0e10c3ddc821290e70cc0f6904d",
    "microsoft.windows.sdk.cpp.x64": "df6226a051e320942abfbd57848b43d18772996ecd66beadad240f2a56ed2f7b",
}
toolchain.mkdir(parents=True, exist_ok=True)
for package, expected in sdk_packages.items():
    archive = toolchain / f"{package}.{sdk_version}.nupkg"
    if not archive.is_file():
        urllib.request.urlretrieve(f"https://api.nuget.org/v3-flatcontainer/{package}/{sdk_version}/{archive.name}", archive)
    if hashlib.file_digest(archive.open("rb"), "sha256").hexdigest() != expected:
        raise SystemExit(f"SDK package checksum mismatch: {archive.name}")
    destination = toolchain / package
    if not (destination / ".extracted").is_file():
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as package_zip:
            for member in package_zip.infolist():
                target = (destination / member.filename).resolve()
                if not target.is_relative_to(destination.resolve()):
                    raise SystemExit("SDK package contained an unsafe path")
            package_zip.extractall(destination)
        (destination / ".extracted").write_text(sdk_version, encoding="utf-8")
sdk_headers = toolchain / "microsoft.windows.sdk.cpp/c/Include/10.0.26100.0"
sdk_libraries = toolchain / "microsoft.windows.sdk.cpp.x64/c"
environment["INCLUDE"] = environment.get("INCLUDE", "") + ";" + ";".join(str(sdk_headers / name) for name in ("ucrt", "shared", "um", "winrt"))
environment["LIB"] = environment.get("LIB", "") + ";" + ";".join(str(sdk_libraries / name / "x64") for name in ("um", "ucrt"))

output = ROOT / "build/native/windows-x86_64"
objects = ROOT / "build/native-obj"
output.mkdir(parents=True, exist_ok=True)
objects.mkdir(parents=True, exist_ok=True)
library = output / "sectorpad-input-windows-x86_64.dll"
source = ROOT / "src/main/native/sectorpad_input_windows.c"
include = ROOT / "src/main/native/include"
compiler = shutil.which("cl.exe", path=environment.get("PATH", ""))
if compiler is None:
    raise SystemExit("MSVC x64 compiler was not found after loading vcvars64")
subprocess.run([compiler, "/nologo", "/LD", "/O2", "/W4", "/WX", "/MT", "/Brepro",
                "/DUNICODE", "/D_UNICODE", f"/I{include}", f"/Fo{objects / 'sectorpad_input_windows.obj'}",
                str(source), "/link", "user32.lib", "kernel32.lib", "/Brepro",
                f"/OUT:{library}", f"/IMPLIB:{objects / 'sectorpad_input_windows.lib'}"],
               cwd=objects, env=environment, check=True)
print(library)
