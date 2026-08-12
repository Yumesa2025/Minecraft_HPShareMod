@echo off
setlocal
title Minecraft SharedFate Fabric Server
cd /d "%~dp0"

powershell.exe -NoLogo -NoProfile -File "%~dp0sharedfate-server-loop.ps1" ^
  -ServerRoot "%~dp0." -JarFile "fabric-server-launch.jar" -MinMemory "1G" -MaxMemory "2G"

echo.
echo Server stopped. Check the message above if it did not restart.
pause
