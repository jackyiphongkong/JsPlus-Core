@echo off
call "%~dp0_RunOne.bat" TestIdentityCard %*
exit /b %ERRORLEVEL%