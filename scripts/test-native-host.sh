#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK (17+)}"
build="$(mktemp -d)"
trap 'rm -rf "$build"' EXIT
"${CC:-cc}" -shared -fPIC -D_GNU_SOURCE -Wall -Wextra \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  third_party/termux-pty/termux.c -o "$build/libtermux.so"
"$JAVA_HOME/bin/javac" -d "$build" core/runtime/src/main/java/com/termux/terminal/JNI.java tests/native/PtyHostTest.java
timeout 30s "$JAVA_HOME/bin/java" --add-opens java.base/java.io=ALL-UNNAMED -Djava.library.path="$build" -cp "$build" PtyHostTest
