@echo off
rem Puts readblob on your user PATH. "install.cmd -Uninstall" removes it. See install.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
