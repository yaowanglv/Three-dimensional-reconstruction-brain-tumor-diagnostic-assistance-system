@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist ".env.mcp" (
    echo 请先复制 .env.mcp.example 为 .env.mcp 并填写密码
    pause
    exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in (".env.mcp") do (
    if not "%%A"=="" if not "%%A:~0,1%"=="#" set "%%A=%%B"
)

if not exist "node_modules" (
    echo 正在 npm install ...
    call npm install
)

echo 启动 MySQL MCP, 库: %MYSQL_DB%
".\node_modules\.bin\mcp-server-mysql.cmd"
pause
