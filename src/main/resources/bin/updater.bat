@echo off
setlocal enabledelayedexpansion

:: 1. 自动请求管理员权限 (静默跳转)
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    :: 这里的 0 表示隐藏新开启的管理员窗口
    echo UAC.ShellExecute "cmd.exe", "/c %~s0 ""%~1"" ""%~2"" ""%~3""", "", "runas", 0 >> "%temp%\getadmin.vbs"
    "%temp%\getadmin.vbs"
    del /f /q "%temp%\getadmin.vbs" >nul 2>&1
    exit /b
)

:: --- 管理员静默区域 ---
:: 切换编码以支持中文路径处理
chcp 936 >nul

set "SRC=%~1"
set "DEST_DIR=%~2"
set "DEST_NAME=%~3"

:: 2. 强制结束进程
taskkill /f /im javaw.exe /t >nul 2>&1
taskkill /f /im AttenDesktop.exe /t >nul 2>&1
:: 留出 2 秒等待句柄释放
timeout /t 2 /nobreak >nul

:: 3. 执行物理替换
if exist "!SRC!" (
    copy /y "!SRC!" "!DEST_DIR!\!DEST_NAME!" >nul 2>&1
    if !errorlevel! equ 0 (
        :: 替换成功后清理临时包
        del /f /q "!SRC!" >nul 2>&1
    )
)

:: 4. 自动重启程序
if exist "!DEST_DIR!\!DEST_NAME!" (
    cd /d "!DEST_DIR!"
    cd ..
    for %%i in (AttenDesktop*.exe) do (
        start "" "%%i"
        exit
    )
)
exit