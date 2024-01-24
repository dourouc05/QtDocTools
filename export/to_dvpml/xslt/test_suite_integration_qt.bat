@echo off

set SAXON=saxon-he-11.3.jar

for %%f in (test\*.qdt) do (
    echo %%~nf
    java -jar "%SAXON%" "doc-qt=true" "document-file-name=%%~nf" "section=65" -s:test\%%~nf.qdt -xsl:docbook_to_dvpml.xslt
    rem exit /B
    echo.
)

pause
