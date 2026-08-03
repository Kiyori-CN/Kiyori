@echo off
setlocal enabledelayedexpansion

rem Directory where this script lives (normalize without trailing backslash)
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

rem Locate the Kiyori project root.
pushd "%SCRIPT_DIR%..\.." >nul 2>nul
set "PROJECT_ROOT=%CD%"
popd >nul 2>nul

if not defined ANDROID_HOME (
    echo [ERROR] ANDROID_HOME is not set.
    exit /b 1
)

set "NDK_VERSION="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_ROOT%\gradle.properties") do (
    if "%%A"=="kiyori.android.ndkVersion" set "NDK_VERSION=%%B"
)
if not defined NDK_VERSION (
    echo [ERROR] kiyori.android.ndkVersion is missing from gradle.properties.
    exit /b 1
)

set "ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\%NDK_VERSION%"
set "CMAKE_EXE=%ANDROID_HOME%\cmake\3.22.1\bin\cmake.exe"
set "NINJA_EXE=%ANDROID_HOME%\cmake\3.22.1\bin\ninja.exe"
if not exist "%ANDROID_NDK_HOME%\build\cmake\android.toolchain.cmake" (
    echo [ERROR] Required NDK not found: %ANDROID_NDK_HOME%
    exit /b 1
)
if not exist "%CMAKE_EXE%" (
    echo [ERROR] Required CMake not found: %CMAKE_EXE%
    exit /b 1
)
if not exist "%NINJA_EXE%" (
    echo [ERROR] Required Ninja not found: %NINJA_EXE%
    exit /b 1
)

echo Using ANDROID_NDK_HOME=%ANDROID_NDK_HOME%

rem Configure CMake build directory under this folder (clean old cache to avoid generator mismatch)
if exist "%SCRIPT_DIR%\build" rmdir /S /Q "%SCRIPT_DIR%\build"
mkdir "%SCRIPT_DIR%\build"

"%CMAKE_EXE%" -G Ninja -S "%SCRIPT_DIR%" -B "%SCRIPT_DIR%\build" ^
  -DCMAKE_TOOLCHAIN_FILE="%ANDROID_NDK_HOME%\build\cmake\android.toolchain.cmake" ^
  -DCMAKE_MAKE_PROGRAM="%NINJA_EXE%" ^
  -DANDROID_ABI=arm64-v8a ^
  -DANDROID_PLATFORM=android-26 ^
  -DCMAKE_BUILD_TYPE=Release
if errorlevel 1 goto :error

"%CMAKE_EXE%" --build "%SCRIPT_DIR%\build" --config Release
if errorlevel 1 goto :error

echo.
echo [1/1] Build finished. Gradle packages the same source as a generated asset.
set "BINARY=%SCRIPT_DIR%\build\operit_shell_exec"
if not exist "%BINARY%" (
    echo [ERROR] Binary not found after build: %BINARY%
    goto :error
)

echo [OK] Build finished: %BINARY%
exit /b 0

:error
echo.
echo [ERROR] Build failed.
exit /b 1
