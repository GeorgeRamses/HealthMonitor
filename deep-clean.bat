@echo off
REM Clean Gradle Build Script
REM This script resolves "Cannot add extension with name 'kotlin'" errors

echo.
echo ========================================
echo Health Monitor - Deep Clean Gradle Build
echo ========================================
echo.

setlocal enabledelayedexpansion

cd /d D:\George\Projects\Android\health-monitor

if not exist "build.gradle.kts" (
    echo ERROR: Not in project root directory!
    exit /b 1
)

echo [1/5] Stopping Gradle daemon...
call gradlew.bat --stop
timeout /t 2 /nobreak

echo [2/5] Deleting .gradle folder...
if exist ".gradle" (
    rmdir /s /q ".gradle"
    echo Deleted: .gradle
) else (
    echo .gradle already clean
)

echo [3/5] Deleting build folders...
if exist "build" (
    rmdir /s /q "build"
    echo Deleted: build
)
if exist "app\build" (
    rmdir /s /q "app\build"
    echo Deleted: app/build
)

echo [4/5] Clearing gradle cache...
if exist "%USERPROFILE%\.gradle\caches" (
    echo Clearing %USERPROFILE%\.gradle\caches
    rmdir /s /q "%USERPROFILE%\.gradle\caches" 2>nul
)

echo.
echo [5/5] Ready for clean build!
echo.
echo ========================================
echo Next steps:
echo ========================================
echo 1. In Android Studio:
echo    - File → Invalidate Caches / Restart
echo    - Click: Invalidate and Restart
echo.
echo 2. After restart, Android Studio will:
echo    - Re-download Gradle 9.1.0
echo    - Re-sync your project
echo    - Rebuild everything
echo.
echo 3. Build signed APK:
echo    - Build → Build APK(s)
echo.
echo ========================================
echo.
echo Clean build setup complete! ✓
pause

