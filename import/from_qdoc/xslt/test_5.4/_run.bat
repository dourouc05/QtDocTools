@echo off
setlocal enabledelayedexpansion

SET SAXON=F:/QtDoc/QtDoc/SaxonHE9-7-0-3J/saxon9he.jar
SET XSLTF=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/xslt/qdoc2db_5.4.xsl

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
                java -jar %SAXON% -s:!FILENAME!.xml -xsl:%XSLTF% -o:!FILENAME!.db
            )
        )
    )
)
echo Done