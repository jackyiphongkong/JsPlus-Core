@echo off
call "%~dp0_RunOne.bat" TestEvalJava %*
exit /b %ERRORLEVEL%