@echo off
call "%~dp0_RunOne.bat" TestResultMap %*
exit /b %ERRORLEVEL%