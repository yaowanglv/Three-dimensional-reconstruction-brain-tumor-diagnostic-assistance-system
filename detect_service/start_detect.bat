@echo off
chcp 65001
cls
cd /d %~dp0

echo ========================================
echo   YOLO 脑肿瘤检测服务
echo ========================================
echo 服务地址: http://localhost:5001
echo.

if exist "venv\Scripts\activate.bat" (
    call venv\Scripts\activate.bat
)

python detect-api.py
pause
