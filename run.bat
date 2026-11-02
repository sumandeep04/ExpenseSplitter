@echo off
title Expense Splitter - Web Application
cd /d "%~dp0"
echo ===================================================
echo   Starting Expense Splitter at http://localhost:8080
echo ===================================================
echo.
call mvn spring-boot:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited with error code %ERRORLEVEL%.
    pause
)
