@echo off
chcp 65001
cls
cd /d %~dp0

echo ========================================
echo   UNet 分割 / 三维重建推理服务
echo ========================================
echo 服务地址: http://localhost:3408
echo.

if exist "venv\Scripts\activate.bat" (
    call venv\Scripts\activate.bat
)

python unet4engine.py --host 0.0.0.0 --port 3408
pause
