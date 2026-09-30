@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat runUi
exit /b %errorlevel%
