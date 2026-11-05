@echo off
rem Builds Flip Ledger (only if the code changed), then starts RuneLite with Flip Ledger and your normal Plugin Hub plugins.
rem   Launch RuneLite.bat          build if needed, launch, close this window
rem   Launch RuneLite.bat safe     also skip Plugin Hub plugins
rem   Launch RuneLite.bat quick    skip the build step
rem   Launch RuneLite.bat debug    keep this window open and write build\launch.log
setlocal
title RuneLite + Flip Ledger
set "JAR=%~dp0build\libs\flip-ledger-runelite.jar"
set "LOG=%~dp0build\launch.log"

rem Find Java 11+: IntelliJ's downloaded JDKs first, then JAVA_HOME, then PATH
set "JAVA="
for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%D\bin\java.exe" set "JAVA=%%D\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA=%%~$PATH:J"
if not defined JAVA (
  echo Couldn't find Java. In IntelliJ: File ^> Project Structure ^> SDK ^> Download JDK ^(version 11 or newer^).
  pause
  exit /b 1
)

set "EXTRA="
set "DEBUG="
set "QUICK="
:args
if "%~1"=="" goto build
if /i "%~1"=="safe" set "EXTRA=--safe-mode"
if /i "%~1"=="debug" set "DEBUG=1"
if /i "%~1"=="quick" set "QUICK=1"
shift
goto args

:build
if defined QUICK goto check
rem Build with the same Java (gradlew uses JAVA_HOME). Gradle skips the work when nothing changed.
for %%I in ("%JAVA%\..\..") do set "JAVA_HOME=%%~fI"
echo Building Flip Ledger (first time can take a few minutes)...
call "%~dp0gradlew.bat" -p "%~dp0." devJar -q
if errorlevel 1 (
  echo.
  echo The build failed. Fix the errors above, or run with "quick" to launch the last good build.
  pause
  exit /b 1
)

:check
if not exist "%JAR%" (
  echo Can't find %JAR%
  echo Run this again without "quick" so it can build first.
  pause
  exit /b 1
)
if defined DEBUG goto debug

rem Normal launch: start RuneLite with javaw (no console window) and close this window right away.
set "JAVAW=%JAVA:java.exe=javaw.exe%"
if not exist "%JAVAW%" set "JAVAW=%JAVA%"
start "" "%JAVAW%" -ea -Xmx768m -jar "%JAR%" --developer-mode %EXTRA%
exit /b 0

:debug
echo Using Java: %JAVA%
echo Debug mode: keep this window open while you play. Log: %LOG%
> "%LOG%" echo Java: %JAVA%
"%JAVA%" -version >> "%LOG%" 2>&1
"%JAVA%" -ea -Xmx768m -jar "%JAR%" --developer-mode %EXTRA% >> "%LOG%" 2>&1
if errorlevel 1 (
  echo.
  echo RuneLite closed with an error. The details are in:
  echo   %LOG%
  pause
)
