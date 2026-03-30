@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "SERVICE_NAME=spring-mcp-1c-copilot"
set "DEFAULT_ACTION=up"
set "ACTION=%~1"

if "%ACTION%"=="" set "ACTION=%DEFAULT_ACTION%"

if /I "%ACTION%"=="help" goto :help
if /I "%ACTION%"=="build" goto :build
if /I "%ACTION%"=="up" goto :up
if /I "%ACTION%"=="down" goto :down
if /I "%ACTION%"=="logs" goto :logs
if /I "%ACTION%"=="status" goto :status

echo [ERROR] Unknown action: %ACTION%
echo.
goto :help

:checks
where docker >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Docker is not installed or not available in PATH.
  exit /b 1
)

if not exist ".env" (
  echo [ERROR] File .env not found.
  echo Create it first and fill in ONEC_AI_TOKEN or ONEC_AI_TOKEN_FILE.
  exit /b 1
)

if not exist ".secrets\onec_ai_token" (
  echo [ERROR] Secret file .secrets\onec_ai_token not found.
  echo Create it before running Docker. The service expects this file to exist.
  exit /b 1
)

exit /b 0

:build
call :checks
if errorlevel 1 exit /b 1

echo [INFO] Building Docker image...
docker compose build
if errorlevel 1 (
  echo [ERROR] Docker image build failed.
  exit /b 1
)

echo [OK] Docker image built successfully.
exit /b 0

:up
call :checks
if errorlevel 1 exit /b 1

echo [INFO] Building image and starting container...
docker compose up -d --build
if errorlevel 1 (
  echo [ERROR] Docker compose up failed.
  exit /b 1
)

echo [OK] Service started.
echo [INFO] MCP endpoint: http://localhost:8186/mcp
echo [INFO] Health check: http://localhost:8186/api/health
exit /b 0

:down
echo [INFO] Stopping container...
docker compose down
if errorlevel 1 (
  echo [ERROR] Docker compose down failed.
  exit /b 1
)

echo [OK] Service stopped.
exit /b 0

:logs
echo [INFO] Showing container logs...
docker compose logs -f %SERVICE_NAME%
exit /b %errorlevel%

:status
echo [INFO] Container status:
docker compose ps
exit /b %errorlevel%

:help
echo Usage:
echo   docker-build-run.cmd [action]
echo.
echo Actions:
echo   build   Build Docker image only
echo   up      Build and start container ^(default^)
echo   down    Stop and remove container
echo   logs    Follow service logs
echo   status  Show container status
echo   help    Show this help
echo.
echo Examples:
echo   docker-build-run.cmd
echo   docker-build-run.cmd build
echo   docker-build-run.cmd up
echo   docker-build-run.cmd logs
exit /b 0
