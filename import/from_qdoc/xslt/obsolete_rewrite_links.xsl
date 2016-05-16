<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:html="http://www.w3.org/1999/xhtml"
    exclude-result-prefixes="xs"
    version="2.0">
    
    <!-- Handles the rewriting part of obsolete files, as they may contain duplicate identifiers. -->
    
    <xsl:template mode="obsolete_rewrite" match="html:h3[@class = 'fn']" priority="1.5">
        <html:h3>
            <xsl:attribute name="class" select="@class"/>
            <xsl:attribute name="id" select="concat('obsolete-', @id)"/>
            <xsl:apply-templates mode="obsolete_rewrite_title" />
        </html:h3>
    </xsl:template>
    <xsl:template mode="obsolete_rewrite_title" match="html:a[@name][not(@href)]" priority="1.5">
        <html:a>
            <xsl:attribute name="name" select="concat('obsolete-', @name)"/>
        </html:a>
    </xsl:template>
    <xsl:template mode="obsolete_rewrite_title" match="@*|node()" priority="1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="obsolete_rewrite_title"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template mode="obsolete_rewrite" match="@*|node()" priority="1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="obsolete_rewrite"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>