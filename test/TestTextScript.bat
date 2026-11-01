@echo off
call "%~dp0_RunOne.bat" TestTextScript %*
exit /b %ERRORLEVEL%