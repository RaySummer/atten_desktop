@echo off
setlocal enabledelayedexpansion

@echo off
:: 自动请求管理员权限
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo 请求管理员权限...
    goto UACPrompt
) else ( goto gotAdmin )
:UACPrompt
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute "%~s0", "%*", "", "runas", 1 >> "%temp%\getadmin.vbs"
    "%temp%\getadmin.vbs"
    exit /B
:gotAdmin
    if exist "%temp%\getadmin.vbs" ( del "%temp%\getadmin.vbs" )
    pushd "%CD%"
    CD /D "%~dp0"

:: 接收 Java 传过来的参数
:: %1 = 新包路径, %2 = 目标 app 目录, %3 = 原 Jar 文件名
set "NEW_FILE=%~1"
set "TARGET_DIR=%~2"
set "TARGET_NAME=%~3"

:: 1. 彻底杀死 Java 进程，确保文件锁被释放
taskkill /f /im javaw.exe /t >nul 2>&1
taskkill /f /im AttenDesktop* /t >nul 2>&1
timeout /t 2 /nobreak >nul

:: 2. 进入目标目录并强制删除旧 Jar
cd /d "!TARGET_DIR!"
if exist "!TARGET_NAME!" (
    del /f /q "!TARGET_NAME!"
)

:: 3. 覆盖文件：将 update_new.jar 变成 TARGET_NAME (如 atten_desktop-latest.jar)
copy /Y "!NEW_FILE!" "!TARGET_NAME!"

:: 4. 再次检查是否覆盖成功 (可选增强)
if not exist "!TARGET_NAME!" (
    echo 错误：文件覆盖失败！
    pause
    exit
)

:: 5. 清理临时文件
del /q "!NEW_FILE!"

:: 6. 返回根目录重启 EXE
cd /d ".."
for %%i in (AttenDesktop*.exe) do (
    start "" "%%i"
    goto EXIT_SCRIPT
)

:EXIT_SCRIPT
exit