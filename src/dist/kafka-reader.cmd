@echo off
setlocal

set KAFKA_READER_HOME=%CD%
set JAVA_CMD=javaw.exe

if not exist "%JAVA_HOME%\bin\javaw.exe" goto check
set JAVA_CMD=%JAVA_HOME%\bin\javaw.exe

:check
"%JAVA_CMD%" -version >nul 2>&1
if %errorlevel% neq 0 goto error

if not exist "%KAFKA_READER_HOME%\kafka-reader.jvm" goto start

@setlocal EnableExtensions EnableDelayedExpansion
for /f "usebackq delims=" %%a in ("%KAFKA_READER_HOME%\kafka-reader.jvm") do set JVM_CONFIG=!JVM_CONFIG! %%a
@endlocal & set JVM_CONFIG=%JVM_CONFIG%

:start
start "Kafka Reader 2026.08" /B "%JAVA_CMD%" %JVM_CONFIG% -jar "%KAFKA_READER_HOME%\kafka-reader.jar" %*
endlocal & exit /b %errorlevel%

:error
echo Install Java (JDK or JRE) if you do not already have. Kafka Reader will not work without it.
echo Please make sure PATH, or JAVA_HOME environment variables point to a valid Java installation.
echo Could not execute Kafka Reader (exit: %errorlevel%)
endlocal & exit /b %errorlevel%
