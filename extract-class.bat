@echo off
setlocal enabledelayedexpansion

REM ========================================
REM Extract Single Minecraft Class API
REM ========================================

if "%~1"=="" (
    echo Usage: extract-class.bat ^<full.class.name^>
    echo.
    echo Example:
    echo   extract-class.bat net.minecraft.world.item.ItemStack
    echo.
    echo This will generate:
    echo   - api_ref/ItemStack.md     ^(human-readable^)
    echo   - api_ref/ItemStack.json   ^(machine-readable^)
    echo.
    pause
    exit /b 1
)

set "CLASS_NAME=%~1"

REM Extract simple class name from full path
for %%i in ("%CLASS_NAME:.= %") do set "SIMPLE_NAME=%%i"

echo ========================================
echo Extracting API: %CLASS_NAME%
echo ========================================
echo.

REM Check if already exists
if exist "api_ref\%SIMPLE_NAME%.md" (
    echo Warning: api_ref\%SIMPLE_NAME%.md already exists
    echo.
    set /p "OVERWRITE=Overwrite? (y/N): "
    if /i not "!OVERWRITE!"=="y" (
        echo Cancelled.
        pause
        exit /b 0
    )
    echo.
)

echo Extracting...
call gradlew.bat :common:extractApi -PapiClasses="%CLASS_NAME%" --console=plain

echo.
if exist "api_ref\%SIMPLE_NAME%.md" (
    echo ========================================
    echo SUCCESS!
    echo ========================================
    echo.
    echo Generated files:
    echo   - api_ref\%SIMPLE_NAME%.md
    echo   - api_ref\%SIMPLE_NAME%.json
    echo.
    echo View documentation:
    echo   type api_ref\%SIMPLE_NAME%.md
    echo.
) else (
    echo ========================================
    echo FAILED
    echo ========================================
    echo.
    echo The class could not be extracted. Possible reasons:
    echo   1. Class name is incorrect
    echo   2. Class requires Minecraft bootstrap
    echo   3. Class is not accessible via reflection
    echo.
    echo Try checking the full class name with:
    echo   https://linkie.shedaniel.dev/mappings
    echo.
)

pause
