@echo off
call "%~dp0_RunOne.bat" TestLogger %*
exit /b %ERRORLEVEL%
