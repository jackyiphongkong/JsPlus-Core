@echo off
call "%~dp0_RunOne.bat" TestRemoteFile %*
exit /b %ERRORLEVEL%