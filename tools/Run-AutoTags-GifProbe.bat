@echo off
setlocal

set "APP_DIR=%~dp0.."
set "JAVA_EXE=java"
set "JAVAC_EXE=javac"
set "NATIVE_DIR=%APP_DIR%\vendor\native\opencv"
set "CLASSES_DIR=%APP_DIR%\tools\classes"
set "GIF_ARGS=--generate 640 480"
set "APP_CLASSES=%APP_DIR%\build\classes\java\main"
set "RUNTIME_LIBS=%APP_DIR%\vendor\runtime-libs\*"

if exist "C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\java.exe"
    set "JAVAC_EXE=C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\javac.exe"
)

if not "%~1"=="" (
    set "GIF_ARGS=%*"
)

if not exist "%APP_CLASSES%" (
    echo Nao encontrei "%APP_CLASSES%".
    echo Rode primeiro: gradlew.bat compileJava
    exit /b 1
)

if not exist "%CLASSES_DIR%" mkdir "%CLASSES_DIR%"

"%JAVAC_EXE%" ^
  --module-path "C:\Program Files\BellSoft\LibericaJDK-25-Full\jmods" ^
  --add-modules javafx.graphics ^
  -cp "%APP_CLASSES%;%RUNTIME_LIBS%" ^
  -d "%CLASSES_DIR%" ^
  "%APP_DIR%\tools\AutoTagsGifProbe.java" || exit /b 1

"%JAVA_EXE%" ^
  --enable-native-access=ALL-UNNAMED ^
  --enable-native-access=javafx.graphics ^
  -Djava.library.path="%NATIVE_DIR%" ^
  --module-path "C:\Program Files\BellSoft\LibericaJDK-25-Full\jmods" ^
  --add-modules javafx.graphics ^
  -cp "%CLASSES_DIR%;%APP_CLASSES%;%RUNTIME_LIBS%" ^
  AutoTagsGifProbe %GIF_ARGS%
