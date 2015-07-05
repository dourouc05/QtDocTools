<?xml version="1.0" encoding="UTF-8"?>
<!--
  Converts QDoc 5.4's HTML5 (first converted to XML) into DocBook. 
  Hypothesis: tables have <tbody> tags in the input (ensured automatically by Python's html5lib). 
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xsl xs html" version="2.0">
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- <xsl:import-schema schema-location="http://www.docbook.org/xml/5.0/xsd/docbook.xsd"/> -->
  <xsl:import-schema schema-location="./schemas/docbook.xsd"/>

  <!-- Output document class. -->
  <xsl:template match="html:html">
    <xsl:variable name="content" select=".//html:div[@class = 'content mainContent']"/>

    <db:article version="5.0">
      <!-- Extract the metadata. -->
      <db:info>
        <db:title>
          <xsl:value-of select="$content/html:h1/text()"/>
        </db:title>
      </db:info>

      <!-- Extract the description, i.e. the long text. -->
      <xsl:variable name="description" select="$content/html:div[@class = 'descr']"/>
      <xsl:variable name="siblingAfterDescription" select="$description/following-sibling::*[1]"/>
      <!--<xsl:apply-templates mode="content" select="$description"/>-->
      <xsl:call-template name="content_withTitles">
        <xsl:with-param name="data" select="$description"/>
      </xsl:call-template>

      <!-- If there is a paragraph just after the description, it's a See also paragraph. -->
      <xsl:variable name="hasSeeAlso" select="boolean($siblingAfterDescription[self::html:p])"
        as="xs:boolean"/>
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
        <xsl:apply-templates mode="indexTable"
          select="$siblingAfterSeeAlso[self::html:div][@class = 'table']"/>
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
        <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of
            select="name($siblingAfterFuncs)"/>
        </xsl:message>
      </xsl:if>
    </db:article>
  </xsl:template>

  <!-- Handle main content sections. -->
  <xsl:template match="html:table[@class = 'annotated']" mode="indexTable">
    <xsl:apply-templates select="." mode="content"/>
  </xsl:template>

  <!-- 
    Handle HTML content and transform it into DocBook. 
    Tables are implemented with HTML model, not CALS. 
  -->
  <xsl:template mode="content" match="html:table">
    <db:informaltable>
      <xsl:apply-templates select="*" mode="content_table"/>
    </db:informaltable>
  </xsl:template>
  <xsl:template mode="content" match="html:p">
    <db:para>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:para>
  </xsl:template>
  <xsl:template mode="content" match="html:h2 | html:h3"/>
  
  <xsl:template name="content_withTitles_before">
    <xsl:param name="data"/>
    
    <xsl:for-each select="$data/*">
      <xsl:choose>
        <xsl:when test="not(.[self::html:h2] or ./preceding-sibling::html:h2 or .[self::html:h3] or ./preceding-sibling::html:h3)">
          <xsl:apply-templates mode="content" select="."/>
        </xsl:when>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="content_withTitles">
    <xsl:param name="data"/>
    
    <xsl:call-template name="content_withTitles_before">
      <xsl:with-param name="data" select="$data"/>
    </xsl:call-template>
    <xsl:variable name="firstTitle" select="$data/html:h2[1]"></xsl:variable>
    <xsl:variable name="afterFirstTitleIncluded" select="($firstTitle, $firstTitle/following-sibling::*)"/>
    
    <xsl:for-each-group select="$afterFirstTitleIncluded" group-starting-with="html:h2">
      <db:section>
        <db:title><xsl:copy-of select="./text()"></xsl:copy-of></db:title>
        
        <xsl:for-each-group select="current-group()" group-starting-with="html:h3">
          <xsl:choose>
            <xsl:when test="current-group()[self::html:h3]">
              <db:section>
                <db:title><xsl:copy-of select="./text()"></xsl:copy-of></db:title>
              </db:section>
            </xsl:when>
            <xsl:otherwise>
              <xsl:apply-templates select="current-group()" mode="content"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each-group>
      </db:section>
    </xsl:for-each-group>
  </xsl:template>

  <xsl:template mode="content_table" match="html:tbody">
    <db:tbody>
      <xsl:apply-templates select="*" mode="content_table"/>
    </db:tbody>
  </xsl:template>
  <xsl:template mode="content_table" match="html:tr">
    <db:tr>
      <xsl:apply-templates select="*" mode="content_table"/>
    </db:tr>
  </xsl:template>
  <xsl:template mode="content_table" match="html:td">
    <db:td>
      <xsl:apply-templates select="*" mode="content"/>
    </db:td>
  </xsl:template>

  <xsl:template mode="content_paragraph" match="html:a">
    <db:link>
      <xsl:attribute name="xlink:href" select="@href"/>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:link>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="text()">
    <!-- <xsl:value-of select="normalize-space(.)"/> -->
    <xsl:value-of select="."/>
  </xsl:template>

  <!-- Catch-all for style sheet errors. -->
  <xsl:template match="*">
    <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of select="name()"/>
    </xsl:message>

    <xsl:apply-templates/>
  </xsl:template>

</xsl:stylesheet>
