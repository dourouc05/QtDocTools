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
          <xsl:variable name="title" select="$content/html:h1/text()"/>
          <xsl:choose>
            <xsl:when test="starts-with($title, 'Q') 
              and ends-with($title, ' Class')
              and count(contains($title, ' ')) = 1">
              <xsl:value-of select="substring-before($title, ' Class')"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="$title"/>
            </xsl:otherwise>
          </xsl:choose>
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
          select="$siblingAfterSeeAlso[self::html:div][@class = 'table']/html:table"/>
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
  <xsl:template match="html:div" mode="indexTable">
    <xsl:apply-templates mode="indexTable"/>
  </xsl:template>
  <xsl:template match="html:table" mode="indexTable">
    <xsl:choose>
      <xsl:when test="./@class = 'annotated'"> <!-- Like index pages. -->
        <xsl:apply-templates select="." mode="content"/>
      </xsl:when>
      <xsl:when test="./@class = 'alignedsummary'"/> <!-- Like class pages: just redundant. -->
      <xsl:otherwise>
        <xsl:message terminate="no">WARNING: Unknown table: <xsl:value-of select="./@class"/></xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Catch-all for style sheet errors. -->
  <xsl:template match="*" mode="#all">
    <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of select="name()"
      /></xsl:message>
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
  <xsl:template mode="content" match="html:a">
    <db:anchor>
      <xsl:attribute name="xml:id" select="@name"/>
    </db:anchor>
  </xsl:template>
  <xsl:template mode="content" match="html:p">
    <!-- A paragraph may hold a single image (treat it accordingly), or be a real paragraph. -->
    <xsl:choose>
      <xsl:when test="count(child::*) = 1 and child::html:img and @class = 'centerAlign'">
        <xsl:if test="not(./html:img[@alt] = '')">
          <xsl:message terminate="no">WARNING: Unmatched attribute alt: <xsl:value-of select="@alt"
            /></xsl:message>
        </xsl:if>

        <db:informalfigure>
          <!--<db:title>docbook.xsd</db:title>-->
          <!-- If title: <figure>. Otherwise: <informalfigure>. -->
          <db:mediaobject>
            <db:imageobject>
              <db:imagedata>
                <xsl:attribute name="fileref">
                  <xsl:copy-of select="./html:img/@src"/>
                </xsl:attribute>
              </db:imagedata>
            </db:imageobject>
          </db:mediaobject>
        </db:informalfigure>
      </xsl:when>
      <xsl:otherwise>
        <xsl:if test="not(. = '')">
          <db:para>
            <xsl:apply-templates mode="content_paragraph"/>
          </db:para>
        </xsl:if>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content" match="html:h2 | html:h3"/>
  <xsl:template mode="content" match="html:pre">
    <db:programlisting>
      <xsl:if test=".[@class]">
        <xsl:attribute name="language" select="@class"/>
      </xsl:if>
      <xsl:value-of select="."/>
    </db:programlisting>
  </xsl:template>
  <xsl:template mode="content" match="html:ul">
    <db:itemizedlist>
      <xsl:apply-templates mode="content_list"/>
    </db:itemizedlist>
  </xsl:template>
  <xsl:template mode="content" match="html:ol">
    <db:orderedlist>
      <xsl:apply-templates mode="content_list"/>
    </db:orderedlist>
  </xsl:template>

  <!-- Handle sections. Based on http://www.ibm.com/developerworks/library/x-addstructurexslt/. -->
  <xsl:template name="content_withTitles_before">
    <xsl:param name="data"/>
    <xsl:for-each select="$data/*">
      <xsl:choose>
        <xsl:when
          test="
            not(.[self::html:h2] or ./preceding-sibling::html:h2
            or .[self::html:h3] or ./preceding-sibling::html:h3
            or .[self::html:h4] or ./preceding-sibling::html:h4
            or .[self::html:h5] or ./preceding-sibling::html:h5
            or .[self::html:h6] or ./preceding-sibling::html:h6)">
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
    <xsl:variable name="firstTitle" select="$data/html:h2[1]"/>
    <xsl:variable name="afterFirstTitleIncluded"
      select="
        ($firstTitle,
        $firstTitle/following-sibling::*)"/>

    <xsl:for-each-group select="$afterFirstTitleIncluded" group-starting-with="html:h2">
      <db:section>
        <db:title>
          <xsl:copy-of select="./text()"/>
        </db:title>

        <xsl:for-each-group select="current-group()" group-starting-with="html:h3">
          <xsl:choose>
            <xsl:when test="current-group()[self::html:h3]">
              <db:section>
                <db:title>
                  <xsl:copy-of select="./text()"/>
                </db:title>

                <xsl:for-each-group select="current-group()" group-starting-with="html:h4">
                  <xsl:choose>
                    <xsl:when test="current-group()[self::html:h4]">
                      <db:section>
                        <db:title>
                          <xsl:copy-of select="./text()"/>
                        </db:title>
                      </db:section>

                      <xsl:for-each-group select="current-group()" group-starting-with="html:h5">
                        <xsl:choose>
                          <xsl:when test="current-group()[self::html:h5]">
                            <db:section>
                              <db:title>
                                <xsl:copy-of select="./text()"/>
                              </db:title>

                              <xsl:for-each-group select="current-group()"
                                group-starting-with="html:h6">
                                <xsl:choose>
                                  <xsl:when test="current-group()[self::html:h6]">
                                    <db:section>
                                      <db:title>
                                        <xsl:copy-of select="./text()"/>
                                      </db:title>

                                      <xsl:apply-templates select="current-group()" mode="content"/>
                                    </db:section>
                                  </xsl:when>
                                  <xsl:otherwise>
                                    <xsl:apply-templates select="current-group()" mode="content"/>
                                  </xsl:otherwise>
                                </xsl:choose>
                              </xsl:for-each-group>
                            </db:section>
                          </xsl:when>
                          <xsl:otherwise>
                            <xsl:apply-templates select="current-group()" mode="content"/>
                          </xsl:otherwise>
                        </xsl:choose>
                      </xsl:for-each-group>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:apply-templates select="current-group()" mode="content"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:for-each-group>
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

  <!-- Handle tables. -->
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

  <!-- Handle lists. -->
  <xsl:template mode="content_list" match="html:li">
    <db:listitem>
      <xsl:choose>
        <!-- Has it a paragraph child? DocBook needs one! -->
        <xsl:when test="*[1][self::html:p]">
          <xsl:apply-templates mode="content_paragraph"/>
        </xsl:when>
        <xsl:otherwise>
          <db:para>
            <!-- Add it if it is not there. Don't forget to handle text (. instead of *). -->
            <xsl:apply-templates mode="content_paragraph"/>
          </db:para>
        </xsl:otherwise>
      </xsl:choose>
    </db:listitem>
  </xsl:template>

  <!-- Handle inline elements, inside paragraphs. DocBook happily allows lists inside paragraphs. -->
  <xsl:template mode="content_paragraph" match="text()">
    <!-- <xsl:value-of select="normalize-space(.)"/> -->
    <xsl:value-of select="."/>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:a">
    <db:link>
      <xsl:attribute name="xlink:href" select="@href"/>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:link>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:b | html:strong">
    <db:emphasis role="strong">
      <xsl:apply-templates mode="content_paragraph"/>
    </db:emphasis>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:i | html:em">
    <!-- Need to distinguish them? -->
    <db:emphasis>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:emphasis>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:ul">
    <db:itemizedlist>
      <xsl:apply-templates mode="content_list"/>
    </db:itemizedlist>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:ol">
    <db:orderedlist>
      <xsl:apply-templates mode="content_list"/>
    </db:orderedlist>
  </xsl:template>
</xsl:stylesheet>
