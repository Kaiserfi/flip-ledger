@echo off
rem Starts RuneLite with Flip Ledger and all your normal Plugin Hub plugins.
rem First time (and after changing the plugin): run the Gradle task "devJar" in IntelliJ.
rem Add "safe" after the file name (Launch RuneLite.bat safe) to skip Plugin Hub plugins.
setlocal
title RuneLite + Flip Ledger
set "JAR=%~dp0build\libs\flip-ledger-runelite.jar"
set "LOG=%~dp0build\launch.log"
if not exist "%JAR%" (
  echo Can't find %JAR%
  echo In IntelliJ, open the Gradle panel and run Tasks ^> application ^> devJar first.
  pause
  exit /b 1
)

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
if /i "%~1"=="safe" set "EXTRA=--safe-mode"

echo Using Java: %JAVA%
echo Starting RuneLite with Flip Ledger... keep this window open while you play (you can minimize it).
echo Log: %LOG%
> "%LOG%" echo Java: %JAVA%
"%JAVA%" -version >> "%LOG%" 2>&1
"%JAVA%" -ea -Xmx768m -jar "%JAR%" --developer-mode %EXTRA% >> "%LOG%" 2>&1
if errorlevel 1 (
  echo.
  echo RuneLite closed with an error. The details are in:
  echo   %LOG%
  pause
)
