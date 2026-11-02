@echo off
title Expense Splitter - Packaging Fat JAR
cd /d "%~dp0"
echo ===================================================
echo   Packaging Spring Boot Web Application
echo ===================================================
echo.
call mvn clean package
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo   BUILD SUCCESS!
    echo   Runnable JAR generated at:
    echo   target\expense-splitter-1.0.0.jar
    echo ===================================================
) else (
    echo.
    echo Build failed.
)
pause
