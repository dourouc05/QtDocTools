<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    xmlns="http://docbook.org/ns/docbook"
    xmlns:db="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs xsl"
    version="2.0">
    
    <xsl:param name="originalDocument" as="xs:string" select="'../tests/CPLEX_before.xml'"/>
    <xsl:variable name="left" as="node()" select="doc($originalDocument)"/>
    
    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="db:screen db:literallayout db:programlisting db:address"/>
    
    <xsl:template match="@*|node()" mode="#all" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- If there is some metadata in the original document, copy it. -->
    <!-- Metadata only appears for classes, functions, etc., so the sections have very predictable names -->
    <!-- (should not be affected by proofreading). -->
    <xsl:template match="db:section">
        <section>
            <xsl:if test="$left//db:section/db:title[text() = ./db:title/text()]">
                <xsl:variable name="match" select="$left//db:section[db:title[text() = ./db:title/text()]]"/>
                
                <xsl:if test="$match/db:title/following-sibling::*/self::db:fieldsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:fieldsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:methodsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:methodsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:constructorsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:constructorsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:destructorsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:destructorsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:enumsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:enumsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:typedefsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:typedefsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:classsynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:classsynopsis"/>
                </xsl:if>
                <xsl:if test="$match/db:title/following-sibling::*/self::db:namespacesynopsis">
                    <xsl:copy-of select="$match/db:title/following-sibling::db:namespacesynopsis"/>
                </xsl:if>
            </xsl:if>
            
            <xsl:apply-templates/>
        </section>
    </xsl:template>
    
    <!-- The only other place where some metadata might be found: just after the root's info. -->
    <xsl:template match="db:info[ancestor::*[1]/self::db:article]">
        <info>
            <xsl:apply-templates/>
        </info>
        
        <xsl:if test="$left/db:article/db:info/following-sibling::*/self::db:classsynopsis">
            <xsl:copy-of select="$left/db:article/db:info/following-sibling::db:classsynopsis"/>
        </xsl:if>
        <xsl:if test="$left/db:article/db:info/following-sibling::*/self::db:namespacesynopsis">
            <xsl:copy-of select="$left/db:article/db:info/following-sibling::db:namespacesynopsis"/>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>