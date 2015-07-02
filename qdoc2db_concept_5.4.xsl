<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" exclude-result-prefixes="xsl xs html" version="2.0">
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- Output document class. -->
  <xsl:template match="html:html">
    <xsl:variable name="content" select=".//html:div[@class = 'content mainContent']"/>

    <db:article>
      <db:info>
        <db:title>
          <xsl:value-of select="$content/html:h1/text()"/>
        </db:title>
        <db:abstract>
          <xsl:apply-templates mode="abstract" select="$content/html:div[@class = 'descr']"/>
        </db:abstract>
      </db:info>
      <xsl:apply-templates select="$content/div[@class = 'descr']" mode="description"/>
    </db:article>
  </xsl:template>

  <!-- Extract the abstract (i.e. everything after <a name="details"> and before the next <a> as top-level inside <div class="descr">). -->
  <!-- Follow-sibling recursivity: http://stackoverflow.com/a/21957114/1066843 -->
  <!-- Apply on siblings until new <a> met: http://stackoverflow.com/a/2165644/1066843 (implemented) -->
  <xsl:template match="*" mode="abstract">
    <xsl:apply-templates mode="abstract_waitForDescr" select=".[1]"/>
  </xsl:template>
  <xsl:template match="html:a[@name = 'details']" mode="abstract_waitForDescr">
    <xsl:variable name="marker" select="."/>
    <xsl:for-each select="following-sibling::html:*[preceding-sibling::html:a[1] = $marker]">
      <xsl:message terminate="no">WARNING: To do. Don't transform empty paragraphs; handle actual content. Can have lists too. </xsl:message>
      <db:para>
        <xsl:value-of select="."/>
      </db:para>
    </xsl:for-each>
  </xsl:template>
  
  <!-- Extract the content of the description. -->

  <xsl:template match="/html:div[@class = 'sidebar']" mode="mainContent"/>
  <!-- Don't care. -->
  <xsl:template match="/html:h1" mode="mainContent">
    <db:info>
      <db:title>
        <xsl:value-of select="."/>
      </db:title>
    </db:info>
  </xsl:template>
  <xsl:template match="*" mode="mainContent"> wtf </xsl:template>

  <!-- Catch-all for style sheet errors. -->
  <xsl:template match="*">
    <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of select="name()"/></xsl:message>

    <xsl:apply-templates/>
  </xsl:template>

</xsl:stylesheet>
