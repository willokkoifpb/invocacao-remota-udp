#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC=javac
    JAR=jar
fi
mkdir -p build/classes
find src/main/java -name '*.java' > build/sources.txt
"$JAVAC" --release 17 -encoding UTF-8 -Xlint:all -Werror -d build/classes @build/sources.txt
"$JAR" --create --file build/udp.jar --main-class br.edu.udp.Main -C build/classes .
echo 'Gerado: build/udp.jar'
