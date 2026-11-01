@echo off
call "%~dp0_RunOne.bat" TestSimpleJsCompressor %*
exit /b %ERRORLEVEL%