#!/usr/bin/env bash
# Builds build/DollarRiskBox.jar from the sources.
#
#   bash build.sh            build only
#   bash build.sh install    build, then copy into your "MotiveWave Extensions" folder
#
# Needs a JDK (17+; the jar is compiled for Java 21 bytecode) and the MotiveWave SDK jar,
# which ships inside your MotiveWave installation (it is NOT part of this repository).
# Override locations with environment variables if yours differ:
#   MW_SDK   path to mwave_sdk.jar   (default: macOS app bundle)
#   MW_EXT   extensions folder       (default: ~/MotiveWave Extensions)
#   JAVA_HOME
set -euo pipefail
cd "$(dirname "$0")"

SDK="${MW_SDK:-/Applications/MotiveWave.app/Contents/Java/mwave_sdk.jar}"
EXT="${MW_EXT:-$HOME/MotiveWave Extensions}"

# macOS ships a /usr/bin/javac stub that fails without a JDK, so every candidate is test-run.
JAVAC=""; JAR=""
candidates=()
[[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME/bin")
if [[ -x /usr/libexec/java_home ]]; then
  jh="$(/usr/libexec/java_home 2>/dev/null || true)"; [[ -n "$jh" ]] && candidates+=("$jh/bin")
fi
candidates+=(/opt/homebrew/opt/openjdk/bin /usr/local/opt/openjdk/bin)
if command -v javac >/dev/null 2>&1; then candidates+=("$(dirname "$(command -v javac)")"); fi
for d in "${candidates[@]}"; do
  if [[ -x "$d/javac" && -x "$d/jar" ]] && "$d/javac" -version >/dev/null 2>&1; then
    JAVAC="$d/javac"; JAR="$d/jar"; break
  fi
done
[[ -n "$JAVAC" ]] || { echo "No working JDK found. Install one (e.g. 'brew install openjdk') or set JAVA_HOME." >&2; exit 1; }
[[ -f "$SDK" ]] || { echo "MotiveWave SDK jar not found: $SDK (set MW_SDK)" >&2; exit 1; }

rm -rf build && mkdir -p build/classes/dollar_risk_box/nls
"$JAVAC" --release 21 -encoding UTF-8 -Xlint:all,-auxiliaryclass -cp "$SDK" -d build/classes dollar_risk_box/DollarRiskBox.java
cp dollar_risk_box/nls/strings.properties build/classes/dollar_risk_box/nls/
"$JAR" cf build/DollarRiskBox.jar -C build/classes .
echo "built build/DollarRiskBox.jar"

if [[ "${1:-}" == "install" ]]; then
  mkdir -p "$EXT"
  # A new file name each time: a running MotiveWave caches an open jar by path, so a replaced
  # jar with the same name would load its classes but not its text resources until a restart.
  rm -f "$EXT"/DollarRiskBox*.jar "$EXT"/PLDollarTarget*.jar   # also drops the pre-rename jar
  cp build/DollarRiskBox.jar "$EXT/DollarRiskBox-$(date +%s).jar"
  touch "$EXT/.last_updated"   # MotiveWave rescans the folder when this file changes
  echo "installed into: $EXT"
fi
