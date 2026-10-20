#!/usr/bin/env bash
# Mirror of .github/workflows/build_js.yml for local use (Windows/Git Bash).
# Usage: bash scripts/build-js-sources.sh
set -uo pipefail

# Forward slashes: this script drives the POSIX `gradlew`, which tests
# "$JAVA_HOME/bin/java". Backslashes are consumed as escapes by bash and break it.
JDK="C:/Users/PC/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
export JAVA_HOME="${JAVA_HOME:-$JDK}"
cd "$(dirname "$0")/.."

echo "JAVA_HOME=$JAVA_HOME"

# 1. KSP for every source with enableJs = true (CI does this as a separate step).
KSP_TASKS=""
while IFS= read -r BUILD_FILE; do
  DIR=$(dirname "$BUILD_FILE")
  NAME=$(basename "$DIR")
  LANG=$(basename "$(dirname "$DIR")")
  [ "$LANG" = "sources" ] && continue
  LANG_CAP="$(echo "${LANG:0:1}" | tr '[:lower:]' '[:upper:]')${LANG:1}"
  KSP_TASKS="$KSP_TASKS :extensions:individual:$LANG:$NAME:ksp${LANG_CAP}ReleaseKotlin"
  echo "JS source: $NAME ($LANG)"
done < <(grep -rl "enableJs\s*=\s*true" sources/ 2>/dev/null)

[ -z "$KSP_TASKS" ] && { echo "No JS-enabled sources found"; exit 1; }

./gradlew $KSP_TASKS --parallel --console=plain || exit 1

# 2. Bundle + index.
./gradlew :js-sources:jsBrowserProductionWebpack :js-sources:createSourceIndex --parallel --console=plain || exit 1

echo "--- js-dist ---"
ls -la js-sources/build/js-dist/
