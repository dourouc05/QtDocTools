@echo off
setlocal enabledelayedexpansion

SET SAXON=F:/QtDoc/QtDoc/SaxonHE9-7-0-3J/saxon9he.jar
SET JING=F:/QtDoc/QtDoc/jing-20140903-saxon95.jar

SET XSLT=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/xslt/qdoc2db_5.4.xsl
SET RNG=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/schemas/docbook51/api.rnc

echo Starting
for %%f in (*.xml) do (
    set FILENAME=%%~nf
    
    echo !FILENAME! | findstr /C:-compat >nul
    set ISCOMPAT=!ERRORLEVEL!
    echo !FILENAME! | findstr /C:-members >nul
    set ISMEMBER=!ERRORLEVEL!
    echo !FILENAME! | findstr /C:-obsolete >nul
    set ISOBSOLETE=!ERRORLEVEL!
    
    IF /I "!ISCOMPAT!" EQU "1" (
        IF /I "!ISMEMBER!" EQU "1" (
            IF /I "!ISOBSOLETE!" EQU "1" (
                echo -- !FILENAME!
                java -jar %SAXON% -s:!FILENAME!.xml -xsl:%XSLT% -o:!FILENAME!.db
                java -jar %JING% -c %RNG% !FILENAME!.db
            )
        )
    )
)
echo Done