@echo off
call "%~dp0_RunOne.bat" TestCrud %*
exit /b %ERRORLEVEL%