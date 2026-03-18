@echo off
setlocal

cd /d "%~dp0"

if not exist "%~dp0gradlew.bat" (
  echo gradlew.bat nao foi encontrado nesta pasta.
  echo.
  pause
  exit /b 1
)

if "%~1"=="" (
  echo ShootOFF - helper do Gradle
  echo.
  echo Exemplos:
  echo   Run-Gradle.bat tasks
  echo   Run-Gradle.bat build
  echo   Run-Gradle.bat test
  echo   Run-Gradle.bat --no-daemon tasks
  echo.
  echo Nenhuma task informada. Executando: --no-daemon tasks
  echo.
  call "%~dp0gradlew.bat" --no-daemon tasks
) else (
  call "%~dp0gradlew.bat" %*
)

set "EXIT_CODE=%ERRORLEVEL%"
echo.
pause
exit /b %EXIT_CODE%
