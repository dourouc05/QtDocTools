<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    xmlns="http://docbook.org/ns/docbook"
    xmlns:db="http://docbook.org/ns/docbook"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:tc="http://tcuvelier.be"
    exclude-result-prefixes="xs db tc"
    version="2.0">
    
    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>
    
    <!-- This style sheet could not be merged into custom_docbook5, as this one is executed within w2x and must use XSLT 1. Most processing is done there, though. -->
    
    <!-- Apply the //<QDOCTOOLS> comments. They indicate a programming language, but may also show that two code blocks were merged together. -->
    <xsl:template match="db:programlisting">
        <xsl:variable name="intermediate">
            <xsl:apply-templates mode="split_pre_phase_1"/>
        </xsl:variable>
        
        <xsl:for-each-group select="$intermediate/child::node()" group-starting-with="tc:annotation">
            <programlisting>
                <xsl:if test="contains(current-group()[1]/@value, ':')">
                    <xsl:attribute name="language" select="normalize-space(tokenize(current-group()[1]/@value, ':')[2])"></xsl:attribute>
                </xsl:if>
                
                <xsl:apply-templates select="current-group()" mode="split_pre_phase_2"/>
            </programlisting>
        </xsl:for-each-group>
    </xsl:template>
    
    <xsl:template match="@*|node()" mode="#all" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="text()" mode="split_pre_phase_1">
        <xsl:analyze-string select="." regex="//.QDOCTOOLS.(.*)\n">
            <xsl:matching-substring>
                <tc:annotation value="{regex-group(1)}"/>
            </xsl:matching-substring>
            <xsl:non-matching-substring>
                <xsl:value-of select="."/>
            </xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:template>
    
    <xsl:template match="tc:annotation" mode="split_pre_phase_2"/>
</xsl:stylesheet>