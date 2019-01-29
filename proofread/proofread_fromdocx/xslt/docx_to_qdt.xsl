<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" 
    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" 
    xmlns:p="http://schemas.openxmlformats.org/drawingml/2006/picture" 
    xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" 
    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" 
    xmlns:v="urn:schemas-microsoft-com:vml"
    xmlns:db="http://docbook.org/ns/docbook"
    exclude-result-prefixes="xs w a p wp r v"
    version="2.0">
    
    <xsl:output method="xml" indent="yes"/>
    
    <xsl:template match="w:document">
        <db:article>
            <db:title><xsl:value-of select="w:body/w:p[1]/w:r/w:t"/></db:title>
            
            <xsl:apply-templates/>
        </db:article>
    </xsl:template>
    
    <xsl:template match="w:body">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="w:p">
        <xsl:variable name="style" as="xs:string">
            <xsl:variable name="tentative" select="w:pPr/w:pStyle/@w:val" as="xs:string?"/>
            <xsl:value-of select="if ($tentative) then $tentative else 'Normal'"/>
        </xsl:variable>
        
        <xsl:choose>
            <xsl:when test="$style = 'XFC_P_Title'"/> <!-- Title output in <db:info> -->
            <xsl:when test="starts-with($style, 'XFC_P_Heading20')">
                <
            </xsl:when>
            <xsl:when test="$style = 'Normal'">
                <db:para>
                    <xsl:for-each select="w:r">
                        <xsl:for-each select="w:t">
                            <xsl:apply-templates/>
                        </xsl:for-each>
                    </xsl:for-each>
                </db:para>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message>WARNING: Paragraph style not handled: <xsl:value-of select="$style"/></xsl:message>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>