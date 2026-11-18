#!/usr/bin/env python3
"""Compile the renderer's actual GLSL ES 1.00 sources using glslangValidator."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
validator = shutil.which('glslangValidator')
if not validator:
    raise SystemExit('Install glslangValidator to run the optional host shader check.')
source = (root / 'mesh-android/src/main/java/io/github/gretchycat/infinemesh/android/MeshRenderer.java').read_text()
with tempfile.TemporaryDirectory(prefix='infinemesh-shaders-') as directory:
    for name, stage in [('vertex', 'vert'), ('fragment', 'frag')]:
        shader = re.search(r'String ' + name + r'="([^"]+)";', source).group(1)
        target = Path(directory) / ('viewer.' + stage)
        target.write_text('#version 100\n' + shader)
        subprocess.run([validator, '-S', stage, str(target)], check=True)
print('Vertex and fragment shaders compile as GLSL ES 1.00.')
