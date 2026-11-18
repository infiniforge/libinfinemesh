#!/usr/bin/env python3
"""Optional host regression using actual viewer shaders and ClipPlanes, EGL and GLES2."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
renderer = (root / 'mesh-android/src/main/java/io/github/gretchycat/infinemesh/android/MeshRenderer.java').read_text()
compiler = shutil.which('clang') or shutil.which('cc')
if not compiler:
    raise SystemExit('Install a C compiler, EGL and GLES2 development libraries for this optional check.')
with tempfile.TemporaryDirectory(prefix='infinemesh-depth-') as temporary:
    directory = Path(temporary)
    header = directory / 'viewer-shaders.h'
    header.write_text('\n'.join('static const char *' + name + '_source=' + json.dumps(re.search(r'String ' + name + r'="([^"]+)";', renderer).group(1)) + ';' for name in ('vertex','fragment')))
    probe = directory / 'ClipProbe.java'
    probe.write_text('''package io.github.gretchycat.infinemesh.android;
public class ClipProbe { public static void main(String[] args) {
    ClipPlanes clip=ClipPlanes.fit(4.8f,2.35f); System.out.println(clip.near+" "+clip.far);
} }''')
    subprocess.run(['javac','-d',str(directory),str(root/'mesh-android/src/main/java/io/github/gretchycat/infinemesh/android/ClipPlanes.java'),str(probe)],check=True)
    clip = subprocess.check_output(['java','-cp',str(directory),'io.github.gretchycat.infinemesh.android.ClipProbe'],text=True).split()
    executable = directory / 'depth-render-smoke'
    subprocess.run([compiler,str(root/'scripts/depth-render-smoke.c'),'-I',str(directory),'-lEGL','-lGLESv2','-o',str(executable)],check=True)
    environment = os.environ.copy()
    environment.update(EGL_PLATFORM='surfaceless',LIBGL_ALWAYS_SOFTWARE='true')
    subprocess.run([str(executable),*clip],env=environment,check=True)
print('Closely spaced surfaces render in the correct order with both depth configurations.')
