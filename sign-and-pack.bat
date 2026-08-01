@echo off
chcp 65001 >nul
echo ===== 抢票助手 - 自签名打包脚本 =====
echo.

REM 检查 Android SDK
if "%ANDROID_HOME%"=="" (
    if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk" (
        set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
    ) else (
        echo [错误] 未找到 Android SDK
        echo 请设置 ANDROID_HOME 环境变量，或安装 Android Studio
        pause
        exit /b 1
    )
)

set BUILD_TOOLS=%ANDROID_HOME%\build-tools\34.0.0

REM 1. 生成自签名密钥（仅首次需要）
set KEYSTORE_FILE=ticket_helper.jks
if not exist "%KEYSTORE_FILE%" (
    echo [1/5] 生成自签名密钥...
    "%JAVA_HOME%\bin\keytool" -genkey -v -keystore "%KEYSTORE_FILE%" ^
        -alias ticket_helper -keyalg RSA -keysize 2048 -validity 36500 ^
        -storepass 123456 -keypass 123456 ^
        -dname "CN=TicketHelper, OU=DEV, O=TicketHelper, L=Beijing, ST=Beijing, C=CN"
    if !errorlevel! neq 0 (
        echo [错误] 密钥生成失败
        pause
        exit /b 1
    )
    echo     密钥已生成: %KEYSTORE_FILE%
) else (
    echo [1/5] 密钥已存在，跳过
)

REM 2. 编译
echo [2/5] 编译项目...
call gradlew assembleRelease
if %errorlevel% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

REM 3. 对齐
set UNALIGNED_APK=app\build\outputs\apk\release\app-release-unsigned.apk
set ALIGNED_APK=app\build\outputs\apk\release\app-release-aligned.apk
echo [3/5] 对齐APK...
"%BUILD_TOOLS%\zipalign" -v -p 4 "%UNALIGNED_APK%" "%ALIGNED_APK%"
if %errorlevel% neq 0 (
    echo [错误] 对齐失败
    pause
    exit /b 1
)

REM 4. 签名
set SIGNED_APK=TicketHelper_v1.0.0.apk
echo [4/5] 签名APK...
"%BUILD_TOOLS%\apksigner" sign --ks "%KEYSTORE_FILE%" --ks-key-alias ticket_helper ^
    --ks-pass pass:123456 --key-pass pass:123456 ^
    --out "%SIGNED_APK%" "%ALIGNED_APK%"
if %errorlevel% neq 0 (
    echo [错误] 签名失败
    pause
    exit /b 1
)

REM 5. 验证
echo [5/5] 验证签名...
"%BUILD_TOOLS%\apksigner" verify "%SIGNED_APK%"
if %errorlevel% neq 0 (
    echo [警告] 签名验证未通过
) else (
    echo     签名验证通过！
)

echo.
echo ===== 打包完成！=====
echo 输出文件: %SIGNED_APK%
echo.
echo 安装方式：
echo   1. 将 APK 传送到手机
echo   2. 在手机上打开文件管理器，点击安装
echo   3. 如提示未知来源，请允许安装
echo.

pause
