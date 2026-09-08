@echo off
chcp 65001
cls
cd /d %~dp0
echo 请使用对应脚本启动服务：
echo   start_detect.bat   YOLO 检测  :5001
echo   start_unet.bat     UNet 分割  :3408
echo   start_mesh.bat     Mesh 重建  :5002
pause
