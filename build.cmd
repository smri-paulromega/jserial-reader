@echo off
rem Builds build\jserial-reader.jar. Needs a JDK 8 or later; the jar runs on Java 8 and later.
setlocal
cd /d "%~dp0"

set "JAVAC=javac"
set "JAR=jar"
if defined JAVA_HOME set "JAVAC=%JAVA_HOME%\bin\javac"
if defined JAVA_HOME set "JAR=%JAVA_HOME%\bin\jar"
set /p VERSION=<VERSION

rem JDK 9+ takes --release 8; JDK 8 itself only knows -source/-target
"%JAVAC%" --release 8 -version >nul 2>&1
if errorlevel 1 (set "TARGET=-source 1.8 -target 1.8") else (set "TARGET=--release 8")

if exist build\classes rmdir /s /q build\classes
mkdir build\classes
"%JAVAC%" %TARGET% -Xlint:-options -encoding UTF-8 -d build\classes src\main\java\jserialreader\JSerialReader.java || exit /b 1
> build\MANIFEST.MF echo Main-Class: jserialreader.JSerialReader
>> build\MANIFEST.MF echo Implementation-Title: jserial-reader
>> build\MANIFEST.MF echo Implementation-Version: %VERSION%
"%JAR%" cfm build\jserial-reader.jar build\MANIFEST.MF -C build\classes . || exit /b 1
echo Built build\jserial-reader.jar (%VERSION%)
