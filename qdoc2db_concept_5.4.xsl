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
      <!-- Extract the metadata. -->
      <db:info>
        <db:title>
          <xsl:value-of select="$content/html:h1/text()"/>
        </db:title>
      </db:info>

      <!-- Extract the description, i.e. the long text. -->
      <xsl:variable name="description" select="$content/html:div[@class = 'descr']"/>
      <xsl:variable name="siblingAfterDescription" select="$description/following-sibling::*[1]"/>
      <xsl:apply-templates select="$description" mode="description"/>

      <!-- If there is a paragraph just after the description, it's a See also paragraph. -->
      <xsl:variable name="hasSeeAlso" select="boolean($siblingAfterDescription[self::html:p])" as="xs:boolean"/>
      <xsl:if test="$hasSeeAlso">
        <xsl:message terminate="no">WARNING: To do. Implement See also. </xsl:message>
      </xsl:if>
      <xsl:variable name="siblingAfterSeeAlso"
        select="
          if ($hasSeeAlso) then
            $siblingAfterDescription/following-sibling::*[1]
          else
            $siblingAfterDescription"/>

      <!-- There may be a table for generated index pages. -->
      <xsl:variable name="hasIndex"
        select="boolean($siblingAfterSeeAlso[self::html:div][@class = 'table'])" as="xs:boolean"/>
      <xsl:if test="$hasIndex">
        <xsl:message terminate="no">WARNING: To do. Implement index pages. </xsl:message>
      </xsl:if>
      <xsl:variable name="siblingAfterIndex"
        select="
          if ($hasIndex) then
            $siblingAfterSeeAlso/following-sibling::*[1]
          else
            $siblingAfterSeeAlso"/>

      <!-- There may be types and functions for classes. -->
      <xsl:variable name="hasTypes"
        select="boolean($siblingAfterIndex[self::html:div][@class = 'types'])" as="xs:boolean"/>
      <xsl:if test="$hasTypes">
        <xsl:message terminate="no">WARNING: To do. Implement types. </xsl:message>
      </xsl:if>
      <xsl:variable name="siblingAfterTypes"
        select="
          if ($hasTypes) then
            $siblingAfterIndex/following-sibling::*[1]
          else
            $siblingAfterIndex"/>

      <xsl:variable name="hasFuncs"
        select="boolean($siblingAfterTypes[self::html:div][@class = 'funcs'])" as="xs:boolean"/>
      <xsl:if test="$hasFuncs">
        <xsl:message terminate="no">WARNING: To do. Implement functions. </xsl:message>
      </xsl:if>
      <xsl:variable name="siblingAfterFuncs"
        select="
          if ($hasFuncs) then
            $siblingAfterTypes/following-sibling::*[1]
          else
            $siblingAfterTypes"/>

      <!-- Catch missing elements. -->
      <xsl:variable name="hasAfterFuncs" select="boolean($siblingAfterFuncs)" as="xs:boolean"/>
      <xsl:if test="$hasAfterFuncs">
        <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of select="name($siblingAfterFuncs)"/>
        </xsl:message>
      </xsl:if>
    </db:article>
  </xsl:template>

  <!-- Catch-all for style sheet errors. -->
  <xsl:template match="*">
    <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of select="name()"/>
    </xsl:message>

    <xsl:apply-templates/>
  </xsl:template>

</xsl:stylesheet>
