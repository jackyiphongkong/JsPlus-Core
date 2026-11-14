@echo off
call "%~dp0_RunOne.bat" TestSimpleHtmlCompressor %*
exit /b %ERRORLEVEL%