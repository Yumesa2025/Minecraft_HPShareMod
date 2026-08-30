@echo off
rem 콘솔을 UTF-8 로 맞춘다. 기본 CP949 로 두면 서버가 찍는 한글이 전부 깨져 보인다.
chcp 65001 > nul
setlocal
title Minecraft SharedFate Fabric Server
cd /d "%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0sharedfate-server-loop.ps1" ^
  -ServerRoot "%~dp0." -JarFile "fabric-server-launch.jar" -MinMemory "1G" -MaxMemory "2G"

echo.
echo Server stopped. Check the message above if it did not restart.
pause
