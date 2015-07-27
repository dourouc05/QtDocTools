<?xml version="1.0" encoding="UTF-8"?>
<!--
  Converts QDoc 5.4's HTML5 (first converted to XML) into DocBook. 
  Hypothesis: tables have <tbody> tags in the input (ensured automatically by Python's html5lib). 
  
  
  
  To check: types and arrays []. 
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xsl xs html" version="2.0">
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- <xsl:import-schema schema-location="http://www.docbook.org/xml/5.0/xsd/docbook.xsd"/> -->
  <!-- <xsl:import-schema schema-location="./schemas/docbook.xsd"/> -->

  <!-- Output document class. -->
  <xsl:template match="html:html">
    <xsl:variable name="content" select=".//html:div[@class = 'content mainContent']"/>

    <!-- Extract the metadata. -->
    <xsl:variable name="title" select="$content/html:h1/text()"/>
    <xsl:variable name="isClass"
      select="
        starts-with($title, 'Q')
        and ends-with($title, ' Class')
        and count(contains($title, ' ')) = 1"/>
    <xsl:variable name="className">
      <xsl:if test="$isClass">
        <xsl:value-of select="substring-before($title, ' Class')"/>
      </xsl:if>
    </xsl:variable>

    <!-- Extract the various parts of the main structure. -->
    <xsl:variable name="description" select="$content/html:div[@class = 'descr']" as="element()"/>
    <xsl:variable name="siblingAfterDescription" as="element()"
      select="$description/following-sibling::*[1]"/>

    <xsl:variable name="seeAlso" select="$siblingAfterDescription[self::html:p]" as="element()?"/>
    <xsl:variable name="hasSeeAlso" select="boolean($seeAlso)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterSeeAlso" as="element()"
      select="
        if ($hasSeeAlso) then
          $siblingAfterDescription/following-sibling::*[1]
        else
          $siblingAfterDescription"/>

    <xsl:variable name="index" as="element()?"
      select="$siblingAfterSeeAlso[self::html:div][@class = 'table']"/>
    <xsl:variable name="hasIndex" select="boolean($index)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterIndex"
      select="
        if ($hasIndex) then
          $siblingAfterSeeAlso/following-sibling::*[1]
        else
          $siblingAfterSeeAlso"/>

    <xsl:variable name="types" as="element()?"
      select="$siblingAfterIndex[self::html:div][@class = 'types']"/>
    <xsl:variable name="hasTypes" select="boolean($types)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterTypes"
      select="
        if ($hasTypes) then
          $siblingAfterIndex/following-sibling::*[1]
        else
          $siblingAfterIndex"/>

    <xsl:variable name="funcs" as="element(html:div)?"
      select="$siblingAfterTypes[self::html:div][@class = 'func']"/>
    <xsl:variable name="hasFuncs" select="boolean($funcs)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterFuncs"
      select="
        if ($hasFuncs) then
          $siblingAfterTypes/following-sibling::*[1]
        else
          $siblingAfterTypes"/>

    <xsl:variable name="nonmems" as="element(html:div)?"
      select="$siblingAfterFuncs[self::html:div][@class = 'relnonmem']"/>
    <xsl:variable name="hasNonmems" select="boolean($nonmems)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterNonmems"
      select="
        if ($hasNonmems) then
          $siblingAfterFuncs/following-sibling::*[1]
        else
          $siblingAfterFuncs"/>

    <!-- Error checks. -->
    <xsl:if test="boolean($siblingAfterNonmems)">
      <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of
          select="name($siblingAfterFuncs)"/>
      </xsl:message>
    </xsl:if>
    <xsl:if test="$isClass and not(boolean($funcs))">
      <xsl:message terminate="no">WARNING: A class has no functions.</xsl:message>
    </xsl:if>
    <xsl:if test="not($isClass) and boolean($types)">
      <xsl:message terminate="no">WARNING: A concept has types.</xsl:message>
    </xsl:if>
    <xsl:if test="not($isClass) and boolean($funcs)">
      <xsl:message terminate="no">WARNING: A concept has functions.</xsl:message>
    </xsl:if>

    <!-- Actually output something. -->
    <db:article version="5.0">
      <!-- xsl:validation="strict" -->
      <db:info>
        <db:title>
          <xsl:value-of select="$title"/>
        </db:title>
      </db:info>

      <!-- Output the list of methods of the class if any, then its related non-member functions. -->
      <xsl:if test="$isClass">
        <xsl:call-template name="classListing">
          <xsl:with-param name="data" select="$funcs"/>
          <xsl:with-param name="className" select="$className"/>
        </xsl:call-template>

        <xsl:if test="$hasNonmems">
          <xsl:call-template name="functionListing">
            <xsl:with-param name="data" select="$nonmems"/>
            <xsl:with-param name="className" select="$className"/>
          </xsl:call-template>
        </xsl:if>
      </xsl:if>

      <!-- Extract the description, i.e. the long text, plus the See also paragraph (meaning a paragraph just after the description for classes). -->
      <xsl:call-template name="content_withTitles">
        <xsl:with-param name="data" select="$description"/>
        <xsl:with-param name="hasSeeAlso" select="$hasSeeAlso"/>
        <xsl:with-param name="seeAlso" select="$seeAlso"/>
      </xsl:call-template>

      <!-- There may be a table for generated index pages. -->
      <xsl:if test="$hasIndex">
        <xsl:apply-templates mode="indexTable" select="$index"/>
      </xsl:if>

      <!-- There may be types and functions for classes. -->
      <xsl:if test="$hasTypes">
        <xsl:call-template name="content_types">
          <xsl:with-param name="data" select="$types"/>
        </xsl:call-template>
      </xsl:if>

      <xsl:if test="$hasFuncs">
        <xsl:call-template name="content_class">
          <xsl:with-param name="data" select="$funcs"/>
        </xsl:call-template>
      </xsl:if>

      <xsl:if test="$hasNonmems">
        <xsl:call-template name="content_nonmems">
          <xsl:with-param name="data" select="$nonmems"/>
        </xsl:call-template>
      </xsl:if>
    </db:article>
  </xsl:template>
  
  <!-- Utility templates, to be used everywhere. -->
  <xsl:template mode="content_title" match="text()">
    <xsl:value-of select="."/>
  </xsl:template>
  <xsl:template mode="content_title" match="html:code">
    <xsl:value-of select="./text()"/>
  </xsl:template>
  <xsl:template mode="content_title" match="html:a[@name]"/>
  <xsl:template mode="content_title" match="html:a[@href]">
    <xsl:value-of select="./text()"/>
  </xsl:template>
  <xsl:template mode="content_title" match="html:i">
    <xsl:apply-templates mode="content_title"/>
  </xsl:template>
  <xsl:template mode="content_title" match="html:span">
    <xsl:apply-templates mode="content_title"/>
    <xsl:text> </xsl:text>
  </xsl:template>

  <!-- Handle table of content sections. -->
  <xsl:template match="html:div" mode="indexTable">
    <xsl:apply-templates mode="indexTable"/>
  </xsl:template>
  <xsl:template match="html:table" mode="indexTable">
    <xsl:choose>
      <xsl:when test="./@class = 'annotated'">
        <!-- Like index pages. -->
        <xsl:apply-templates select="." mode="content"/>
      </xsl:when>
      <xsl:when test="./@class = 'alignedsummary'"/>
      <!-- Like class pages: just redundant. -->
      <xsl:otherwise>
        <xsl:message terminate="no">WARNING: Unknown table: <xsl:value-of select="./@class"
          /></xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Handle classes: class structure. -->
  <xsl:template name="classListing">
    <xsl:param name="data" as="element(html:div)"/>
    <xsl:param name="className" as="xs:string"/>

    <db:classsynopsis>
      <db:ooclass>
        <db:classname>
          <xsl:value-of select="$className"/>
        </db:classname>
      </db:ooclass>
      <xsl:apply-templates mode="classListing" select="$data/html:h3">
        <xsl:with-param name="className" select="$className"/>
      </xsl:apply-templates>
    </db:classsynopsis>
  </xsl:template>
  <xsl:template mode="classListing" match="text()"/>
  <xsl:template mode="classListing" match="html:h3[@class = 'fn']">
    <xsl:param name="className" as="xs:string"/>

    <!-- Possible anchors: for constructors, Class, Class-2; for destructors, dtor.Class -->
    <xsl:variable name="functionAnchor" select="./@id"/>
    <xsl:variable name="isCtor" select="starts-with($functionAnchor, $className)"/>
    <xsl:variable name="isDtor" select="starts-with($functionAnchor, 'dtor.')"/>
    <xsl:variable name="isFct" select="not($isCtor or $isDtor)"/>

    <xsl:choose>
      <xsl:when test="$isCtor">
        <db:constructorsynopsis>
          <xsl:attribute name="xlink:href" select="concat('#', $functionAnchor)"/>
          <xsl:call-template name="classListing_methodBody"/>
        </db:constructorsynopsis>
      </xsl:when>
      <xsl:when test="$isDtor">
        <db:destructorsynopsis>
          <xsl:attribute name="xlink:href" select="$functionAnchor"/>
          <xsl:call-template name="classListing_methodBody"/>
        </db:destructorsynopsis>
      </xsl:when>
      <xsl:when test="$isFct">
        <db:methodsynopsis>
          <xsl:attribute name="xlink:href" select="$functionAnchor"/>
          <xsl:call-template name="classListing_methodBody"/>
        </db:methodsynopsis>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="classListing_methodBody">
    <xsl:variable name="titleNode" select="."/>
    <xsl:variable name="functionName" select="./html:span[@class = 'name']"/>
    <xsl:variable name="returnTypes"
      select="$functionName/preceding-sibling::html:span[@class = 'type']"/>
    <xsl:variable name="isStatic" as="xs:boolean"
      select="boolean($returnTypes/preceding-sibling::html:code[normalize-space(text()) = '[static]'])"/>

    <xsl:if test="$isStatic">
      <db:modifier>static</db:modifier>
    </xsl:if>

    <xsl:if test="$returnTypes">
      <xsl:call-template name="classListing_methodBody_analyseType">
        <xsl:with-param name="typeNodes" select="$returnTypes"/>
      </xsl:call-template>
    </xsl:if>

    <db:methodname>
      <xsl:value-of select="$functionName"/>
    </db:methodname>

    <!-- Handle parameters list. -->
    <xsl:variable name="textAfterName" select="$functionName/following-sibling::text()[1]"/>
    <xsl:choose>
      <xsl:when test="starts-with($textAfterName, '()')">
        <db:void/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:variable name="nArguments" select="count(./text()[contains(., ',')]) + 1"/>

        <xsl:for-each select="1 to $nArguments">
          <xsl:variable name="index" select="." as="xs:integer"/>
          <xsl:variable name="commas" select="$titleNode/text()[contains(., ',')]"/>
          <xsl:variable name="firstNode"
            select="
              if (. = 1) then
                $functionName
              else
                $commas[$index - 1]"/>
          <xsl:variable name="types"
            select="$firstNode/following-sibling::html:span[@class = 'type']"/>
          <xsl:variable name="type" select="$types[1]"/>
          <xsl:variable name="textAfterType"
            select="normalize-space($type/following-sibling::text()[1])"/>

          <xsl:variable name="test" select="$firstNode/following-sibling::*"/>

          <db:methodparam>
            <!-- Maybe this parameter is const. -->
            <xsl:if test="normalize-space($textAfterName) = '(const'">
              <db:modifier>const</db:modifier>
            </xsl:if>

            <!-- Output the type. -->
            <db:type>
              <xsl:choose>
                <xsl:when test="$type/html:a">
                  <xsl:value-of select="$type/html:a"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="$type/text()"/>
                </xsl:otherwise>
              </xsl:choose>

              <!-- Maybe it's a pointer or a reference. -->
              <xsl:if test="$textAfterType = '&amp;' or $textAfterType = '*'">
                <xsl:value-of select="concat(' ', $textAfterType)"/>
              </xsl:if>
            </db:type>

            <!-- Then the name. -->
            <xsl:variable name="names" select="$type/following-sibling::html:i"/>
            <db:parameter>
              <xsl:value-of select="normalize-space($names[1])"/>
            </db:parameter>

            <!-- Eventually an initialiser. -->
            <xsl:variable name="afterName" select="$names[1]/following-sibling::text()[1]"/>
            <xsl:variable name="afterNameBeforeNext"
              select="
                if (not(contains($afterName, ','))) then
                  substring-before($afterName, ')')
                else
                  substring-before($afterName, ',')"/>
            <xsl:if test="contains($afterName, '=')">
              <db:initializer>
                <xsl:value-of select="normalize-space(substring-after($afterNameBeforeNext, '='))"/>
              </db:initializer>
            </xsl:if>
          </db:methodparam>
        </xsl:for-each>
      </xsl:otherwise>
    </xsl:choose>

    <!-- Handle function const. -->
    <xsl:variable name="textAfterArguments"
      select="normalize-space(substring-after($textAfterName, ')'))"/>
    <xsl:if test="string-length($textAfterArguments) > 0">
      <db:modifier>
        <xsl:value-of select="replace($textAfterArguments, ' \)', '')"/>
      </db:modifier>
    </xsl:if>
  </xsl:template>
  <xsl:template name="classListing_methodBody_analyseType">
    <xsl:param name="typeNodes" as="element()+"/>

    <xsl:choose>
      <xsl:when test="count($typeNodes) = 1">
        <xsl:variable name="node" select="$typeNodes[1]"/>
        <xsl:choose>
          <xsl:when test="$node/html:a">
            <db:type>
              <xsl:value-of select="$node/html:a/text()"/>
            </db:type>
          </xsl:when>
          <xsl:otherwise>
            <xsl:variable name="type" select="$node/text()"/>
            <xsl:choose>
              <xsl:when test="$type = 'void'">
                <db:void/>
              </xsl:when>
              <xsl:otherwise>
                <db:type>
                  <xsl:value-of select="$type"/>
                </db:type>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <db:type>
          <xsl:for-each select="1 to count($typeNodes)">
            <!-- Output the class names. -->
            <xsl:variable name="node" select="subsequence($typeNodes, ., 1)"/>
            <xsl:choose>
              <xsl:when test="$node/html:a">
                <xsl:value-of select="$node/html:a/text()"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="$node"/>
              </xsl:otherwise>
            </xsl:choose>

            <!-- Output the chevrons. -->
            <xsl:if test="not(position() = count($typeNodes))">&lt;</xsl:if>
          </xsl:for-each>

          <!-- Close the chevrons. -->
          <xsl:for-each select="1 to count($typeNodes) - 1">&gt;</xsl:for-each>
        </db:type>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="functionListing">
    <xsl:param name="data" as="element(html:div)"/>
    <xsl:param name="className" as="xs:string"/>

    <xsl:apply-templates mode="functionListing" select="$data/html:h3">
      <xsl:with-param name="className" select="$className"/>
    </xsl:apply-templates>
  </xsl:template>
  <xsl:template mode="functionListing" match="text()"/>
  <xsl:template mode="functionListing" match="html:h3[@class = 'fn'][ends-with(@id, '-typedef')]">
    <xsl:message terminate="no">WARNING: No summary output for typedefs. </xsl:message>
  </xsl:template>
  <xsl:template mode="functionListing" match="html:h3[@class = 'fn'][not(ends-with(@id, '-typedef'))]">
    <db:funcsynopsis>
      <xsl:attribute name="xlink:href" select="concat('#', ./@id)"/>

      <db:funcprototype>
        <xsl:variable name="titleNode" select="."/>
        <xsl:variable name="functionName" select="./html:span[@class = 'name']"/>
        <xsl:variable name="returnTypes"
          select="$functionName/preceding-sibling::html:span[@class = 'type']"/>
        <xsl:variable name="isStatic" as="xs:boolean"
          select="boolean($returnTypes/preceding-sibling::html:code[normalize-space(text()) = '[static]'])"/>

        <xsl:if test="$isStatic">
          <db:modifier>static</db:modifier>
        </xsl:if>

        <db:funcdef>
          <xsl:if test="$returnTypes">
            <xsl:call-template name="classListing_methodBody_analyseType">
              <xsl:with-param name="typeNodes" select="$returnTypes"/>
            </xsl:call-template>
          </xsl:if>

          <db:function>
            <xsl:value-of select="$functionName"/>
          </db:function>
        </db:funcdef>

        <!-- Handle parameters list. -->
        <xsl:variable name="textAfterName" select="$functionName/following-sibling::text()[1]"/>
        <xsl:choose>
          <xsl:when test="starts-with($textAfterName, '()')">
            <db:void/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:variable name="nArguments" select="count(./text()[contains(., ',')]) + 1"/>

            <xsl:for-each select="1 to $nArguments">
              <xsl:variable name="index" select="." as="xs:integer"/>
              <xsl:variable name="commas" select="$titleNode/text()[contains(., ',')]"/>
              <xsl:variable name="firstNode"
                select="
                  if (. = 1) then
                    $functionName
                  else
                    $commas[$index - 1]"/>
              <xsl:variable name="types"
                select="$firstNode/following-sibling::html:span[@class = 'type']"/>
              <xsl:variable name="type" select="$types[1]"/>
              <xsl:variable name="textAfterType"
                select="normalize-space($type/following-sibling::text()[1])"/>

              <xsl:variable name="test" select="$firstNode/following-sibling::*"/>

              <db:paramdef>
                <!-- Maybe this parameter is const. -->
                <xsl:if test="normalize-space($textAfterName) = '(const'">
                  <db:modifier>const</db:modifier>
                </xsl:if>

                <!-- Output the type. -->
                <db:type>
                  <xsl:choose>
                    <xsl:when test="$type/html:a">
                      <xsl:value-of select="$type/html:a"/>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:value-of select="$type/text()"/>
                    </xsl:otherwise>
                  </xsl:choose>

                  <!-- Maybe it's a pointer or a reference. -->
                  <xsl:if test="$textAfterType = '&amp;' or $textAfterType = '*'">
                    <xsl:value-of select="concat(' ', $textAfterType)"/>
                  </xsl:if>
                </db:type>

                <!-- Then the name. -->
                <xsl:variable name="names" select="$type/following-sibling::html:i"/>
                <db:parameter>
                  <xsl:value-of select="normalize-space($names[1])"/>
                </db:parameter>
              </db:paramdef>
            </xsl:for-each>
          </xsl:otherwise>
        </xsl:choose>
      </db:funcprototype>
    </db:funcsynopsis>
  </xsl:template>

  <!-- Handle types: detailed description. -->
  <xsl:template name="content_types">
    <xsl:param name="data" as="element(html:div)"/>

    <db:section>
      <db:title>Member Type Documentation</db:title>
      <xsl:apply-templates mode="content_types" select="$data/html:h3"/>
    </db:section>
  </xsl:template>
  <!-- 
    Two types of types: either class="fn", just an enum; or class="flags". 
    In the latter case, the title mentions both an enum and a flags, separated with a <br/>. 
  -->
  <xsl:template mode="content_types" match="html:h3[@class = 'fn']">
    <xsl:variable name="functionAnchor" select="./@id"/>

    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>

      <db:info>
        <db:title>
          <xsl:apply-templates mode="content_title"/>
        </db:title>
      </db:info>
      
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_types" match="html:h3[@class = 'flags']">
    <xsl:variable name="functionAnchor" select="./@id"/>

    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>

      <db:info>
        <db:title>
          <xsl:apply-templates mode="content_title"/>
        </db:title>
      </db:info>

      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>

  <!-- Handle classes: detailed description. -->
  <xsl:template name="content_class">
    <xsl:param name="data" as="element(html:div)"/>

    <db:section>
      <db:title>Member Function Documentation</db:title>
      <xsl:apply-templates mode="content_class" select="$data/html:h3"/>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_class" match="html:h3[@class = 'fn']">
    <xsl:variable name="functionAnchor" select="./@id"/>
    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>

      <db:info>
        <db:title>
          <xsl:apply-templates mode="content_title"/>
        </db:title>
      </db:info>

      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  <xsl:template name="content_class_content">
    <xsl:param name="node" as="element()?"/>

    <xsl:if test="$node and not($node[self::html:h3])">
      <xsl:apply-templates mode="content" select="$node"/>
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="$node/following-sibling::*[1]"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>

  <!-- Handle classes: non-member related functions. -->
  <xsl:template name="content_nonmems">
    <xsl:param name="data" as="element(html:div)"/>

    <db:section>
      <db:title>Related Non-Members</db:title>
      <xsl:apply-templates mode="content_nonmems" select="$data/html:h3"/>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_nonmems" match="html:h3[@class = 'fn'][ends-with(@id, '-typedef')]">
    <xsl:variable name="functionAnchor" select="./@id"/>
    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>

      <db:info>
        <db:title>
          <xsl:apply-templates mode="content_title"/>
        </db:title>
      </db:info>

      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_nonmems"
    match="html:h3[@class = 'fn'][not(ends-with(@id, '-typedef'))]">
    <xsl:variable name="functionAnchor" select="./@id"/>
    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>

      <db:info>
        <db:title>
          <xsl:apply-templates mode="content_title"/>
        </db:title>
      </db:info>

      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
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
  <xsl:template mode="content" match="html:div[@class = 'table']">
    <xsl:apply-templates select="*" mode="content"/>
  </xsl:template>
  <xsl:template mode="content" match="html:table">
    <db:informaltable>
      <xsl:apply-templates select="*" mode="content_table"/>
    </db:informaltable>
  </xsl:template>
  <xsl:template mode="content" match="html:a[@name]">
    <db:anchor>
      <xsl:attribute name="xml:id" select="@name"/>
    </db:anchor>
  </xsl:template>
  <xsl:template mode="content" match="html:p">
    <!-- A paragraph may hold a single image (treat it accordingly), or be an admonition, or be a real paragraph. -->
    <xsl:choose>
      <xsl:when test="count(child::*) = 1 and child::html:img">
        <db:informalfigure>
          <!--<db:title>docbook.xsd</db:title>-->
          <!-- If title: <figure>. Otherwise: <informalfigure>. -->
          <db:mediaobject>
            <xsl:if test="./html:img[@alt]">
              <db:alt>
                <xsl:value-of select="./html:img/@alt"/>
              </db:alt>
            </xsl:if>

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
      <xsl:when test="./html:b and starts-with(./html:b/text(), 'Note')">
        <db:note>
          <db:para>
            <xsl:apply-templates mode="content_paragraph">
              <xsl:with-param name="forgetNotes" select="true()"/>
            </xsl:apply-templates>
          </db:para>
        </db:note>
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
    <xsl:param name="hasSeeAlso"/>
    <xsl:param name="seeAlso"/>

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

        <xsl:if test="$hasSeeAlso">
          <db:section>
            <db:info>
              <db:title>See Also</db:title>
            </db:info>
            <db:simplelist>
              <xsl:for-each select="$seeAlso/html:a">
                <db:member>
                  <xsl:apply-templates mode="content_paragraph" select="."/>
                </db:member>
              </xsl:for-each>
            </db:simplelist>
          </db:section>
        </xsl:if>
      </db:section>
    </xsl:for-each-group>
  </xsl:template>

  <!-- Handle tables. -->
  <xsl:template mode="content_table" match="html:thead">
    <db:thead>
      <xsl:apply-templates select="*" mode="content_table"/>
    </db:thead>
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
  <xsl:template mode="content_table" match="html:th">
    <db:th>
      <xsl:choose>
        <xsl:when test="./child::html:p">
          <xsl:apply-templates select="*" mode="content"/>
        </xsl:when>
        <xsl:otherwise>
          <db:para>
            <xsl:apply-templates mode="content_paragraph"/>
          </db:para>
        </xsl:otherwise>
      </xsl:choose>
    </db:th>
  </xsl:template>
  <xsl:template mode="content_table" match="html:td">
    <db:td>
      <xsl:choose>
        <xsl:when test="./child::html:p">
          <xsl:apply-templates select="*" mode="content"/>
        </xsl:when>
        <xsl:otherwise>
          <db:para>
            <xsl:apply-templates mode="content_paragraph"/>
          </db:para>
        </xsl:otherwise>
      </xsl:choose>
    </db:td>
  </xsl:template>

  <!-- Handle lists. -->
  <xsl:template mode="content_list" match="html:li">
    <db:listitem>
      <xsl:choose>
        <!-- 
          Has it a paragraph child? DocBook needs one! QDoc peculiarity: may very well have text, then
          a paragraph tag! 
        -->
        <xsl:when test="./child::node()[1][self::html:p]">
          <xsl:apply-templates mode="content"/>
        </xsl:when>
        <xsl:when test="./html:p">
          <!-- It has a paragraph, but it's not the first element. Treat the beginning as if there were no paragraph near, then do the paragraph separately. -->
          <db:para>
            <xsl:apply-templates mode="content_paragraph"/>
          </db:para>
          <xsl:apply-templates mode="content" select="./html:p"/>
        </xsl:when>
        <xsl:otherwise>
          <db:para>
            <xsl:apply-templates mode="content_paragraph"/>
          </db:para>
        </xsl:otherwise>
      </xsl:choose>
    </db:listitem>
  </xsl:template>

  <!-- Handle inline elements, inside paragraphs. DocBook happily allows lists inside paragraphs. -->
  <xsl:template mode="content_paragraph" match="text()">
    <xsl:choose>
      <xsl:when test="./preceding-sibling::*[1][self::html:a] and starts-with(., '(')">
        <xsl:value-of select="substring-after(., ')')"/>
      </xsl:when>
      <xsl:when test="./preceding-sibling::*[1][self::html:a] and starts-with(., '&lt;')">
        <!-- Templated type: starts with &lt;, ends with &gt;. -->
        <xsl:value-of select="substring-after(., '>')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="."/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:a">
    <!-- 
      Output a link, maybe enclosing its content with <db:code> when it's a method (followed by parentheses) or a class. 
      
      Strange things to output <db:code> (output it as pure text, but unescaped), just to ensure there is no whitespace 
      between this tag and the link, i.e. visible space to the user!
    -->
    <xsl:choose>
      <xsl:when test="starts-with(./following-sibling::text()[1], '()')">
        <xsl:text disable-output-escaping="yes">&lt;db:code&gt;</xsl:text>
        <db:link>
          <xsl:attribute name="xlink:href" select="@href"/>
          <xsl:apply-templates mode="content_paragraph"/>
        </db:link>

        <xsl:variable name="toEndList"
          select="substring-before(./following-sibling::text()[1], ')')[1]"/>
        <xsl:variable name="justList" select="substring-after($toEndList, '(')"/>

        <xsl:text>(</xsl:text>
        <xsl:value-of select="$justList"/>
        <xsl:text>)</xsl:text>

        <xsl:text disable-output-escaping="yes">&lt;/db:code&gt;</xsl:text>
      </xsl:when>
      <xsl:when test="starts-with(./text(), 'Q')">
        <xsl:text disable-output-escaping="yes">&lt;db:code&gt;</xsl:text>
        <db:link>
          <xsl:attribute name="xlink:href" select="@href"/>
          <xsl:apply-templates mode="content_paragraph"/>
        </db:link>

        <!-- Maybe it's templated. -->
        <xsl:if test="starts-with(./following-sibling::text()[1], '&lt;')">
          <xsl:variable name="toEndTemplate"
            select="substring-before(./following-sibling::text()[1], '>')[1]"/>
          <xsl:variable name="templateArgument" select="substring-after($toEndTemplate, '&lt;')"/>

          <xsl:text>&lt;</xsl:text>
          <xsl:value-of select="$templateArgument"/>
          <xsl:text>&gt;</xsl:text>
        </xsl:if>

        <xsl:text disable-output-escaping="yes">&lt;/db:code&gt;</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <db:link>
          <xsl:attribute name="xlink:href" select="@href"/>
          <xsl:apply-templates mode="content_paragraph"/>
        </db:link>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:b | html:strong">
    <xsl:param name="forgetNotes" select="false()"/>
    <xsl:if test="not($forgetNotes and starts-with(text(), 'Note'))">
      <db:emphasis role="bold">
        <xsl:apply-templates mode="content_paragraph"/>
      </db:emphasis>
    </xsl:if>
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
  <xsl:template mode="content_paragraph" match="html:code">
    <db:code>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:code>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:img">
    <db:inlinemediaobject>
      <xsl:if test=".[@alt] and not(./@alt = '')">
        <db:alt>
          <xsl:value-of select="./@alt"/>
        </db:alt>
      </xsl:if>
      <db:imageobject>
        <db:imagedata>
          <xsl:attribute name="fileref">
            <xsl:value-of select="./@src"/>
          </xsl:attribute>
        </db:imagedata>
      </db:imageobject>
    </db:inlinemediaobject>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:p">
    <xsl:if test="./child::*[self::html:img]">
      <xsl:apply-templates mode="content_paragraph"/>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
