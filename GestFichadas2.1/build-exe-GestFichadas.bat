@echo off
REM Siempre arrancar desde la carpeta donde está este .bat
cd /d "%~dp0"

REM Ruta al javaw del JDK 21 (sin consola)
set "JAVA_EXE=C:\Program Files\Java\jdk-21\bin\javaw.exe"

REM Si querés ver errores en consola, cambiá javaw.exe por java.exe
REM set "JAVA_EXE=C:\Program Files\Java\jdk-21\bin\java.exe"

echo Iniciando GestFichadas...
"%JAVA_EXE%" -jar "target\GestFichadas-1.0-jar-with-dependencies.jar"
