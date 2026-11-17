#!/bin/sh
set -eu
cd "$(dirname "$0")"
sh build.sh
if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=javac
    JAVA=java
fi
mkdir -p build/test-classes
find src/test/java -name '*.java' > build/test-sources.txt
"$JAVAC" --release 17 -encoding UTF-8 -d build/test-classes -cp build/classes @build/test-sources.txt
"$JAVA" -cp 'build/classes:build/test-classes' br.edu.udp.ProtocolTest
