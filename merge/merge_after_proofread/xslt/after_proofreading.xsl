<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    xmlns:db="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs xsl"
    version="2.0">
    
    <xsl:param name="originalDocument" as="xs:string" select="'../tests/qmodbusrequest_before.xml'"/>
    <xsl:variable name="left" as="node()" select="doc($originalDocument)"/>
    
    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="db:screen db:literallayout db:programlisting db:address db:code db:link"/>
    
    <xsl:template match="@*|node()" mode="#all" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- If there is some metadata in the original document, copy it. -->
    <!-- Metadata only appears for classes, functions, etc., so the sections have very predictable names -->
    <!-- (should not be affected by proofreading). -->
    <xsl:template match="db:section">
        <db:section>
            <xsl:if test="@xml:id">
                <xsl:attribute name="xml:id" select="@xml:id"/>
            </xsl:if>
            
            <xsl:variable name="currentSectionId" select="concat(count(ancestor::*), '_', count(preceding::db:section))"/>
            <xsl:if test="$left//db:section[concat(count(ancestor::*), '_', count(preceding::db:section)) = $currentSectionId]"> 
                <xsl:variable name="match" select="$left//db:section[concat(count(ancestor::*), '_', count(preceding::db:section)) = $currentSectionId]"/>
                
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
        </db:section>
    </xsl:template>
    
    <xsl:template match="db:bridgehead[some $tag in $left//db:section satisfies @xml:id = $tag/@xml:id]">
        <xsl:message>WARNING: A section in the original file has seemingly been converted into a bridgehead!</xsl:message>
    </xsl:template>
    
    <!-- The only other place where some metadata might be found: just after the root's info. -->
    <xsl:template match="db:info[ancestor::*[1]/self::db:article]">
        <db:info>
            <!-- From the modified file, probably just a title and an abstract, but not the rest. -->
            <xsl:apply-templates/>
            
            <!-- So take back everything else from $left. -->
            <xsl:variable name="tagsAtRight" select="./*/local-name()"/>
            <xsl:for-each select="$left/db:article/db:info/*">
                <xsl:if test="not(some $tag in $tagsAtRight satisfies local-name() = $tag)">
                    <xsl:copy-of select="."/>
                </xsl:if>
            </xsl:for-each>
        </db:info>
        
        <xsl:if test="$left/db:article/db:info/following-sibling::*/self::db:classsynopsis">
            <xsl:copy-of select="$left/db:article/db:info/following-sibling::db:classsynopsis"/>
        </xsl:if>
        <xsl:if test="$left/db:article/db:info/following-sibling::*/self::db:namespacesynopsis">
            <xsl:copy-of select="$left/db:article/db:info/following-sibling::db:namespacesynopsis"/>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>