@echo off
REM ============================================================
REM  SmartWash-Android 模块脚手架（Windows）
REM  薄包装：调用同目录 scaffold-module.py
REM
REM  用法:
REM    scripts\scaffold-module.bat --type feature --name game
REM    scripts\scaffold-module.bat --type feature --name order --split
REM    scripts\scaffold-module.bat --type common --name util --with-compose
REM    scripts\scaffold-module.bat --type feature --name game --delete --yes
REM    scripts\scaffold-module.bat --type feature --name demo --dry-run
REM ============================================================
setlocal
set "DIR=%~dp0"

where python >nul 2>nul
if %errorlevel%==0 (
    set "PY=python"
) else (
    where py >nul 2>nul
    if %errorlevel%==0 (set "PY=py") else (echo [ERROR] 未找到 python/py，请先安装 Python 3 & exit /b 1)
)

%PY% "%DIR%scaffold-module.py" %*
endlocal
