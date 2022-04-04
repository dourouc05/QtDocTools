@echo off

set SAXON=C:\Program Files\Oxygen XML Editor 23\frameworks\dita\DITA-OT3.x\lib\Saxon-HE-9.9.1-7.jar

for %%f in (test\*.qdt) do (
    echo %%~nf
    java -jar "%SAXON%" "doc-qt=true()" "document-file-name=%%~nf" -s:test\%%~nf.qdt -xsl:docbook_to_dvpml.xslt
)

pause
