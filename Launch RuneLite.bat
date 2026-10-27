@echo off
rem Starts RuneLite with Flip Ledger and all your normal Plugin Hub plugins.
rem First time (and after changing the plugin): run the Gradle task "devJar" in IntelliJ.
rem Add "safe" after the file name (Launch RuneLite.bat safe) to skip Plugin Hub plugins.
setlocal
set "JAR=%~dp0build\libs\flip-ledger-runelite.jar"
if not exist "%JAR%" (
  echo Can't find %JAR%
  echo In IntelliJ, open the Gradle panel and run Tasks ^> application ^> devJar first.
  pause
  exit /b 1
)

set "JAVAW="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAW for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%D\bin\javaw.exe" set "JAVAW=%%D\bin\javaw.exe"
if not defined JAVAW for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" set "JAVAW=%%~$PATH:J"
if not defined JAVAW (
  echo Couldn't find Java. Install a JDK 11+ (IntelliJ: File ^> Project Structure ^> SDK ^> Download JDK).
  pause
  exit /b 1
)

set "EXTRA="
if /i "%~1"=="safe" set "EXTRA=--safe-mode"
start "" "%JAVAW%" -ea -Xmx768m -jar "%JAR%" --developer-mode %EXTRA%
