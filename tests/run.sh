#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
work="$(mktemp -d)"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$work" src/fun/hpqq/kindleremote/{InputGate,RemoteClient}.java tests/RemoteTest.java
"$JAVA_HOME/bin/java" -cp "$work" RemoteTest
