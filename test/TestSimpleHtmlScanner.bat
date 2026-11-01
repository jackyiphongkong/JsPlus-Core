@echo off
call "%~dp0_RunOne.bat" TestSimpleHtmlScanner %*
exit /b %ERRORLEVEL%
