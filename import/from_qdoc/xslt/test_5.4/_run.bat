@echo off
setlocal enabledelayedexpansion

SET SAXON=F:/QtDoc/QtDoc/SaxonHE9-7-0-3J/saxon9he.jar
SET JING=F:/QtDoc/QtDoc/jing-20140903-saxon95.jar

SET XSLT=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/xslt/qdoc2db_5.4.xsl
SET RNG_DB=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/schemas/docbook51/docbookxi.rnc
SET RNG_QDT=F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/schemas/docbook51/custom.rnc

SET EXT_DB=db
SET EXT_QDT=qdt

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
                
                rem Process for each output: first generate the resulting XML. Then, either Saxon successes and outputs the requested XML file, 
                rem or it does not and shows a lengthy error. No need to duplicate an error for something that does not exist, hence the check. 
                
                java -jar %SAXON% -s:!FILENAME!.xml -xsl:%XSLT% -o:!FILENAME!.%EXT_DB% vocabulary=docbook
                if exist !FILENAME!.%EXT_DB% (
                    java -jar %JING% -c %RNG_DB% !FILENAME!.%EXT_DB%
                )
                
                java -jar %SAXON% -s:!FILENAME!.xml -xsl:%XSLT% -o:!FILENAME!.%EXT_QDT% vocabulary=qtdoctools
                if exist !FILENAME!.%EXT_QDT% (
                    java -jar %JING% -c %RNG_QDT% !FILENAME!.%EXT_QDT%
                )
            )
        )
    )
)
echo Done

pause