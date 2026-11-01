@echo off
call "%~dp0_RunOne.bat" TestDBSync %*
exit /b %ERRORLEVEL%