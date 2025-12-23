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

:: 1. 循环等待主进程完全退出，防止文件被占用无法覆盖
:WAIT_LOOP
:: 使用通配符 AttenDesktop* 来匹配所有可能的版本号进程
tasklist /FI "IMAGENAME eq AttenDesktop*" 2>NUL | find /I /N "AttenDesktop">NUL
if "%ERRORLEVEL%"=="0" (
    timeout /t 1 /nobreak >nul
    goto WAIT_LOOP
)

:: 2. 执行覆盖（双引号是处理空格的关键）
:: /Y 表示不提示直接覆盖
copy /Y "!NEW_FILE!" "!TARGET_DIR!\!TARGET_NAME!"

:: 3. 清理临时文件
del /Q "!NEW_FILE!"

:: 4. 重新启动程序
cd /d "%~dp0"

:: 方案 1：如果你决定按方案 A 锁定名称，保留这一行即可
:: start "" "AttenDesktop.exe"

:: 方案 2：模糊匹配启动（寻找当前目录下任何以 AttenDesktop 开头的 exe）
for %%i in (AttenDesktop*.exe) do (
    start "" "%%i"
    goto :EXIT_SCRIPT
)

:EXIT_SCRIPT
exit