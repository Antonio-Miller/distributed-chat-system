@echo off
REM Build script for CPD Chat System (Windows) — produces a runnable JAR

setlocal enabledelayedexpansion
cd /d "%~dp0"

set VERSION=1.0
set BUILD_DIR=build
set OUT_DIR=%BUILD_DIR%\classes
set JAR_NAME=ChatSystem-%VERSION%.jar

echo === CPD Chat System Build ===
java -version 2>&1 | findstr /R "version" | findstr "21" >nul
if errorlevel 1 (
    echo Warning: Java 21+ not found. Build may fail.
)

REM Clean
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%OUT_DIR%"

REM Compile
echo Compiling...
dir /s /b src\main\java\*.java > sources.txt
javac --release 21 -d "%OUT_DIR%" @sources.txt
if errorlevel 1 (
    echo Compilation failed!
    exit /b 1
)

REM Create manifest
echo Creating manifest...
(
    echo Manifest-Version: 1.0
    echo Implementation-Title: CPD Chat System
    echo Implementation-Version: %VERSION%
    echo Main-Class: chat.server.ChatServer
) > "%BUILD_DIR%\MANIFEST.MF"

REM Package JAR
echo Packaging JAR...
cd "%OUT_DIR%"
jar cfm "..\..\%JAR_NAME%" "..\..\%BUILD_DIR%\MANIFEST.MF" .
cd ..\..

echo.
echo Build successful: %JAR_NAME%
echo.
echo Usage:
echo   Server:  java -jar %JAR_NAME% [port] [users-file] [--tls]
echo   Client:  java -cp %JAR_NAME% chat.client.ChatClient [host] [port] [--tls]
echo.
echo Examples:
echo   java -jar %JAR_NAME%
echo   java -jar %JAR_NAME% 9000 data/users.db --tls
echo.
endlocal
