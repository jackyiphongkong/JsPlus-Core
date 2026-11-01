@echo off
call "%~dp0_RunOne.bat" TestDiskFile %*
exit /b %ERRORLEVEL%