@echo off
call "%~dp0_RunOne.bat" TestJson %*
exit /b %ERRORLEVEL%
