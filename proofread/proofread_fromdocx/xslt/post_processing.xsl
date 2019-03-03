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
    <!-- Some transformations could also be performed in custom_docbook5, but are much simpler to express as operations on DocBook content. -->
    
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
    
    <!-- The abstract, if there is one, is in a table after <info>. It must be brought back into <info>. -->
    <xsl:template match="db:info">
        <info>
            <xsl:copy-of select="./*"/>
            
            <xsl:if test="following-sibling::*[1]/self::db:informaltable and following-sibling::db:informaltable[1]/db:tbody/db:tr/db:td/child::*[1]/self::db:para 
                          and normalize-space(following-sibling::db:informaltable[1]/db:tbody/db:tr/db:td/child::db:para[1]/text()) = 'Abstract'">
                <abstract>
                    <xsl:apply-templates select="following-sibling::db:informaltable[1]/db:tbody/db:tr/db:td/db:para[1]/following-sibling::node()">
                        <xsl:with-param name="wrapWithPara" select="true()"/>
                    </xsl:apply-templates>
                </abstract>
            </xsl:if>
        </info>
    </xsl:template>
    
    <xsl:template match="db:informaltable[preceding-sibling::*[1]/self::db:info and db:tbody/db:tr/db:td/child::*[1]/self::db:para and normalize-space(db:tbody/db:tr/db:td/db:para[1]/text()) = 'Abstract']" priority="42"/>
    
    <!-- Informal tables that only have one column (for all their rows) are converted to simplelists. -->
    <xsl:template match="db:informaltable[not(/db:tbody/db:tr/count(db:td) > 1)]">
        <xsl:param name="wrapWithPara" as="xs:boolean" select="false()"/>
        
        <xsl:variable name="simplelist">
            <simplelist>
                <xsl:for-each select="db:tbody/db:tr/db:td">
                    <member>
                        <xsl:apply-templates></xsl:apply-templates>
                    </member>
                </xsl:for-each>
            </simplelist>
        </xsl:variable>
        
        <!-- In the abstract, this list must be within a paragraph. -->
        <xsl:choose>
            <xsl:when test="$wrapWithPara">
                <para>
                    <xsl:copy-of select="$simplelist"/>
                </para>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy-of select="$simplelist"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>