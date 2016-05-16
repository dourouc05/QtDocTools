@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

SET SAXON=F:/QtDoc/QtDoc/SaxonHE9-7-0-3J/saxon9he.jar
SET XSLTF=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/xslt/qdoc2db_5.4.xsl

echo Starting!
for %%f in (*.xml) do (
    set FILENAME=%%~nf
    echo -- !FILENAME!
    java -jar %SAXON% -s:!FILENAME!.xml -xsl:%XSLTF% -o:!FILENAME!.db
)
for %%f in (*.xml) do (
    set FILENAME=%%~nf
    if exist !FILENAME!-compat.db   del !FILENAME!-compat.db
    if exist !FILENAME!-members.db  del !FILENAME!-members.db
    if exist !FILENAME!-obsolete.db del !FILENAME!-obsolete.db
)
echo Done!