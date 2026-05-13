#!/bin/sh
# Run Maven for ArchiGPT with -P with-archi and the local Archi p2 repository.
# Expects a sibling clone at: <repo-root>/../archi (see docs/BUILD.md).
# Usage (from anywhere): scripts/mvn-with-archi.sh clean package
# Override p2 location: ARCHI_REPO_PATH=/path/to/repository scripts/mvn-with-archi.sh test

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_ARCHI_REPO="$ROOT/../archi/com.archimatetool.editor.product/target/repository"
ARCHI_REPO_PATH="${ARCHI_REPO_PATH:-$DEFAULT_ARCHI_REPO}"
if [ -d "$ARCHI_REPO_PATH" ]; then
  ARCHI_REPO_PATH="$(cd "$ARCHI_REPO_PATH" && pwd)"
fi

export MAVEN_OPTS="${MAVEN_OPTS:--Djdk.xml.maxGeneralEntitySizeLimit=2147483647 -Djdk.xml.totalEntitySizeLimit=2147483647}"

cd "$ROOT"
echo "Using Archi p2: $ARCHI_REPO_PATH"
exec mvn -P with-archi -Darchi.repo.path="$ARCHI_REPO_PATH" "$@"
