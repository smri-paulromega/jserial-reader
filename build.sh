#!/bin/sh
# Builds build/jserial-reader.jar. Needs a JDK 8 or later; the jar runs on Java 8 and later.
set -eu
cd "$(dirname "$0")"

if [ -n "${JAVA_HOME:-}" ]; then
	JAVAC="$JAVA_HOME/bin/javac"
	JAR="$JAVA_HOME/bin/jar"
else
	JAVAC=javac
	JAR=jar
fi
VERSION=$(tr -d '\r\n' < VERSION)

# JDK 9+ takes --release 8; JDK 8 itself only knows -source/-target
if "$JAVAC" --release 8 -version >/dev/null 2>&1; then
	TARGET="--release 8"
else
	TARGET="-source 1.8 -target 1.8"
fi

rm -rf build/classes
mkdir -p build/classes
# shellcheck disable=SC2086 # TARGET holds two words on purpose
"$JAVAC" $TARGET -Xlint:-options -encoding UTF-8 -d build/classes src/main/java/jserialreader/JSerialReader.java
printf 'Main-Class: jserialreader.JSerialReader\nImplementation-Title: jserial-reader\nImplementation-Version: %s\n' \
	"$VERSION" > build/MANIFEST.MF
"$JAR" cfm build/jserial-reader.jar build/MANIFEST.MF -C build/classes .
echo "Built build/jserial-reader.jar ($VERSION)"
