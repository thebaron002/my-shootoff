@echo off
setlocal

cd /d "%~dp0"

call "%~dp0gradlew.bat" --no-daemon stageModernRuntime
if errorlevel 1 (
  echo.
  echo Falha ao preparar o runtime moderno da source.
  echo Feche qualquer instancia aberta do ShootOFF e tente novamente.
  echo.
  pause
  exit /b 1
)

call "%~dp0build\modern-runtime\Run-ShootOFF.bat" %*
exit /b %ERRORLEVEL%
