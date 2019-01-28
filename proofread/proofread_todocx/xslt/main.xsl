<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xfc="http://www.xmlmind.com/foconverter/xsl/extensions"
    xmlns:t="http://docbook.org/xslt/ns/template"
    xmlns:f="http://docbook.org/xslt/ns/extension"
    xmlns:m="http://docbook.org/xslt/ns/mode"
    xmlns:db="http://docbook.org/ns/docbook"
    exclude-result-prefixes="xs"
    version="3.0">
    
    <xsl:import href="docbook_xsl2/fo/final-pass.xsl"/>
    
    <xsl:attribute-set name="monospace.verbatim.properties" use-attribute-sets="verbatim.properties monospace.properties">
        <xsl:attribute name="text-align">start</xsl:attribute>
        <xsl:attribute name="wrap-option">no-wrap</xsl:attribute>
        <xsl:attribute name="xfc:user-style">ProgramListing</xsl:attribute>
    </xsl:attribute-set>
    
    <!-- For sections: titlepage-mode.xsl -->
    <xsl:template match="db:title" mode="m:titlepage-mode">
        <xsl:variable name="xfcStyle">
            <xsl:variable name="isSect" select="self::sect1 or self::sect2 or self::sect3 or self::sect4 or self::sect5 or self::sect6"/>
            <xsl:choose>
                <xsl:when test="count(ancestor::db:section) = 0 and not($isSect)"><xsl:value-of select="'Title'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 1 and not($isSect)) or self::sect1"><xsl:value-of select="'Heading 1'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 2 and not($isSect)) or self::sect2"><xsl:value-of select="'Heading 2'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 3 and not($isSect)) or self::sect3"><xsl:value-of select="'Heading 3'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 4 and not($isSect)) or self::sect4"><xsl:value-of select="'Heading 4'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 5 and not($isSect)) or self::sect5"><xsl:value-of select="'Heading 5'"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="'Heading 6'"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <fo:block font-family="{$title.fontset}" xfc:user-style="{$xfcStyle}">
            <!-- The rest comes from the original style. -->
            <xsl:choose>
                <xsl:when test="not(parent::*)">
                    <xsl:apply-templates mode="m:titlepage-mode"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:variable name="context"
                        select="if (parent::db:info) then parent::db:info/parent::* else parent::*"/>
                    
                    <xsl:apply-templates select="$context" mode="m:object-title-markup">
                        <xsl:with-param name="allow-anchors" select="true()"/>
                    </xsl:apply-templates>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>
</xsl:stylesheet>