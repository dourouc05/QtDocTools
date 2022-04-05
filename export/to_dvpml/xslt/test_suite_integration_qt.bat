@echo off

rem set SAXON=C:\Program Files\Oxygen XML Editor 23\frameworks\dita\DITA-OT3.x\lib\Saxon-HE-9.9.1-7.jar
set SAXON=saxon-he-11.3.jar

for %%f in (test\*.qdt) do (
    echo %%~nf
    java -jar "%SAXON%" "doc-qt=true" "document-file-name=%%~nf" "section=65" -s:test\%%~nf.qdt -xsl:docbook_to_dvpml.xslt
)

pause
