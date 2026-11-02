@echo off
call "%~dp0_RunOne.bat" TestTextScriptStress %*
exit /b %ERRORLEVEL%
