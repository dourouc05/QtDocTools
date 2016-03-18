<?xml version="1.0" encoding="UTF-8"?>
<!--
  Converts QDoc 5.4's HTML5 (first converted to XML) into DocBook. 
  Hypothesis: tables have <tbody> tags in the input (ensured automatically by Python's html5lib). 
  
  
  
  To check: types and arrays []. 
  How to retrieve base class?
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xsl xs html" version="2.0">
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- <xsl:import-schema schema-location="http://www.docbook.org/xml/5.0/xsd/docbook.xsd"/> -->
  <xsl:import-schema schema-location="../schemas/docbook.xsd" use-when="system-property('xsl:is-schema-aware')='yes'"/>

  <!-- Output document class. (First a trick to keep the same stylesheet schema-aware and non-schema-aware.) -->
  <xsl:template match="/" use-when="system-property('xsl:is-schema-aware')='yes'" priority="2">
    <xsl:variable name="input" as="document-node()">
      <xsl:document>
        <xsl:copy-of select="/"/>
      </xsl:document>
    </xsl:variable>
    <xsl:result-document validation="strict">
      <xsl:apply-templates select="$input/html:html"/>
    </xsl:result-document>
  </xsl:template>
  <xsl:template match="/">
    <xsl:apply-templates select="html:html"/>
  </xsl:template>    
  
  <!-- @TODO: classes may have a <ul> somewhere in the beginning to indicate other files to load. -->
  <xsl:template match="html:html">
    <xsl:variable name="content" select=".//html:div[@class = 'content mainContent']"/>

    <!-- Extract the metadata. -->
    <xsl:variable name="title" select="$content/html:h1[@class = 'title']/text()" as="xs:string"/>
    <xsl:variable name="subtitle" as="xs:string"
      select="string($content/html:span[@class = 'subtitle']/text())"/>
    <xsl:variable name="hasSubtitle" as="xs:boolean" select="not($subtitle = '')"/>

    <xsl:variable name="isClass" as="xs:boolean"
      select="
        starts-with($title, 'Q')
        and ends-with($title, ' Class')
        and count(contains($title, ' ')) = 1"/>
    <xsl:variable name="className">
      <xsl:if test="$isClass">
        <xsl:value-of select="substring-before($title, ' Class')"/>
      </xsl:if>
    </xsl:variable>

    <xsl:variable name="isQmlType" as="xs:boolean" select="ends-with($title, ' QML Type')"/>
    <xsl:variable name="qmlTypeName">
      <xsl:if test="$isQmlType">
        <xsl:value-of select="substring-before($title, ' QML Type')"/>
      </xsl:if>
    </xsl:variable>

    <!-- Extract the various parts of the prologue. -->
    <xsl:variable name="prologueTable"
      select="
        $content
        /html:div[@class = 'table'][preceding-sibling::node()[self::html:p]][1]
        /html:table[@class = 'alignedsummary']/html:tbody/html:tr"/>
    <!-- C++ classes. -->
    <xsl:variable name="prologueHeader" as="element(html:span)?"
      select="$prologueTable/html:td[1][contains(text(), 'Header')]/following-sibling::html:td/html:span"/>
    <xsl:variable name="prologueQmake" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'qmake')]/following-sibling::html:td"/>
    <!-- QML types. -->
    <xsl:variable name="prologueImport" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'Import')]/following-sibling::html:td"/>
    <xsl:variable name="prologueInstantiates" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'Instantiates')]/following-sibling::html:td"/>
    <!-- For both.  -->
    <xsl:variable name="prologueInherits" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'Inherits')]/following-sibling::html:td"/>
    <xsl:variable name="prologueInheritedBy" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'Inherited')]/following-sibling::html:td"/>
    <xsl:variable name="prologueSince" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'Since')]/following-sibling::html:td"/>
    <!-- Check all lines have been used. -->
    <xsl:variable name="prologueCount" as="xs:integer"
      select="
      count((boolean($prologueHeader), boolean($prologueQmake), boolean($prologueInstantiates), boolean($prologueInherits), boolean($prologueInheritedBy), boolean($prologueImport), boolean($prologueSince))[. = true()])"
    />
    <xsl:if test="count($prologueTable) != $prologueCount">
      <xsl:message>WARNING: One or more rows of prologue table not recognised.</xsl:message>
    </xsl:if>

    <!-- Extract the various parts of the main structure. 
      QML types have a description without outer <div>; complexity to deal with this.
      The following code will look after siblings of $description for classes, no copy allowed in this case!
    -->
    <!-- 
      TODO: copy anchors into DocBook! E.g.: 
      <html:a name="key-handling"></html:a>
      <html:h3>Key Handling</html:h3> 
      Must be done afterwards, when $description is used. 
    -->
    <xsl:variable name="descriptionRawQml" as="element()?">
        <xsl:if test="$isQmlType">
          <xsl:variable name="propText" select="'Property Documentation'" as="xs:string"/>
          <xsl:variable name="methText" select="'Method Documentation'" as="xs:string"/>
          
          <xsl:variable name="descTitle" select="$content/html:h2[@id = 'details']" as="element()"/>
          <xsl:variable name="descTitleId" select="generate-id($descTitle)"/>
          <xsl:variable name="propTitle" select="$content/html:h2[text() = $propText]" as="element()"/>
          <xsl:variable name="propTitleId" select="generate-id($propTitle)"/>
          <xsl:variable name="methTitle" select="$content/html:h2[text() = $methText]" as="element()"/>
          <xsl:variable name="methTitleId" select="generate-id($methTitle)"/>
          
          <html:div class="descr">
            <html:h2>Detailed Description</html:h2>
            <xsl:for-each
              select="
              $descTitle
              /following-sibling::html:*[
                text() != '' 
                and text() != $propText
                and text() != $methText
                and not(
                  generate-id(preceding-sibling::html:h2[1]) = $propTitleId
                  or
                  generate-id(preceding-sibling::html:h2[1]) = $methTitleId
                )
              ]"
              >
              <!-- Selectively rewrite titles so there is only one h2, and the whole description is under the same title. -->
              <xsl:choose>
                <xsl:when test="current()[self::html:h2]">
                  <html:h3>
                    <xsl:attribute name="id" select="current()[@id]"/>
                    <xsl:value-of select="current()"/>
                  </html:h3>
                </xsl:when>
                <xsl:when test="current()[self::html:h3][generate-id(preceding-sibling::html:h2[1]) != $descTitleId]">
                  <html:h4>
                    <xsl:attribute name="id" select="current()[@id]"/>
                    <xsl:value-of select="current()"/>
                  </html:h4>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:copy-of select="current()"></xsl:copy-of>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:for-each>
          </html:div>
        </xsl:if>
    </xsl:variable>
    <xsl:variable name="description" as="element()" select="if(not($isQmlType)) then $content/html:div[@class = 'descr'] else $descriptionRawQml"/>
    <xsl:variable name="siblingAfterDescription" as="element()?"
      select="$description/following-sibling::*[1]"/>
    <xsl:if test="not($description)">
      <xsl:message>WARNING: No description found, while one was expected.</xsl:message>
    </xsl:if>

    <!-- TODO: this was written only for classes and concepts, not QML types! (No $description.) -->
    <xsl:variable name="seeAlso" select="$siblingAfterDescription[self::html:p]" as="element()?"/>
    <xsl:variable name="hasSeeAlso" select="boolean($seeAlso)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterSeeAlso" as="element()?"
      select="
        if ($hasSeeAlso) then
          $siblingAfterDescription/following-sibling::*[1]
        else
          $siblingAfterDescription"/>

    <xsl:variable name="index" as="element()?"
      select="$siblingAfterSeeAlso[self::html:div][@class = 'table']"/>
    <xsl:variable name="hasIndex" select="boolean($index)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterIndex" as="element()?"
      select="
        if ($hasIndex) then
          $siblingAfterSeeAlso/following-sibling::*[1]
        else
          $siblingAfterSeeAlso"/>

    <xsl:variable name="types" as="element()?"
      select="$siblingAfterIndex[self::html:div][@class = 'types']"/>
    <xsl:variable name="hasTypes" select="boolean($types)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterTypes" as="element()?"
      select="
        if ($hasTypes) then
          $siblingAfterIndex/following-sibling::*[1]
        else
          $siblingAfterIndex"/>

    <xsl:variable name="properties" as="element()?"
      select="$siblingAfterTypes[self::html:div][@class = 'prop']"/>
    <xsl:variable name="hasProperties" select="boolean($properties)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterProperties" as="element()?"
      select="
        if ($hasProperties) then
          $siblingAfterTypes/following-sibling::*[1]
        else
          $siblingAfterTypes"/>

    <xsl:variable name="funcs" as="element(html:div)?"
      select="$siblingAfterProperties[self::html:div][@class = 'func']"/>
    <xsl:variable name="hasFuncs" select="boolean($funcs)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterFuncs" as="element()?"
      select="
        if ($hasFuncs) then
          $siblingAfterProperties/following-sibling::*[1]
        else
          $siblingAfterProperties"/>

    <xsl:variable name="nonmems" as="element(html:div)?"
      select="$siblingAfterFuncs[self::html:div][@class = 'relnonmem']"/>
    <xsl:variable name="hasNonmems" select="boolean($nonmems)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterNonmems" as="element()?"
      select="
        if ($hasNonmems) then
          $siblingAfterFuncs/following-sibling::*[1]
        else
          $siblingAfterFuncs"/>

    <!-- Error checks. -->
    <xsl:variable name="isExamplePage" as="xs:boolean"
      select="$hasSubtitle and ends-with($title, 'Example File')"/>
    <xsl:variable name="isBareExamplePage" as="xs:boolean"
      select="
        $isExamplePage
          and count($description/child::*) = 2
          and $description/child::*[1][self::html:a][@name = 'details']
          and $description/child::*[2][self::html:pre]"
    />
    <!-- 
      TODO: is the distinction with $isExamplePage required? It seems only pages with source code 
      (and not the "main" page for each example) have a nonempty subtitle and require this. 
    -->

    <xsl:if test="$hasSubtitle and not($isBareExamplePage)">
      <!-- Ignore the subtitles for example pages, with only source code (it is redundant with the title). -->
      <xsl:message terminate="no">WARNING: Found a subtitle; not implemented</xsl:message>
    </xsl:if>
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
    <xsl:if test="not($isClass) and boolean($properties)">
      <xsl:message terminate="no">WARNING: A concept has properties.</xsl:message>
    </xsl:if>
    <xsl:if test="not($isClass) and boolean($funcs)">
      <xsl:message terminate="no">WARNING: A concept has functions.</xsl:message>
    </xsl:if>

    <!-- Do the same with linked documents. -->
    <xsl:variable name="linkedDocumentsList"
      select="$content/html:ul[preceding::html:div[@class = 'table']][1]" as="element()?"/>
    <xsl:variable name="hasLinkedDocumentsList" select="boolean($linkedDocumentsList)"
      as="xs:boolean"/>
    <xsl:variable name="linkedDocumentsFileNames" as="xs:string*"
      select="replace($linkedDocumentsList/html:li/html:a[not(ends-with(@href, '-members.html'))]/@href, '.html', '.xml')"/>
    <xsl:variable name="linkedDocuments" as="element()*">
      <xsl:choose>
        <xsl:when test="$linkedDocumentsFileNames">
          <xsl:for-each select="$linkedDocumentsFileNames">
            <entry>
              <xsl:attribute name="file-name">
                <xsl:value-of select="."/>
              </xsl:attribute>
              <xsl:attribute name="type">
                <xsl:value-of select="substring-after(replace(., '.xml', ''), '-')"/>
              </xsl:attribute>
              <xsl:copy-of select="document(resolve-uri(., base-uri($content)))"/>
            </entry>
          </xsl:for-each>
        </xsl:when>
        <xsl:otherwise/>
      </xsl:choose>
    </xsl:variable>
    <xsl:if test="count($linkedDocuments) > 1">
      <xsl:message>WARNING: More than one linked document. Unsupported.</xsl:message>
    </xsl:if>
    <xsl:variable name="obsolete"
      select="
        $linkedDocuments[1]
        /html:html
        /html:body
        /html:div[@class = 'header']
        /html:div[@class = 'main']
        /html:div[@class = 'content']
        /html:div[@class = 'line']
        /html:div[@class = 'content mainContent']"/>
    <xsl:variable name="hasObsolete" select="boolean($obsolete)" as="xs:boolean"/>

    <xsl:variable name="obsolete_types_title" as="element(html:h2)?"
      select="$obsolete/html:h2[text() = 'Member Type Documentation']"/>
    <xsl:variable name="obsolete_types" as="element(html:div)?">
      <html:div class="types">
        <xsl:copy-of select="$obsolete_types_title"/>
        <xsl:copy-of
          select="$obsolete_types_title/following-sibling::node()[preceding-sibling::html:h2 = $obsolete_types_title]"
        />
      </html:div>
    </xsl:variable>
    <xsl:variable name="obsolete_hasTypes" select="boolean($obsolete_types_title)" as="xs:boolean"/>

    <xsl:variable name="obsolete_properties_title" as="element(html:h2)?"
      select="$obsolete/html:h2[text() = 'Property Documentation']"/>
    <xsl:variable name="obsolete_properties" as="element(html:div)?">
      <html:div class="prop">
        <xsl:copy-of select="$obsolete_properties_title"/>
        <xsl:copy-of
          select="$obsolete_properties_title/following-sibling::node()[preceding-sibling::html:h2 = $obsolete_properties_title]"
        />
      </html:div>
    </xsl:variable>
    <xsl:variable name="obsolete_hasProperties" select="boolean($obsolete_properties_title)"
      as="xs:boolean"/>

    <xsl:variable name="obsolete_funcs_title" as="element(html:h2)?"
      select="$obsolete/html:h2[text() = 'Member Function Documentation']"/>
    <xsl:variable name="obsolete_funcs" as="element(html:div)?">
      <html:div class="func">
        <xsl:copy-of select="$obsolete_funcs_title"/>
        <xsl:copy-of
          select="$obsolete_funcs_title/following-sibling::node()[preceding-sibling::html:h2 = $obsolete_funcs_title]"
        />
      </html:div>
    </xsl:variable>
    <xsl:variable name="obsolete_hasFuncs" select="boolean($obsolete_funcs_title)" as="xs:boolean"/>

    <xsl:variable name="obsolete_nonmems_title" as="element(html:h2)?"
      select="$obsolete/html:h2[text() = 'Related Non-Members']"/>
    <xsl:variable name="obsolete_nonmems" as="element(html:div)?">
      <html:div class="types">
        <xsl:copy-of select="$obsolete_nonmems_title"/>
        <xsl:copy-of
          select="$obsolete_nonmems_title/following-sibling::node()[preceding-sibling::html:h2 = $obsolete_nonmems_title]"
        />
      </html:div>
    </xsl:variable>
    <xsl:variable name="obsolete_hasNonmems" select="boolean($obsolete_nonmems_title)"
      as="xs:boolean"/>

    <!-- Actually output something. -->
    <db:article version="5.0">
      <xsl:attribute name="xml:lang">
        <xsl:value-of select="./@lang"/>
      </xsl:attribute>

      <db:title>
        <xsl:value-of select="$title"/>
      </db:title>

      <!-- Output the list of methods of the class if any, then its related non-member functions. -->
      <xsl:if test="$hasTypes">
        <xsl:message>WARNING: No summary output for types.</xsl:message>
      </xsl:if>
      <xsl:if test="$obsolete_hasTypes">
        <xsl:message>WARNING: No summary output for types, even if obsolete.</xsl:message>
      </xsl:if>
      
      <xsl:if test="$isClass">
        <xsl:call-template name="classListing">
          <xsl:with-param name="functions" select="$funcs"/>
          <xsl:with-param name="properties" select="$properties"/>
          <xsl:with-param name="className" select="$className"/>
          
          <xsl:with-param name="header" select="$prologueHeader"/>
          <xsl:with-param name="qmake" select="$prologueQmake"/>
          <xsl:with-param name="inherits" select="$prologueInherits"/>
          <xsl:with-param name="inheritedBy" select="$prologueInheritedBy"/>
          <xsl:with-param name="since" select="$prologueSince"/>
          
          <xsl:with-param name="obsoleteFunctions" select="$obsolete_funcs"/>
          <xsl:with-param name="obsoleteProperties" select="$obsolete_properties"/>
        </xsl:call-template>
        
        <xsl:if test="$hasNonmems">
          <xsl:call-template name="functionListing">
            <xsl:with-param name="data" select="$nonmems"/>
            <xsl:with-param name="className" select="$className"/>
            <xsl:with-param name="obsoleteData" select="$obsolete_nonmems"/>
          </xsl:call-template>
        </xsl:if>
      </xsl:if>
      
      <xsl:if test="$isQmlType">
        <xsl:call-template name="qmlTypeListing">
          <xsl:with-param name="qmlTypeName" select="$qmlTypeName"/>
          <xsl:with-param name="import" select="$prologueImport"/>
          <xsl:with-param name="instantiates" select="$prologueInstantiates"/>
          <xsl:with-param name="inherits" select="$prologueInherits"/>
          <xsl:with-param name="inheritedBy" select="$prologueInheritedBy"/>
          <xsl:with-param name="since" select="$prologueSince"/>
        </xsl:call-template>
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

      <xsl:if test="$hasObsolete">
        <db:section>
          <db:title>
            <xsl:text>Obsolete Members</xsl:text>
          </db:title>

          <xsl:if test="$obsolete_hasTypes">
            <xsl:call-template name="content_types">
              <xsl:with-param name="data" select="$obsolete_types"/>
            </xsl:call-template>
          </xsl:if>

          <xsl:if test="$obsolete_hasFuncs">
            <xsl:call-template name="content_class">
              <xsl:with-param name="data" select="$obsolete_funcs"/>
            </xsl:call-template>
          </xsl:if>

          <xsl:if test="$obsolete_hasNonmems">
            <xsl:call-template name="content_nonmems">
              <xsl:with-param name="data" select="$obsolete_nonmems"/>
            </xsl:call-template>
          </xsl:if>
        </db:section>
      </xsl:if>
    </db:article>
  </xsl:template>

  <!-- Utility templates, to be used everywhere. -->
  <xsl:template name="content_title">
    <xsl:variable name="content">
      <xsl:apply-templates mode="content_title_hidden" select="."/>
    </xsl:variable>
    <db:title>
      <!-- Normalise spaces and line feeds in titles. -->
      <xsl:value-of
        select="replace(replace(replace($content, ' \(', '('), '(\r|\n|\r\n|(  ))+', ' '), '  ', ' ')"
      />
    </db:title>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="text()">
    <xsl:value-of select="."/>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:code">
    <xsl:value-of select="./text()"/>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:a[@name]"/>
  <xsl:template mode="content_title_hidden" match="html:a[@href]">
    <xsl:value-of select="./text()"/>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:i">
    <xsl:apply-templates mode="content_title_hidden"/>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:span">
    <xsl:apply-templates mode="content_title_hidden"/>
    <xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:h3">
    <xsl:apply-templates mode="content_title_hidden"/>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:br">
    <xsl:message terminate="no">WARNING: Line feed in title; this is not handled!</xsl:message>
  </xsl:template>

  <xsl:template name="content_seealso">
    <xsl:param name="seeAlso" as="element(html:p)"/>

    <db:section>
      <db:title>See Also</db:title>
      <db:simplelist>
        <xsl:for-each select="$seeAlso/html:a">
          <db:member>
            <xsl:apply-templates mode="content_paragraph" select="."/>
          </db:member>
        </xsl:for-each>
      </db:simplelist>
    </db:section>
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
    <xsl:param name="className" as="xs:string"/>
    <xsl:param name="functions" as="element(html:div)"/>
    <xsl:param name="properties" as="element(html:div)?"/>
    
    <xsl:param name="obsoleteFunctions" as="element(html:div)?"/>
    <xsl:param name="obsoleteProperties" as="element(html:div)?"/>

    <xsl:param name="header" as="element()?"/>
    <xsl:param name="qmake" as="element()?"/>
    <xsl:param name="inherits" as="element()?"/>
    <xsl:param name="inheritedBy" as="element()?"/>
    <xsl:param name="since" as="element()?"/>

    <db:classsynopsis>
      <db:ooclass>
        <db:classname>
          <xsl:value-of select="$className"/>
        </db:classname>
      </db:ooclass>

      <xsl:if test="$header">
        <db:classsynopsisinfo role="header">
          <xsl:value-of select="$header"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$qmake">
        <db:classsynopsisinfo role="qmake">
          <xsl:value-of select="$qmake"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$inherits">
        <db:classsynopsisinfo role="inherits">
          <xsl:value-of select="$inherits/html:a/text()"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$inheritedBy">
        <db:classsynopsisinfo role="inheritedBy">
          <xsl:value-of select="$inheritedBy/html:p/html:a/text()"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$since">
        <db:classsynopsisinfo role="since">
          <xsl:value-of select="$since"/>
        </db:classsynopsisinfo>
      </xsl:if>
      
      <!-- Deal with properties as fields. -->
      <xsl:apply-templates mode="propertiesListing" select="$properties/html:h3"/>
      <xsl:if test="boolean($obsoleteProperties)">
        <xsl:apply-templates mode="propertiesListing" select="$obsoleteProperties/html:h3">
          <xsl:with-param name="obsolete">
            <xsl:value-of select="true()"/>
          </xsl:with-param>
        </xsl:apply-templates>
      </xsl:if>

      <!-- Deal with functions. -->
      <xsl:apply-templates mode="classListing" select="$functions/html:h3">
        <xsl:with-param name="className" select="$className"/>
      </xsl:apply-templates>
      <xsl:if test="boolean($obsoleteFunctions)">
        <xsl:apply-templates mode="classListing" select="$obsoleteFunctions/html:h3">
          <xsl:with-param name="className" select="$className"/>
          <xsl:with-param name="obsolete">
            <xsl:value-of select="true()"/>
          </xsl:with-param>
        </xsl:apply-templates>
      </xsl:if>
    </db:classsynopsis>
  </xsl:template>
  
  <xsl:template mode="propertiesListing" match="text()"/>
  <xsl:template mode="propertiesListing" match="html:h3[@class = 'fn']">
    <xsl:param name="obsolete" as="xs:boolean" select="false()"/>
    <xsl:variable name="anchor" select="./@id"/>
    
    <db:fieldsynopsis>
      <xsl:if test="$obsolete">
        <db:modifier><xsl:text>(obsolete)</xsl:text></db:modifier>
      </xsl:if>
      
      <xsl:call-template name="classListing_methodBody_analyseType">
        <xsl:with-param name="typeNodes" select="./html:span[@class = 'type']"></xsl:with-param>
      </xsl:call-template>
      <db:varname>
        <xsl:value-of select="./html:span[@class = 'name']/text()"/>
      </db:varname>
    </db:fieldsynopsis>
  </xsl:template>
  
  <xsl:template mode="classListing" match="text()"/>
  <xsl:template mode="classListing" match="html:h3[@class = 'fn']">
    <xsl:param name="className" as="xs:string"/>
    <xsl:param name="obsolete" as="xs:boolean" select="false()"/>

    <!-- Possible anchors: for constructors, Class, Class-2; for destructors, dtor.Class -->
    <xsl:variable name="functionAnchor" select="./@id"/>
    <xsl:variable name="isCtor" select="starts-with($functionAnchor, $className)"/>
    <xsl:variable name="isDtor" select="starts-with($functionAnchor, 'dtor.')"/>
    <xsl:variable name="isFct" select="not($isCtor or $isDtor)"/>

    <xsl:choose>
      <xsl:when test="$isCtor">
        <db:constructorsynopsis>
          <xsl:attribute name="xlink:href" select="concat('#', $functionAnchor)"/>
          <xsl:call-template name="classListing_methodBody">
            <xsl:with-param name="obsolete" select="$obsolete"/>
          </xsl:call-template>
        </db:constructorsynopsis>
      </xsl:when>
      <xsl:when test="$isDtor">
        <db:destructorsynopsis>
          <xsl:attribute name="xlink:href" select="$functionAnchor"/>
          <xsl:call-template name="classListing_methodBody">
            <xsl:with-param name="obsolete" select="$obsolete"/>
          </xsl:call-template>
        </db:destructorsynopsis>
      </xsl:when>
      <xsl:when test="$isFct">
        <db:methodsynopsis>
          <xsl:attribute name="xlink:href" select="$functionAnchor"/>
          <xsl:call-template name="classListing_methodBody">
            <xsl:with-param name="obsolete" select="$obsolete"/>
          </xsl:call-template>
        </db:methodsynopsis>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="classListing_methodBody">
    <xsl:param name="obsolete" as="xs:boolean" select="false()"/>

    <xsl:variable name="titleNode" select="."/>
    <xsl:variable name="functionName" select="./html:span[@class = 'name']"/>
    <xsl:variable name="returnTypes"
      select="$functionName/preceding-sibling::html:span[@class = 'type']"/>
    <xsl:variable name="isStatic" as="xs:boolean"
      select="boolean($returnTypes/preceding-sibling::html:code[normalize-space(text()) = '[static]'])"/>

    <xsl:if test="$obsolete">
      <db:modifier>(obsolete)</db:modifier>
    </xsl:if>

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
      <!-- Either the function has zero arguments or at least one. -->
      <xsl:when test="starts-with($textAfterName, '()')">
        <db:void/>
      </xsl:when>
      <!-- 
        This code mostly works, but miserably fails on more complicated cases. E.g.:
            QPixmap QWidget::grab(const QRect & rectangle = QRect( QPoint( 0, 0 ), QSize( -1, -1 ) ))
      -->
      <xsl:when test="false()">
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

          <xsl:variable name="names" select="$type/following-sibling::html:i"/>
          <xsl:variable name="afterName" select="$names[1]/following-sibling::text()[1]"/>
          <xsl:variable name="afterNameBeforeNext"
            select="
              if (not(contains($afterName, ','))) then
                substring-before($afterName, ')')
              else
                substring-before($afterName, ',')"/>
          <xsl:variable name="hasInitialiser" select="contains($afterName, '=')" as="xs:boolean"/>

          <db:methodparam>
            <!-- methodparam attributes. No repeating argument until Qt uses C's stdarg. -->
            <xsl:choose>
              <xsl:when test="$hasInitialiser">
                <xsl:attribute name="choice">
                  <xsl:text>opt</xsl:text>
                </xsl:attribute>
              </xsl:when>
              <xsl:otherwise>
                <xsl:attribute name="choice">
                  <xsl:text>req</xsl:text>
                </xsl:attribute>
              </xsl:otherwise>
            </xsl:choose>

            <xsl:attribute name="rep">
              <xsl:text>norepeat</xsl:text>
            </xsl:attribute>

            <!-- Maybe this parameter is const. -->
            <xsl:if test="normalize-space($textAfterName) = '(const'">
              <db:modifier>const</db:modifier>
            </xsl:if>

            <!-- Output the type. -->
            <xsl:call-template name="classListing_methodBody_analyseType">
              <xsl:with-param name="typeNodes" select="$type"/>
            </xsl:call-template>

            <!-- Then the name. -->
            <db:parameter>
              <xsl:value-of select="normalize-space($names[1])"/>
            </db:parameter>

            <!-- Eventually an initialiser. -->
            <xsl:if test="$hasInitialiser">
              <db:initializer>
                <xsl:value-of select="normalize-space(substring-after($afterNameBeforeNext, '='))"/>
              </db:initializer>
            </xsl:if>
          </db:methodparam>
        </xsl:for-each>
      </xsl:when>
      <!-- Output everything as a raw string, a post-processor will deal with it. -->
      <xsl:otherwise>
        <db:void role="parameters"/>
        <db:exceptionname role="parameters">
          <xsl:value-of select="$functionName/following-sibling::node()"/>
        </db:exceptionname>
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

    <!-- 
    Type can be composed of one or multiple nodes: 
      -   <html:span class="type">int</html:span>
      -   <html:span class="type"><html:a href="qcolor.html#QRgb-typedef">QRgb</html:a></html:span>
    Multiple nodes are linked to templates: 
      -   <html:span class="type">
            <html:a href="qtcore/qlist.html">QList</html:a>
          </html:span>
          &lt;
          <html:span class="type">
            <html:a href="qlowenergydescriptor.html">QLowEnergyDescriptor</html:a>
          </html:span>
          &gt;
    -->
    <xsl:choose>
      <xsl:when test="count($typeNodes) = 1">
        <xsl:variable name="node" select="$typeNodes[1]"/>
        <xsl:choose>
          <xsl:when test="$node/html:a">
            <db:type>
              <xsl:attribute name="xlink:href">
                <xsl:value-of select="$node/html:a/@href"/>
              </xsl:attribute>

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
    <xsl:param name="obsoleteData" as="element(html:div)?"/>

    <xsl:apply-templates mode="functionListing" select="$data/html:h3">
      <xsl:with-param name="className" select="$className"/>
    </xsl:apply-templates>
    <xsl:if test="boolean($obsoleteData)">
      <xsl:apply-templates mode="functionListing" select="$obsoleteData/html:h3">
        <xsl:with-param name="className" select="$className"/>
        <xsl:with-param name="obsolete" select="true()"/>
      </xsl:apply-templates>
    </xsl:if>
  </xsl:template>
  <xsl:template mode="functionListing" match="text()"/>
  <xsl:template mode="functionListing" match="html:h3[@class = 'fn'][ends-with(@id, '-typedef')]">
    <xsl:message terminate="no">WARNING: No summary output for typedefs. </xsl:message>
  </xsl:template>
  <xsl:template mode="functionListing"
    match="html:h3[@class = 'fn'][not(ends-with(@id, '-typedef'))]">
    <xsl:param name="obsolete" as="xs:boolean" select="false()"/>
    <db:funcsynopsis>
      <xsl:attribute name="xlink:href" select="concat('#', ./@id)"/>

      <db:funcprototype>
        <xsl:variable name="titleNode" select="."/>
        <xsl:variable name="functionName" select="./html:span[@class = 'name']"/>
        <xsl:variable name="returnTypes"
          select="$functionName/preceding-sibling::html:span[@class = 'type']"/>
        <xsl:variable name="isStatic" as="xs:boolean"
          select="boolean($returnTypes/preceding-sibling::html:code[normalize-space(text()) = '[static]'])"/>

        <xsl:if test="$obsolete">
          <db:modifier>(obsolete)</db:modifier>
        </xsl:if>

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

              <db:paramdef>
                <!-- Maybe this parameter is const. -->
                <xsl:if test="normalize-space($textAfterName) = '(const'">
                  <db:modifier>const</db:modifier>
                </xsl:if>

                <!-- Output the type. -->
                <xsl:call-template name="classListing_methodBody_analyseType">
                  <xsl:with-param name="typeNodes" select="$types"/>
                </xsl:call-template>

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

  <!-- Handle QML types: type structure. -->
  <xsl:template name="qmlTypeListing">
    <xsl:param name="qmlTypeName" as="xs:string"/>
    
    <xsl:param name="import" as="element()?"/>
    <xsl:param name="instantiates" as="element()?"/>
    <xsl:param name="inherits" as="element()?"/>
    <xsl:param name="inheritedBy" as="element()?"/>
    <xsl:param name="since" as="element()?"/>
    
    <db:classsynopsis>
      <db:ooclass>
        <db:classname>
          <xsl:value-of select="$qmlTypeName"/>
        </db:classname>
      </db:ooclass>
      
      <xsl:if test="$import">
        <db:classsynopsisinfo role="import">
          <xsl:value-of select="$import"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$instantiates">
        <db:classsynopsisinfo role="instantiates">
          <xsl:value-of select="$instantiates"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$inherits">
        <db:classsynopsisinfo role="inherits">
          <xsl:value-of select="$inherits/html:a/text()"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$inheritedBy">
        <db:classsynopsisinfo role="inheritedBy">
          <xsl:value-of select="$inheritedBy/html:p/html:a/text()"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$since">
        <db:classsynopsisinfo role="since">
          <xsl:value-of select="$since"/>
        </db:classsynopsisinfo>
      </xsl:if>
    </db:classsynopsis>
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
      <xsl:call-template name="content_title"/>

      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_types" match="html:h3[@class = 'flags']">
    <xsl:variable name="functionAnchor" select="./@id"/>

    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>
      <xsl:call-template name="content_title"/>

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
      <xsl:call-template name="content_title"/>

      <xsl:choose>
        <xsl:when test="./following-sibling::*[1][not(self::html:h3)]">
          <xsl:call-template name="content_class_content">
            <xsl:with-param name="node" select="./following-sibling::*[1]"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:message>WARNING: This function has no documentation.</xsl:message>
          <db:para/>
        </xsl:otherwise>
      </xsl:choose>
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
      <xsl:call-template name="content_title"/>

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
      <xsl:call-template name="content_title"/>

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
    <!-- Normally, these should already be in xml:id. -->
  </xsl:template>
  <xsl:template mode="content" match="html:p">
    <!-- 
      A paragraph may hold a single image (treat it accordingly), or be an admonition, 
      or be a See also list of links, or be a real paragraph. 
    -->
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
      <xsl:when test="./html:b and count(./html:b) = 1">
        <xsl:choose>
          <xsl:when test="starts-with(./html:b/text(), 'Note')">
            <db:note>
              <db:para>
                <xsl:apply-templates mode="content_paragraph">
                  <xsl:with-param name="forgetNotes" select="true()"/>
                </xsl:apply-templates>
              </db:para>
            </db:note>
          </xsl:when>
          <xsl:when test="starts-with(./html:b/text(), 'See also')">
            <xsl:call-template name="content_seealso">
              <xsl:with-param name="seeAlso" select="."/>
            </xsl:call-template>
          </xsl:when>
        </xsl:choose>
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
  <xsl:template mode="content" match="html:blockquote">
    <!-- Blockquotes are barely used in the documentation. -->
    <xsl:choose>
      <xsl:when test="count(child::*) = 1 and child::html:p and html:p/count(child::*) = 1 and html:p/child::html:code">
        <db:programlisting>
          <xsl:value-of select="./html:p/html:code/text()"/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Unexpected element: html:blockquote</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content" match="html:h2 | html:h3"/>
  <xsl:template mode="content" match="html:pre">
    <db:programlisting>
      <!-- 
        All codes may have class="cpp", even JavaScript (qtqml-javascript-imports.xml), 
        no sense to read it (even though it's sometimes correct: qtbluetooth-index.xml). 
      -->
      <!-- 
      <xsl:if test=".[@class]">
        <xsl:attribute name="language" select="@class"/>
      </xsl:if>
      -->
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
      <xsl:choose>
        <xsl:when test=".[@class = '1']">
          <xsl:attribute name="numeration">
            <xsl:text>arabic</xsl:text>
          </xsl:attribute>
        </xsl:when>
      </xsl:choose>

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
        <!-- Handle anchors. -->
        <xsl:choose>
          <xsl:when test=".[@id]">
            <xsl:attribute name="xml:id">
              <xsl:value-of select="./@id"/>
            </xsl:attribute>
          </xsl:when>
          <xsl:when test="./preceding-sibling::html:a[@name]">
            <xsl:attribute name="xml:id">
              <xsl:value-of select="./preceding-sibling::html:a[@name][last()]/@name"/>
            </xsl:attribute>
          </xsl:when>
        </xsl:choose>

        <!-- Handle title then subsections. -->
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

        <xsl:if test="$hasSeeAlso">
          <xsl:call-template name="content_seealso">
            <xsl:with-param name="seeAlso" select="$seeAlso"/>
          </xsl:call-template>
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
      <xsl:if test=".[@rowspan]">
        <xsl:attribute name="rowspan">
          <xsl:value-of select="./@rowspan"/>
        </xsl:attribute>
      </xsl:if>

      <xsl:choose>
        <xsl:when test="./child::html:p | ./child::html:pre | ./child::html:ul | ./child::html:ol">
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
      <xsl:when test="starts-with(./text(), 'Q') and not(contains(./text(), ' '))">
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
  <xsl:template mode="content_paragraph" match="html:acronym | html:abbr">
    <db:acronym>
      <db:alt>
        <xsl:value-of select="./@title"/>
      </db:alt>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:acronym>
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
