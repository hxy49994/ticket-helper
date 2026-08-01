@rem Gradle wrapper stub
@echo off
set DIRNAME=%~dp0
if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not set.
    echo.
    echo Please install JDK 17+ and set JAVA_HOME.
    echo Example: set JAVA_HOME=C:\Program Files\Java\jdk-17
    pause
    exit /b 1
)
"%JAVA_HOME%\bin\java" -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found at %%JAVA_HOME%%
    pause
    exit /b 1
)
"%JAVA_HOME%\bin\java" -jar "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" %*
pause
