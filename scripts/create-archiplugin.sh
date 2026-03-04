#!/bin/sh
# Create ArchiGPT.archiplugin for installation via Help → Manage Archi Plug-ins.
# Run from repo root after building the plugin (mvn package -pl com.archimatetool.archigpt ...).

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/com.archimatetool.archigpt/target/com.archimatetool.archigpt-1.0.0-SNAPSHOT.jar"
OUT="$ROOT/export/ArchiGPT.archiplugin"

if [ ! -f "$JAR" ]; then
  echo "Plugin JAR not found. Build it first: mvn package -pl com.archimatetool.archigpt -P with-archi -Darchi.repo.path=..."
  exit 1
fi

mkdir -p "$ROOT/export"
# .archiplugin = zip with magic entry "archi-plugin" + plugin JAR (see Archi DropinsPluginHandler)
python3 - "$OUT" "$JAR" << 'PY'
import zipfile, os, sys
out_path, jar_path = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out_path, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('archi-plugin', '')
    z.write(jar_path, os.path.basename(jar_path))
PY
echo "Created $OUT"
