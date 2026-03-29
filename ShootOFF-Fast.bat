@echo off
setlocal

cd /d "%~dp0"

echo [ShootOFF] Preparando o app sem rodar testes (Fast Mode)...
call "%~dp0gradlew.bat" stageModernRuntime -x test --daemon
if errorlevel 1 (
  echo.
  echo [ShootOFF] Falha ao preparar o runtime. 
  echo Se o app estiver aberto, feche-o e tente novamente.
  echo.
  pause
  exit /b 1
)

echo [ShootOFF] Iniciando o aplicativo...
call "%~dp0build\modern-runtime\Run-ShootOFF.bat" %*
exit /b %ERRORLEVEL%
