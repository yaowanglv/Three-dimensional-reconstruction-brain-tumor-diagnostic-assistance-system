@echo off
chcp 65001
cls
cd /d %~dp0

echo 激活 Conda 环境后请分别启动三个服务。
where conda >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Conda
    pause
    exit /b 1
)

call conda activate rtdetr-detect
if errorlevel 1 (
    echo [提示] 请先: conda create -n rtdetr-detect python=3.10
    echo        conda activate rtdetr-detect
    echo        pip install -r requirements.txt
    pause
    exit /b 1
)

echo 当前环境已激活。请另开终端运行 start_detect.bat / start_unet.bat / start_mesh.bat
pause
