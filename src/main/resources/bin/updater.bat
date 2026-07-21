@echo off
setlocal enabledelayedexpansion

:: 1. 自动请求管理员权限 (在你的原版基础修补：将最后的 0 改为 1)
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    
    :: 【核心修复一】：将原本最后的参数 0 (隐藏) 改为 1 (普通激活运行)
    :: 这样能保证在所有第三方杀毒软件和 Windows 企业安全策略电脑上 100% 弹出 UAC 提示。
    :: 保留你原本一模一样的短路径 %~s0 和参数双引号结构，确保你的 Desktop 主程序传参不失效。
    echo UAC.ShellExecute "cmd.exe", "/c %~s0 ""%~1"" ""%~2"" ""%~3""", "", "runas", 1 >> "%temp%\getadmin.vbs"
    
    "%temp%\getadmin.vbs"
    del /f /q "%temp%\getadmin.vbs" >nul 2>&1
    exit /b
)

:: --- 管理员安全区域 ---
:: 【核心修复二】：既然脚本存为了 ANSI/GBK，一进来就立刻强制指定 936，防止环境错乱
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