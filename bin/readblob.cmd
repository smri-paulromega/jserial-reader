@echo off
rem readblob - prints Java-serialized data as a readable tree. Run "readblob --help" for usage.
rem Looks for the jar in ..\lib (release download) or ..\build (source checkout).
setlocal

set "JAR=%~dp0..\lib\jserial-reader.jar"
if not exist "%JAR%" set "JAR=%~dp0..\build\jserial-reader.jar"
if not exist "%JAR%" (
	echo readblob: jserial-reader.jar not found; run build.cmd first 1>&2
	exit /b 1
)

set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"

rem JAVA_OPTS passes extra JVM options, e.g. set JAVA_OPTS=-Duser.timezone=UTC
"%JAVA%" %JAVA_OPTS% -jar "%JAR%" %*
exit /b %ERRORLEVEL%
