@echo off
chcp 65001
cls
cd /d %~dp0

echo ========================================
echo   Mesh 3D 重建服务
echo ========================================
echo 服务地址: http://localhost:5002
echo.

if exist "venv\Scripts\activate.bat" (
    call venv\Scripts\activate.bat
)

python mesh-api.py
pause
