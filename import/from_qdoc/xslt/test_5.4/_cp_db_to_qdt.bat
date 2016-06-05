@echo off
setlocal enabledelayedexpansion

SET EXT_DB=db
SET EXT_QDT=qdt

echo Starting
for %%f in (*.db) do (
    set FILENAME=%%~nf
    copy !FILENAME!.%EXT_DB% !FILENAME!.%EXT_QDT%
)
echo Done

pause