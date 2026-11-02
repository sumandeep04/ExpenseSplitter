@echo off
title Expense Splitter - Running Automated Tests
cd /d "%~dp0"
echo ===================================================
echo   Running All Automated Tests (JUnit 5)
echo ===================================================
echo.
call mvn clean test
echo.
echo ===================================================
echo   Testing Completed. Press any key to close.
echo ===================================================
pause
