@echo off
setlocal

set ROOT=%~dp0
set GRADLE_VERSION=8.10.2
set GRADLE_DIST_DIR=%ROOT%.gradle\dist
set GRADLE_BAT=%GRADLE_DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat

if not exist "%GRADLE_BAT%" (
  powershell -ExecutionPolicy Bypass -NoProfile -Command "$ErrorActionPreference='Stop'; $Root='%ROOT%'; $Version='%GRADLE_VERSION%'; $Dist=Join-Path $Root '.gradle\dist'; $Tmp=Join-Path $Root '.gradle\tmp'; New-Item -ItemType Directory -Force -Path $Dist, $Tmp | Out-Null; $zip=Join-Path $Tmp \"gradle-$Version-bin.zip\"; Invoke-WebRequest -Uri \"https://services.gradle.org/distributions/gradle-$Version-bin.zip\" -OutFile $zip -UseBasicParsing; Expand-Archive -LiteralPath $zip -DestinationPath $Dist -Force; Remove-Item -LiteralPath $zip -Force"
)

set GRADLE_USER_HOME=%ROOT%\.gradle\home
if exist "C:\Program Files\Java\jdk-21" (
  set JAVA_HOME=C:\Program Files\Java\jdk-21
  set PATH=%JAVA_HOME%\bin;%PATH%
)

if "%ANDROID_HOME%"=="" set ANDROID_HOME=D:\0.1编程\AndroidSdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%

call "%GRADLE_BAT%" %*
