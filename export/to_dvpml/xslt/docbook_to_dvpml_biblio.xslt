<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  <xsl:variable name="biblioRefs" as="map(xs:string, xs:decimal)">
    <!-- Create a global map between bibliographic IDs and the numbers they are assigned to. These numbers are increasing in order of appearance in the text. This is not configurable. -->
    <xsl:variable name="uniqueRefs" select="distinct-values(//db:biblioref/@endterm)"/>
    
    <xsl:map>
      <xsl:for-each select="$uniqueRefs">
        <xsl:map-entry key="xs:string(.)" select="xs:decimal(position())"/>
      </xsl:for-each>
    </xsl:map>
  </xsl:variable>
  
  <xsl:template match="db:bibliography">
    <section noNumber="1" id="BIBLIOGRAPHY">
      <title>
        <xsl:choose>
          <xsl:when test="db:info/db:title"><xsl:value-of select="db:info/db:title"/></xsl:when>
          <xsl:when test="db:title"><xsl:value-of select="db:title"/></xsl:when>
          <xsl:otherwise>Bibliographie</xsl:otherwise>
        </xsl:choose>
      </title>
      <xsl:apply-templates mode="content_bibliography"/>
    </section>
  </xsl:template>
  
  <xsl:template match="db:bibliodiv" mode="content_bibliography">
    <section>
      <title>
        <xsl:choose>
          <xsl:when test="db:info/db:title"><xsl:value-of select="db:info/db:title"/></xsl:when>
          <xsl:when test="db:title"><xsl:value-of select="db:title"/></xsl:when>
        </xsl:choose>
      </title>
      <xsl:apply-templates mode="content_bibliography"/>
    </section>
  </xsl:template>
  
  <xsl:template match="db:bibliomixed" mode="content_bibliography">
    <signet id="{@xml:id}">[<xsl:value-of select="$biblioRefs(xs:string(@xml:id))"/>]</signet>
    <paragraph>
      <xsl:apply-templates mode="content_para"/>
    </paragraph>
  </xsl:template>
  
  <xsl:template match="db:biblioentry" mode="content_bibliography">
    <signet id="{@xml:id}">[<xsl:value-of select="$biblioRefs(xs:string(@xml:id))"/>]</signet>
    <!-- Basic formatting of the entry. -->
    <!-- If need be, implement something more intelligent, like https://github.com/docbook/xslTNG/blob/main/src/main/xslt/modules/bibliography.xsl. -->
    <paragraph>
      <xsl:apply-templates mode="content_bibliography_author"/>, 
      <xsl:apply-templates mode="content_bibliography_title"/>. 
    </paragraph>
  </xsl:template>
  
  <xsl:template match="db:authorgroup" mode="content_bibliography_author">
    <xsl:for-each select="db:author">
      <xsl:apply-templates mode="content_bibliography_author"/>
      <xsl:if test="position() != last()">
        <xsl:text>, </xsl:text>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  
  <xsl:template match="db:author" mode="content_bibliography_author">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template match="*" mode="content_bibliography_author">
    <!-- Ignore this. -->
  </xsl:template>
  
  <xsl:template match="db:title" mode="content_bibliography_title">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template match="*" mode="content_bibliography_title">
    <!-- Ignore this. -->
  </xsl:template>
</xsl:stylesheet>