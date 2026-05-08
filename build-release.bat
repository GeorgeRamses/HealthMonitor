@REM === Health Monitor - Build & Sign Script ===
@REM This script builds a signed release APK/AAB for distribution

@echo off
setlocal enabledelayedexpansion

echo.
echo ========================================
echo Health Monitor - App Distribution Build
echo ========================================
echo.

REM Change to project directory
cd /d D:\George\Projects\Android\health-monitor

REM Check if running in project root
if not exist "build.gradle.kts" (
    echo ERROR: Not in project root directory!
    exit /b 1
)

REM Menu
:MENU
echo.
echo Select build type:
echo 1. Build signed APK (for direct distribution)
echo 2. Build signed AAB (for Google Play Store)
echo 3. Build both APK and AAB
echo 4. Clean before building
echo 5. Exit
echo.
set /p choice="Enter your choice (1-5): "

if "%choice%"=="1" goto BUILD_APK
if "%choice%"=="2" goto BUILD_AAB
if "%choice%"=="3" goto BUILD_BOTH
if "%choice%"=="4" goto CLEAN
if "%choice%"=="5" goto END
goto MENU

:BUILD_APK
echo.
echo Building signed APK...
call gradlew.bat assembleRelease
if %errorlevel% equ 0 (
    echo.
    echo ✓ APK built successfully!
    echo Location: app\build\outputs\apk\release\app-release.apk
    echo.
    set /p openFile="Open output folder? (y/n): "
    if /i "!openFile!"=="y" (
        start "" "app\build\outputs\apk\release"
    )
) else (
    echo.
    echo ✗ APK build failed!
)
goto MENU

:BUILD_AAB
echo.
echo Building signed AAB...
call gradlew.bat bundleRelease
if %errorlevel% equ 0 (
    echo.
    echo ✓ AAB built successfully!
    echo Location: app\build\outputs\bundle\release\app-release.aab
    echo.
    set /p openFile="Open output folder? (y/n): "
    if /i "!openFile!"=="y" (
        start "" "app\build\outputs\bundle\release"
    )
) else (
    echo.
    echo ✗ AAB build failed!
)
goto MENU

:BUILD_BOTH
echo.
echo Building signed APK and AAB...
call gradlew.bat assembleRelease bundleRelease
if %errorlevel% equ 0 (
    echo.
    echo ✓ Both APK and AAB built successfully!
    echo APK Location: app\build\outputs\apk\release\app-release.apk
    echo AAB Location: app\build\outputs\bundle\release\app-release.aab
    echo.
    set /p openFile="Open output folder? (y/n): "
    if /i "!openFile!"=="y" (
        start "" "app\build\outputs"
    )
) else (
    echo.
    echo ✗ Build failed!
)
goto MENU

:CLEAN
echo.
echo Cleaning project...
call gradlew.bat clean
echo ✓ Project cleaned
goto MENU

:END
echo.
echo Thank you for using Health Monitor Distribution Builder!
echo.
endlocal

