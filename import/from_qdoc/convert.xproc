<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" xmlns:c="http://www.w3.org/ns/xproc-step" version="1.0">
    <p:input port="source">
        <p:document href="preprocessor/qwidget.db"/>
    </p:input>
    <p:output port="result">
        <p:pipe port="result" step="final"/>
    </p:output>
    
    <!-- First import the input as HTML data, make it XML. -->
    <p:exec>
        <p:input port="source"/>
        <p:with-option name="command" select="'python'"/>
        <p:with-option name="args" select="'./html2xml/html2xml_stdin.py'"/>
        <p:with-option name="source-is-xml" select="false()"/>
        <p:with-option name="result-is-xml" select="true()"/>
    </p:exec>
    
    <!-- Then perform an XSLT step. -->
    <p:xslt>
        <p:input port="stylesheet">
            <p:document href="xslt/qdoc2db_5.4.xsl"/>
        </p:input>
        <p:input port="parameters">
            <p:empty/>
        </p:input>
    </p:xslt>
    
    <!-- Finally, deal with the function prototypes as postprocessing step. -->
    <p:exec>
        <p:with-option name="command" select="'python'"/>
        <p:with-option name="args" select="'./html2xml/html2xml_stdin.py'"/>
        <p:with-option name="source-is-xml" select="false()"/>
        <p:with-option name="result-is-xml" select="true()"/>
    </p:exec>
</p:declare-step>