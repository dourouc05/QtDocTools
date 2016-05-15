<?xml version="1.0" encoding="UTF-8"?>
<!--
  Converts QDoc 5.4's HTML5 (first converted to XML) into DocBook. 
  Hypothesis: tables have <tbody> tags in the input (ensured automatically by Python's html5lib). 
  
  
  
  To check: types and arrays []. 
  How to retrieve base class?
  
  
  
  What about QML signal handlers? Always the same presentation in signals: 
    The corresponding handler is onExited.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xsl xs html" version="2.0">
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>
  
  <xsl:param name="vocabulary" select="'docbook'"/> <!-- 'docbook' for raw DocBook 5.1; 'quickbook' for Boost's variant (TODO). -->
  <xsl:param name="warnVocabularyUnsupportedFeatures" select="false()"/> <!-- Output warnings when some semantics cannot be translated in the chosen vocabulary. -->
  <xsl:param name="warnMissingDocumentation" select="false()"/>

  <!-- <xsl:import-schema schema-location="http://www.docbook.org/xml/5.0/xsd/docbook.xsd"/> -->
  <xsl:import-schema schema-location="../schemas/docbook.xsd" use-when="system-property('xsl:is-schema-aware')='yes'"/>

  <!-- Trick to keep the same stylesheet schema-aware and non-schema-aware. -->
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
  
  <!-- Main function. Output document class. -->
  <xsl:template match="html:html">
    <xsl:variable name="content" select=".//html:div[@class = 'content mainContent']"/>

    <!-- Extract the metadata. -->
    <xsl:variable name="title" select="$content/html:h1[@class = 'title']/text()" as="xs:string"/>
    <xsl:variable name="subtitle" as="xs:string"
      select="string($content/html:span[@class = 'subtitle']/text())"/>
    <xsl:variable name="hasSubtitle" as="xs:boolean" select="not($subtitle = '')"/>

    <xsl:variable name="isClass" as="xs:boolean"
      select="
        ends-with($title, ' Class')
        and count(contains($title, ' ')) = 1"/>
    <xsl:variable name="className">
      <xsl:if test="$isClass">
        <xsl:value-of select="substring-before($title, ' Class')"/>
      </xsl:if>
    </xsl:variable>
    
    <xsl:variable name="isNamespace" as="xs:boolean"
      select="
      ends-with($title, ' Namespace')
      and count(contains($title, ' ')) = 1"/>
    <xsl:variable name="namespaceName">
      <xsl:if test="$isNamespace">
        <xsl:value-of select="substring-before($title, ' Namespace')"/>
      </xsl:if>
    </xsl:variable>
    <xsl:if test="$isNamespace and $warnVocabularyUnsupportedFeatures">
      <xsl:message>WARNING: No summary output for namespaces (actually replaced by classes).</xsl:message>
    </xsl:if>

    <xsl:variable name="isQmlType" as="xs:boolean" select="ends-with($title, ' QML Type')"/>
    <xsl:variable name="qmlTypeName">
      <xsl:if test="$isQmlType">
        <xsl:value-of select="substring-before($title, ' QML Type')"/>
      </xsl:if>
    </xsl:variable>
    
    <xsl:variable name="isConcept" select="not($isClass) and not($isNamespace) and not($isQmlType)" as="xs:boolean"/>

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
    <xsl:variable name="prologueInstantiatedBy" as="element(html:td)?"
      select="$prologueTable/html:td[1][contains(text(), 'Instantiated By')]/following-sibling::html:td"/>
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
      count((boolean($prologueHeader), boolean($prologueQmake), boolean($prologueInstantiates), boolean($prologueInstantiatedBy), boolean($prologueInherits), boolean($prologueInheritedBy), boolean($prologueImport), boolean($prologueSince))[. = true()])"
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
          <xsl:variable name="attachedPropText" select="'Attached Property Documentation'" as="xs:string"/>
          <xsl:variable name="methText" select="'Method Documentation'" as="xs:string"/>
          
          <xsl:variable name="descTitle" select="$content/html:h2[@id = 'details']" as="element()"/>
          <xsl:variable name="descTitleId" select="generate-id($descTitle)"/>
          <xsl:variable name="propTitle" select="$content/html:h2[text() = $propText]" as="element()?"/>
          <xsl:variable name="propTitleId" select="generate-id($propTitle)"/>
          <xsl:variable name="attachedPropTitle" select="$content/html:h2[text() = $attachedPropText]" as="element()?"/>
          <xsl:variable name="attachedPropTitleId" select="generate-id($attachedPropTitle)"/>
          <xsl:variable name="methTitle" select="$content/html:h2[text() = $methText]" as="element()?"/>
          <xsl:variable name="methTitleId" select="generate-id($methTitle)"/>
          
          <html:div class="descr">
            <html:h2>Detailed Description</html:h2>
            <xsl:for-each
              select="
                $descTitle
                /following-sibling::html:*[
                  text() != '' 
                  and text() != $propText
                  and text() != $attachedPropText
                  and text() != $methText
                  and not(
                    generate-id(preceding-sibling::html:h2[1]) = $propTitleId
                    or
                    generate-id(preceding-sibling::html:h2[1]) = $attachedPropTitleId
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
    <xsl:variable name="hasActuallyNoDescription" as="xs:boolean" select="not(boolean(//html:a[@name = 'details']/following-sibling::node()[1]))"/>
    <xsl:variable name="description" as="element()?" select="if(not($isQmlType)) then $content/html:div[@class = 'descr'] else $descriptionRawQml"/>
    <xsl:if test="not($hasActuallyNoDescription) and not($description)">
      <xsl:message>WARNING: Found no description, while there should be one. </xsl:message>
    </xsl:if>
    <xsl:variable name="siblingAfterDescription" as="element()?"
      select="$description/following-sibling::*[1]"/>
    <xsl:if test="$siblingAfterDescription and $isQmlType">
      <xsl:message>WARNING: QML types are not supposed to have siblings after description. Bug in the style sheets!</xsl:message>
    </xsl:if>

    <xsl:variable name="seeAlso" select="$siblingAfterDescription[self::html:p]" as="element()?"/> <!-- For QML types: "see also" handled naturally as a paragraph. -->
    <xsl:variable name="hasSeeAlso" select="boolean($seeAlso)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterSeeAlso" as="element()?"
      select="
        if ($hasSeeAlso) then
          $siblingAfterDescription/following-sibling::*[1]
        else
          $siblingAfterDescription"/>
    
    <xsl:variable name="index" as="element()?"
      select="$siblingAfterSeeAlso[self::html:div][@class = 'table'][html:table[@class = 'annotated']]"/> <!-- For pages that contain only an index, like accessibility -->
    <xsl:variable name="hasIndex" select="boolean($index)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterIndex" as="element()?"
      select="
        if ($hasIndex) then
          $siblingAfterSeeAlso/following-sibling::*[1]
        else
          $siblingAfterSeeAlso"/>
    <xsl:if test="$isQmlType and //html:*[self::html:div][@class = 'table'][html:table[@class = 'annotated']]">
      <xsl:message>WARNING: QML type seems to have an index page; not implemented. </xsl:message>
      <!-- In this case, would need to find a way back in the page (cannot use the $sibling variables). -->
    </xsl:if>
    
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
    
    <xsl:variable name="macros" as="element(html:div)?"
      select="$siblingAfterNonmems[self::html:div][@class = 'macros']"/>
    <xsl:variable name="hasMacros" select="boolean($macros)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterMacros" as="element()?"
      select="
        if ($hasMacros) then
          $siblingAfterNonmems/following-sibling::*[1]
        else
          $siblingAfterNonmems"/>
    
    <xsl:variable name="vars" as="element(html:div)?"
      select="$siblingAfterMacros[self::html:div][@class = 'vars']"/>
    <xsl:variable name="hasVars" select="boolean($vars)" as="xs:boolean"/>
    <xsl:variable name="siblingAfterVars" as="element()?"
      select="
        if ($hasVars) then
          $siblingAfterMacros/following-sibling::*[1]
        else
          $siblingAfterMacros"/>
    
    <xsl:variable name="legitVarsFollower" as="xs:boolean" select="boolean($siblingAfterVars[self::html:p and contains(@class, 'navi')])"/>
    
    <xsl:variable name="qmlPropsTitleText" select="'Property Documentation'" as="xs:string"/>
    <xsl:variable name="qmlPropsTitle" select="$content/html:h2[text() = $qmlPropsTitleText]" as="element()?"/>
    <xsl:variable name="qmlPropsTitleId" select="generate-id($qmlPropsTitle)"/>
    <xsl:variable name="hasQmlProps" select="$isQmlType and boolean($qmlPropsTitle)" as="xs:boolean"/>
    <xsl:variable name="qmlProps" as="element(html:div)?">
      <xsl:if test="$isQmlType and $hasQmlProps">
        <html:div class="qml-props">
          <xsl:for-each
            select="
              $qmlPropsTitle
                /following-sibling::html:div[
                  @class = 'qmlitem' and generate-id(preceding-sibling::html:h2[1]) = $qmlPropsTitleId
                ]"
            >
            <xsl:copy-of select="current()"></xsl:copy-of>
          </xsl:for-each>
        </html:div>
      </xsl:if>
    </xsl:variable>
    
    <xsl:variable name="qmlAttachedPropsTitleText" select="'Attached Property Documentation'" as="xs:string"/>
    <xsl:variable name="qmlAttachedPropsTitle" select="$content/html:h2[text() = $qmlAttachedPropsTitleText]" as="element()?"/>
    <xsl:variable name="qmlAttachedPropsTitleId" select="generate-id($qmlAttachedPropsTitle)"/>
    <xsl:variable name="hasQmlAttachedProps" select="$isQmlType and boolean($qmlAttachedPropsTitle)" as="xs:boolean"/>
    <xsl:variable name="qmlAttachedProps" as="element(html:div)?">
      <xsl:if test="$isQmlType and $hasQmlAttachedProps">
        <html:div class="qml-attached-props">
          <xsl:for-each
            select="
            $qmlAttachedPropsTitle
            /following-sibling::html:div[
            @class = 'qmlitem' and generate-id(preceding-sibling::html:h2[1]) = $qmlAttachedPropsTitleId
            ]"
            >
            <xsl:copy-of select="current()"></xsl:copy-of>
          </xsl:for-each>
        </html:div>
      </xsl:if>
    </xsl:variable>
    
    <xsl:variable name="qmlMethsTitleText" select="'Method Documentation'" as="xs:string"/>
    <xsl:variable name="qmlMethsTitle" select="$content/html:h2[text() = $qmlMethsTitleText]" as="element()?"/>
    <xsl:variable name="qmlMethsTitleId" select="generate-id($qmlMethsTitle)"/>
    <xsl:variable name="hasQmlMeths" select="$isQmlType and boolean($qmlMethsTitle)" as="xs:boolean"/>
    <xsl:variable name="qmlMeths" as="element(html:div)?">
      <xsl:if test="$isQmlType and $hasQmlMeths">
        <html:div class="qml-meths">
          <xsl:for-each
            select="
            $qmlMethsTitle
            /following-sibling::html:div[
            @class = 'qmlitem' and generate-id(preceding-sibling::html:h2[1]) = $qmlMethsTitleId
            ]"
            >
            <xsl:copy-of select="current()"></xsl:copy-of>
          </xsl:for-each>
        </html:div>
      </xsl:if>
    </xsl:variable>
    
    <xsl:variable name="qmlSignalsTitleText" select="'Signal Documentation'" as="xs:string"/>
    <xsl:variable name="qmlSignalsTitle" select="$content/html:h2[text() = $qmlSignalsTitleText]" as="element()?"/>
    <xsl:variable name="qmlSignalsTitleId" select="generate-id($qmlSignalsTitle)"/>
    <xsl:variable name="hasQmlSignals" select="$isQmlType and boolean($qmlSignalsTitle)" as="xs:boolean"/>
    <xsl:variable name="qmlSignals" as="element(html:div)?">
      <xsl:if test="$isQmlType and $hasQmlSignals">
        <html:div class="qml-signals">
          <xsl:for-each
            select="
              $qmlSignalsTitle
              /following-sibling::html:div[
                @class = 'qmlitem' and generate-id(preceding-sibling::html:h2[1]) = $qmlSignalsTitleId
              ]"
            >
            <xsl:copy-of select="current()"></xsl:copy-of>
          </xsl:for-each>
        </html:div>
      </xsl:if>
    </xsl:variable>

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
    <xsl:if test="boolean($siblingAfterVars) and not($legitVarsFollower)">
      <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of
        select="name($siblingAfterVars)"/>
      </xsl:message>
    </xsl:if>
    <!-- A C++ class can perfectly have no functions! Example: QQuickItem::ItemChangedData. -->
    <xsl:if test="$isClass and boolean($hasQmlProps)">
      <xsl:message terminate="no">WARNING: A C++ class has QML properties.</xsl:message>
    </xsl:if>
    <xsl:if test="$isClass and boolean($hasQmlAttachedProps)">
      <xsl:message terminate="no">WARNING: A C++ class has QML attached properties.</xsl:message>
    </xsl:if>
    <xsl:if test="$isClass and boolean($hasQmlMeths)">
      <xsl:message terminate="no">WARNING: A C++ class has QML methods.</xsl:message>
    </xsl:if>
    <xsl:if test="$isConcept and boolean($types)">
      <xsl:message terminate="no">WARNING: A concept has C++ types.</xsl:message>
    </xsl:if>
    <xsl:if test="$isConcept and boolean($funcs)">
      <xsl:message terminate="no">WARNING: A concept has C++ functions.</xsl:message>
    </xsl:if>
    <xsl:if test="$isConcept and boolean($properties)">
      <xsl:message terminate="no">WARNING: A concept has C++ properties.</xsl:message>
    </xsl:if>
    <xsl:if test="$isConcept and boolean($funcs)">
      <xsl:message terminate="no">WARNING: A concept has C++ functions.</xsl:message>
    </xsl:if>
    <xsl:if test="$isQmlType and boolean($funcs)">
      <xsl:message terminate="no">WARNING: A QML type has C++ functions.</xsl:message>
    </xsl:if>
    <xsl:if test="$isQmlType and boolean($types)">
      <xsl:message terminate="no">WARNING: A QML type has C++ types.</xsl:message>
    </xsl:if>
    <xsl:if test="$isQmlType and boolean($properties)">
      <xsl:message terminate="no">WARNING: A QML type has C++ properties.</xsl:message>
    </xsl:if>
    <xsl:if test="$isQmlType and boolean($vars)">
      <xsl:message terminate="no">WARNING: A QML type has C++ variables.</xsl:message>
    </xsl:if>

    <!-- Do the same with linked documents (only for C++ classes). -->
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
    
    <xsl:variable name="obsolete_memfuncs_title" as="element(html:h2)?"
      select="$obsolete/html:h2[text() = 'Member Function Documentation']"/>
    <xsl:variable name="obsolete_memfuncs" as="element(html:div)?">
      <html:div class="func">
        <xsl:copy-of select="$obsolete_memfuncs_title"/>
        <xsl:copy-of
          select="$obsolete_memfuncs_title/following-sibling::node()[preceding-sibling::html:h2 = $obsolete_memfuncs_title]"
        />
      </html:div>
    </xsl:variable>
    <xsl:variable name="obsolete_hasMemFuncs" select="boolean($obsolete_memfuncs_title)" as="xs:boolean"/>
    
    <xsl:variable name="obsolete_funcs_title" as="element(html:h2)?"
      select="$obsolete/html:h2[text() = 'Function Documentation']"/>
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
      <xsl:if test="$hasTypes and $warnVocabularyUnsupportedFeatures">
        <xsl:message>WARNING: No summary output for types.</xsl:message>
      </xsl:if>
      <xsl:if test="$obsolete_hasTypes and $warnVocabularyUnsupportedFeatures">
        <xsl:message>WARNING: No summary output for types, even if obsolete.</xsl:message>
      </xsl:if>
      
      <xsl:if test="$isClass">
        <xsl:call-template name="classListing">
          <xsl:with-param name="className" select="$className"/>
          <xsl:with-param name="functions" select="$funcs"/>
          <xsl:with-param name="properties" select="$properties"/>
          <xsl:with-param name="vars" select="$vars"/>
          
          <xsl:with-param name="header" select="$prologueHeader"/>
          <xsl:with-param name="qmake" select="$prologueQmake"/>
          <xsl:with-param name="inherits" select="$prologueInherits"/>
          <xsl:with-param name="inheritedBy" select="$prologueInheritedBy"/>
          <xsl:with-param name="since" select="$prologueSince"/>
          
          <xsl:with-param name="obsoleteMemberFunctions" select="$obsolete_memfuncs"/>
          <xsl:with-param name="obsoleteProperties" select="$obsolete_properties"/>
        </xsl:call-template>
        
        <xsl:if test="$hasMacros">
          <xsl:call-template name="macroListing">
            <xsl:with-param name="data" select="$macros"/>
            <xsl:with-param name="className" select="$className"/>
          </xsl:call-template>
        </xsl:if>
        
        <xsl:if test="$hasNonmems">
          <xsl:call-template name="functionListing">
            <xsl:with-param name="data" select="$nonmems"/>
            <xsl:with-param name="className" select="$className"/>
            <xsl:with-param name="obsoleteData" select="$obsolete_nonmems"/>
          </xsl:call-template>
        </xsl:if>
      </xsl:if>
      
      <xsl:if test="$isNamespace">
        <xsl:call-template name="classListing">
          <xsl:with-param name="className" select="$namespaceName"/>
          <xsl:with-param name="isNamespace" select="true()"/>
          
          <xsl:with-param name="functions" select="$funcs"/>
          <xsl:with-param name="properties" select="$properties"/>
          <xsl:with-param name="vars" select="$vars"/>
          
          <xsl:with-param name="header" select="$prologueHeader"/>
          <xsl:with-param name="qmake" select="$prologueQmake"/>
          <xsl:with-param name="inherits" select="$prologueInherits"/>
          <xsl:with-param name="inheritedBy" select="$prologueInheritedBy"/>
          <xsl:with-param name="since" select="$prologueSince"/>
          
          <xsl:with-param name="obsoleteMemberFunctions" select="$obsolete_memfuncs"/>
          <xsl:with-param name="obsoleteProperties" select="$obsolete_properties"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$isQmlType">
        <xsl:call-template name="qmlTypeListing">
          <xsl:with-param name="qmlTypeName" select="$qmlTypeName"/>
          
          <xsl:with-param name="import" select="$prologueImport"/>
          <xsl:with-param name="instantiates" select="$prologueInstantiates"/>
          <xsl:with-param name="instantiatedBy" select="$prologueInstantiatedBy"/>
          <xsl:with-param name="inherits" select="$prologueInherits"/>
          <xsl:with-param name="inheritedBy" select="$prologueInheritedBy"/>
          <xsl:with-param name="since" select="$prologueSince"/>
          
          <xsl:with-param name="props" select="$qmlProps"/>
          <xsl:with-param name="attachedProps" select="$qmlAttachedProps"/>
          <xsl:with-param name="meths" select="$qmlMeths"/>
          <xsl:with-param name="signals" select="$qmlSignals"></xsl:with-param>
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

      <!-- There may be types, properties, functions, and macros for C++ classes. -->
      <xsl:if test="$hasTypes">
        <xsl:call-template name="content_types">
          <xsl:with-param name="data" select="$types"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$hasProperties">
        <xsl:call-template name="content_props">
          <xsl:with-param name="data" select="$properties"/>
        </xsl:call-template>
      </xsl:if>

      <xsl:if test="$hasFuncs">
        <xsl:call-template name="content_class">
          <xsl:with-param name="data" select="$funcs"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$hasMacros">
        <xsl:call-template name="content_macros">
          <xsl:with-param name="data" select="$macros"/>
        </xsl:call-template>
      </xsl:if>

      <xsl:if test="$hasNonmems">
        <xsl:call-template name="content_nonmems">
          <xsl:with-param name="data" select="$nonmems"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$hasVars">
        <xsl:call-template name="content_vars">
          <xsl:with-param name="data" select="$vars"/>
        </xsl:call-template>
      </xsl:if>
      
      <!-- There may be properties and methods for QML types. -->
      <xsl:if test="$hasQmlProps">
        <xsl:call-template name="content_qmlProps">
          <xsl:with-param name="data" select="$qmlProps"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$hasQmlAttachedProps">
        <xsl:call-template name="content_qmlProps">
          <xsl:with-param name="data" select="$qmlAttachedProps"/>
          <xsl:with-param name="attached" select="true()"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$hasQmlMeths">
        <xsl:call-template name="content_qmlMeths">
          <xsl:with-param name="data" select="$qmlMeths"/>
        </xsl:call-template>
      </xsl:if>
      
      <xsl:if test="$hasQmlSignals">
        <xsl:call-template name="content_qmlMeths">
          <xsl:with-param name="data" select="$qmlSignals"/>
          <xsl:with-param name="type" select="'signal'"/>
        </xsl:call-template>
      </xsl:if>
      
      <!-- There may be obsolete things for C++ classes. -->
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

          <xsl:if test="$obsolete_hasMemFuncs">
            <xsl:call-template name="content_class">
              <xsl:with-param name="data" select="$obsolete_memfuncs"/>
            </xsl:call-template>
          </xsl:if>
          
          <xsl:if test="$obsolete_hasFuncs">
            <xsl:call-template name="content_class">
              <xsl:with-param name="data" select="$obsolete_funcs"/>
              <xsl:with-param name="title" select="'Function Documentation'"></xsl:with-param>
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
    <xsl:variable name="title_raw">
      <xsl:apply-templates mode="content_title_hidden">
        <xsl:with-param name="what" select="."/>
      </xsl:apply-templates>
    </xsl:variable>
    <xsl:variable name="title" select="normalize-space(replace(replace($title_raw, ' \(', '('), '(\r|\n|\r\n|(  ))+', ' '))"/>
    <xsl:for-each select="tokenize($title, '\|\\\|/\|')">
      <xsl:choose>
        <xsl:when test="starts-with($title, .)">
          <!-- This is the first (and often only) title of the list. -->
          <db:title>
            <xsl:value-of select="normalize-space(.)"/>
          </db:title>
        </xsl:when>
        <xsl:otherwise>
          <db:bridgehead renderas="sect2">
            <xsl:value-of select="normalize-space(.)"/>
          </db:bridgehead>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:br">
    <xsl:text>|\|/|</xsl:text>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:h3">
    <xsl:apply-templates mode="content_title_hidden"/>
  </xsl:template>
  <xsl:template mode="content_title_hidden" match="html:p">
    <xsl:for-each-group select="." group-starting-with="html:br">
      <xsl:apply-templates mode="content_title_hidden"/>
    </xsl:for-each-group>
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
    <xsl:param name="isNamespace" as="xs:boolean" select="false()"/>
    
    <xsl:param name="functions" as="element(html:div)?"/>
    <xsl:param name="properties" as="element(html:div)?"/>
    <xsl:param name="vars" as="element(html:div)?"/>
    
    <xsl:param name="obsoleteMemberFunctions" as="element(html:div)?"/>
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
      
      <xsl:if test="$isNamespace">
        <db:classsynopsisinfo role="isNamespace">
          yes
        </db:classsynopsisinfo>
      </xsl:if>
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
        <xsl:for-each select="$inherits/html:a">
          <db:classsynopsisinfo role="inherits">
            <db:link>
              <xsl:attribute name="xlink:href" select="./@href"/>
              <xsl:value-of select="./text()"/>
            </db:link>
          </db:classsynopsisinfo>
        </xsl:for-each>
      </xsl:if>
      <xsl:if test="$inheritedBy">
        <xsl:for-each select="$inheritedBy/html:p/html:a">
          <db:classsynopsisinfo role="inheritedBy">
            <db:link>
              <xsl:attribute name="xlink:href" select="./@href"/>
              <xsl:value-of select="./text()"/>
            </db:link>
          </db:classsynopsisinfo>
        </xsl:for-each>
      </xsl:if>
      <xsl:if test="$since">
        <db:classsynopsisinfo role="since">
          <xsl:value-of select="$since"/>
        </db:classsynopsisinfo>
      </xsl:if>
      
      <!-- Deal with properties and variables as fields. -->
      <xsl:apply-templates mode="propertiesListing" select="$properties/html:h3">
        <xsl:with-param name="kind" select="'Qt property'"/>
      </xsl:apply-templates>
      <xsl:if test="boolean($obsoleteProperties)">
        <xsl:apply-templates mode="propertiesListing" select="$obsoleteProperties/html:h3">
          <xsl:with-param name="obsolete" select="true()"/>
          <xsl:with-param name="kind" select="'Qt property'"/>
        </xsl:apply-templates>
      </xsl:if>
      <xsl:apply-templates mode="propertiesListing" select="$vars/html:h3">
        <xsl:with-param name="kind" select="'public variable'"/>
      </xsl:apply-templates>

      <!-- Deal with functions. -->
      <xsl:apply-templates mode="classListing" select="$functions/html:h3">
        <xsl:with-param name="className" select="$className"/>
      </xsl:apply-templates>
      <xsl:if test="boolean($obsoleteMemberFunctions)">
        <xsl:apply-templates mode="classListing" select="$obsoleteMemberFunctions/html:h3">
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
    <xsl:param name="kind" as="xs:string"/>
    <xsl:variable name="anchor" select="./@id"/>
    
    <db:fieldsynopsis>
      <xsl:if test="$kind">
        <db:modifier>(<xsl:value-of select="$kind"/>)</db:modifier>
      </xsl:if>
      
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
  
  <xsl:template name="macroListing">
    <xsl:param name="data" as="element(html:div)"/>
    <xsl:param name="className" as="xs:string"/>
    
    <xsl:apply-templates mode="macroListing" select="$data/html:h3">
      <xsl:with-param name="className" select="$className"/>
    </xsl:apply-templates>
  </xsl:template>
  <xsl:template mode="macroListing" match="text()"/>
  <xsl:template mode="macroListing" match="html:h3[@class = 'fn']">
    <db:funcsynopsis>
      <xsl:attribute name="xlink:href" select="concat('#', ./@id)"/>
      
      <db:funcsynopsisinfo>macro</db:funcsynopsisinfo>
      <db:funcprototype>
        <xsl:variable name="titleNode" select="."/>
        <xsl:variable name="functionName" select="./html:span[@class = 'name']"/>
        
        <db:funcdef>
          <db:function>
            <xsl:value-of select="$functionName"/>
          </db:function>
        </db:funcdef>
        
        <!-- Handle parameters list. -->
        <xsl:variable name="textAfterName" select="normalize-space($functionName/following-sibling::text()[1])"/>
        <xsl:choose>
          <xsl:when test="$textAfterName = '' or starts-with($textAfterName, '()')">
            <db:void/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:for-each select="./html:i">
              <db:paramdef choice="req">
                <!-- A macro only has a name. -->
                <db:parameter>
                  <xsl:value-of select="normalize-space(.)"/>
                </db:parameter>
              </db:paramdef>
            </xsl:for-each>
          </xsl:otherwise>
        </xsl:choose>
      </db:funcprototype>
    </db:funcsynopsis>
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
    <xsl:if test="$warnVocabularyUnsupportedFeatures">
      <xsl:message terminate="no">WARNING: No summary output for typedefs. </xsl:message>
    </xsl:if>
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
          <!-- Unhappily, DocBook does not allow <db:void> here… -->
          <xsl:if test="$returnTypes and not(contains($returnTypes/text(), 'void'))">
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

              <db:paramdef choice="req">
                <!-- Maybe this parameter is const. -->
                <xsl:if test="normalize-space($textAfterName) = '(const'">
                  <!-- DocBook does not allow <db:modifier>!? -->
                  <db:type>const</db:type>
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
  <xsl:template mode="functionListing" match="html:h3[not(@class = 'fn')][starts-with(normalize-space(.), 'class')]">
    <db:classsynopsis>
      <db:ooclass>
        <db:classname>
          <xsl:attribute name="xlink:href" select="./html:a/@href"/>
          <xsl:value-of select="./html:a"/>
        </db:classname>
      </db:ooclass>
    </db:classsynopsis>
  </xsl:template>
  
  <!-- Handle QML types: type structure. -->
  <xsl:template name="qmlTypeListing">
    <xsl:param name="qmlTypeName" as="xs:string"/>
    
    <xsl:param name="import" as="element()?"/>
    <xsl:param name="instantiates" as="element()?"/>
    <xsl:param name="instantiatedBy" as="element()?"></xsl:param>
    <xsl:param name="inherits" as="element()?"/>
    <xsl:param name="inheritedBy" as="element()?"/>
    <xsl:param name="since" as="element()?"/>
    
    <xsl:param name="props" as="element()?"/> 
    <xsl:param name="attachedProps" as="element()?"/> 
    <xsl:param name="meths" as="element()?"/>
    <xsl:param name="signals" as="element()?"/>
    
    <db:classsynopsis>
      <db:ooclass>
        <db:classname>
          <xsl:value-of select="$qmlTypeName"/>
        </db:classname>
      </db:ooclass>
      
      <xsl:if test="$import">
        <db:classsynopsisinfo role="import">
          <xsl:value-of select="normalize-space($import)"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$instantiates">
        <db:classsynopsisinfo role="instantiates">
          <xsl:value-of select="$instantiates"/>
        </db:classsynopsisinfo>
      </xsl:if>
      <xsl:if test="$instantiatedBy">
        <db:classsynopsisinfo role="instantiatedBy">
          <xsl:value-of select="$instantiatedBy"/>
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
      
      <xsl:if test="$props">
        <xsl:apply-templates mode="qmlPropertiesListing" select="$props/html:div[@class = 'qmlitem']"/>
      </xsl:if>
      
      <xsl:if test="$attachedProps">
        <xsl:apply-templates mode="qmlPropertiesListing" select="$attachedProps/html:div[@class = 'qmlitem']">
          <xsl:with-param name="attached" select="true()"></xsl:with-param>
        </xsl:apply-templates>
      </xsl:if>
      
      <xsl:if test="$meths">
        <xsl:apply-templates mode="qmlMethodsListing" select="$meths/html:div[@class = 'qmlitem']"/>
      </xsl:if>
      
      <xsl:if test="$signals">
        <xsl:apply-templates mode="qmlMethodsListing" select="$signals/html:div[@class = 'qmlitem']">
          <xsl:with-param name="type" select="'signal'"></xsl:with-param>
        </xsl:apply-templates>
      </xsl:if>
    </db:classsynopsis>
  </xsl:template>
  <xsl:template mode="qmlPropertiesListing" match="html:div[@class = 'qmlitem']">
    <xsl:param name="attached" select="false()"></xsl:param>
    
    <xsl:variable name="rows" select="html:div[@class = 'qmlproto']/html:div[@class = 'table']/html:table[@class = 'qmlname']/html:tbody/html:tr"/>
    
    <xsl:for-each select="$rows">
      <xsl:variable name="row" select="." as="element(html:tr)"/>
      <xsl:variable name="title" select="$row/html:td/html:p"/>
      <xsl:variable name="anchor" select="$row/@id"/>

      <xsl:if test="$title/html:span[@class = 'name']">
        <!-- TODO: how to handle property groups? -->
        <db:fieldsynopsis>
          <xsl:if test="$attached">
            <db:modifier>attached</db:modifier>
          </xsl:if>
          
          <xsl:call-template name="classListing_methodBody_analyseType">
            <xsl:with-param name="typeNodes" select="$title/html:span[@class = 'type']"/>
          </xsl:call-template>
          <db:varname>
            <xsl:value-of select="$title/html:span[@class = 'name']/text()"/>
          </db:varname>
        </db:fieldsynopsis>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <xsl:template mode="qmlMethodsListing" match="html:div[@class = 'qmlitem']">
    <xsl:param name="type" as="xs:string" select="''"/>
    
    <xsl:variable name="row" select="html:div[@class = 'qmlproto']/html:div[@class = 'table']/html:table[@class = 'qmlname']/html:tbody/html:tr"/>
    <xsl:variable name="title" select="$row/html:td/html:p"/>
    <xsl:variable name="anchor" select="$row/@id"/>
    
    <db:methodsynopsis>
      <xsl:if test="string-length($type)">
        <db:modifier>
          <xsl:value-of select="$type"/>
        </db:modifier>
      </xsl:if>
      
      <!-- Return type. -->
      <xsl:choose>
        <xsl:when test="$title/html:*[2][self::html:span][@class = 'type']">
          <xsl:call-template name="classListing_methodBody_analyseType">
            <xsl:with-param name="typeNodes" select="$title/html:*[2][self::html:span][@class = 'type']"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <db:void/>
        </xsl:otherwise>
      </xsl:choose>
      
      <!-- Method name. -->
      <db:methodname>
        <xsl:value-of select="$title/html:span[@class = 'name']/text()"/>
      </db:methodname>
      
      <!-- Parameters. -->
      <xsl:choose>
        <xsl:when test="count($title/html:span[@class = 'type']) = 0">
          <db:void/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:for-each select="$title/html:span[@class = 'type']">
            <!-- 
              Peculiarity: could very well see a type, but nothing else in the vicinity! Example from Item: 
              <html:tr class="odd" id="grabToImage-method" valign="top">
                <html:td class="tblQmlFuncNode">
                  <html:p>
                    <html:a name="grabToImage-method"/>
                    <html:span class="type">bool</html:span>
                    <html:span class="name">grabToImage</html:span> 
                    (
                      <html:span class="type">callback</html:span>, 
                      <html:span class="type">targetSize</html:span> 
                    )
                  </html:p>
                </html:td>
              </html:tr>
            -->
            <xsl:variable name="potentialType" select="." as="element(html:span)"/>
            <xsl:variable name="nextNode" select="$potentialType/following-sibling::node()[1]" as="node()"/>
            <xsl:variable name="hasType" as="xs:boolean" select="$nextNode and not($nextNode[self::text()])"/>
            
            <xsl:variable name="type" as="element(html:span)?">
              <xsl:if test="$hasType">
                <xsl:copy-of select="$potentialType"></xsl:copy-of>
              </xsl:if>
            </xsl:variable>
            <xsl:variable name="name" as="text()">
              <xsl:choose>
                <xsl:when test="$hasType">
                  <xsl:value-of select="$nextNode"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="$potentialType"/>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:variable>
            
            <db:methodparam>
              <xsl:if test="$hasType">
                <xsl:call-template name="classListing_methodBody_analyseType">
                  <xsl:with-param name="typeNodes" select="$type"/>
                </xsl:call-template>
              </xsl:if>
              <db:parameter><xsl:value-of select="normalize-space($name)"/></db:parameter>
            </db:methodparam>
          </xsl:for-each>
        </xsl:otherwise>
      </xsl:choose>
    </db:methodsynopsis>
  </xsl:template>
  
  <!-- Handle C++ types: detailed description. -->
  <xsl:template name="content_types">
    <xsl:param name="data" as="element(html:div)"/>

    <db:section>
      <db:title>Member Type Documentation</db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_types" select="$data/html:h3"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:h3))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
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
  
  <!-- Handle C++ properties: detailed description. -->
  <xsl:template name="content_props">
    <xsl:param name="data" as="element(html:div)"/>
    
    <db:section>
      <db:title>Property Documentation</db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_types" select="$data/html:h3"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:h3))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
    </db:section>
  </xsl:template>

  <!-- Handle C++ classes: detailed description. -->
  <xsl:template name="content_class">
    <xsl:param name="data" as="element(html:div)"/>
    <xsl:param name="title" as="xs:string" select="'Member Function Documentation'"></xsl:param>

    <db:section>
      <db:title>
        <xsl:value-of select="$title"/>
      </db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_class" select="$data/html:h3"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:h3))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
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
          <xsl:if test="$warnMissingDocumentation">
            <xsl:message>WARNING: The function "<xsl:value-of select="$functionAnchor"/>" has no documentation.</xsl:message>
          </xsl:if>
          <db:para/>
        </xsl:otherwise>
      </xsl:choose>
    </db:section>
  </xsl:template>
  <xsl:template name="content_class_content">
    <xsl:param name="node" as="element()?"/>

    <xsl:if test="$node and not($node[self::html:h3]) and not($node[self::html:div[starts-with(@class, 'qml')]])">
      <xsl:apply-templates mode="content" select="$node"/>
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="$node/following-sibling::*[1]"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>
  
  <!-- Handle C++ macros. -->
  <xsl:template name="content_macros">
    <xsl:param name="data" as="element(html:div)"/>
    
    <db:section>
      <db:title>Macro Documentation</db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_nonmems" select="$data/html:h3"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:h3))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
    </db:section>
  </xsl:template>
  
  <!-- Handle C++ classes: non-member related functions, typedefs, classes. -->
  <xsl:template name="content_nonmems">
    <xsl:param name="data" as="element(html:div)"/>
    
    <db:section>
      <db:title>Related Non-Members</db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_nonmems" select="$data/html:h3"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:h3))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_nonmems" match="html:h3[not(@class)]">
    <!-- Classes (they have no anchor!) -->
    <xsl:variable name="functionAnchor" select="./@id"/>
    <db:section>
      <xsl:call-template name="content_title"/>
      
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_nonmems" match="html:h3[@class = 'fn'][ends-with(@id, '-typedef')]">
    <!-- Classes -->
    <xsl:variable name="functionAnchor" select="./@id"/>
    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>
      <xsl:call-template name="content_title"/>
      
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_nonmems" match="html:h3[@class = 'fn'][not(ends-with(@id, '-typedef'))]">
    <xsl:variable name="functionAnchor" select="./@id"/>
    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>
      <xsl:call-template name="content_title"/>
      
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./following-sibling::*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  
  <!-- Handle C++ classes: member variables. -->
  <xsl:template name="content_vars">
    <xsl:param name="data" as="element(html:div)"/>
    
    <db:section>
      <db:title>Member Variable Documentation</db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_types" select="$data/html:h3"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:h3))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
    </db:section>
  </xsl:template>
  
  <!-- Handle QML properties. -->
  <xsl:template name="content_qmlProps">
    <xsl:param name="data" as="element(html:div)"/>
    <xsl:param name="attached" select="false()" as="xs:boolean"></xsl:param>
    
    <db:section>
      <xsl:choose>
        <xsl:when test="$attached">
          <db:title>Attached Properties Documentation</db:title>
        </xsl:when>
        <xsl:otherwise>
          <db:title>Properties Documentation</db:title>
        </xsl:otherwise>
      </xsl:choose>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_qmlProps" select="$data/html:div[@class = 'qmlitem']"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:div[@class = 'qmlitem']))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
    </db:section>
  </xsl:template>
  <xsl:template mode="content_qmlProps" match="html:div[@class = 'qmlitem']">
    <xsl:variable name="table" select="./html:div[@class = 'qmlproto']/html:div[@class = 'table']/html:table[@class = 'qmlname']/html:tbody" as="element(html:tbody)"/>
    <xsl:variable name="row" select="$table/html:tr[1]" as="element(html:tr)"/>
    <xsl:variable name="functionAnchor" select="$row/@id"/>
    <db:section>
      <xsl:attribute name="xml:id" select="$functionAnchor"/>
      
      <!-- 
        Output the title. Either only one (single property) or a sequence of titles (property group).
        Not reusing that of C++, as it does not handle links (need to renormalise a lot strings in C++, 
        not as much for QML). 
      -->
      <xsl:choose>
        <xsl:when test="$row/html:td/html:p">
          <!-- Single property. -->
          <db:title>
            <xsl:apply-templates mode="content_paragraph" select="$row/html:td/html:p/html:span[@class = 'name']"/>
            <xsl:for-each select="$row/html:td/html:p/html:span[@class = 'name']/following-sibling::node()">
              <xsl:apply-templates mode="content_paragraph" select="."/>
            </xsl:for-each>
          </db:title>
        </xsl:when>
        <xsl:otherwise>
          <!-- Property group. Group title on the first row; actual properties on subsequent rows. -->
          <db:title>
            <xsl:value-of select="$row/html:b"/>
          </db:title>
          
          <xsl:for-each select="$table/html:tr">
            <xsl:if test="not(.//html:b)">
              <db:bridgehead renderas="sect3">
                <xsl:value-of select="./html:td/html:p/html:span[@class = 'name']"/>
                <xsl:text> : </xsl:text>
                <xsl:apply-templates mode="content_paragraph" select="./html:td/html:p/html:span[@class = 'type']"/>
              </db:bridgehead>
            </xsl:if>
          </xsl:for-each>
        </xsl:otherwise>
      </xsl:choose>
      
      <!-- Deal with the content. -->
      <xsl:call-template name="content_class_content">
        <xsl:with-param name="node" select="./html:div[@class = 'qmldoc']/child::html:*[1]"/>
      </xsl:call-template>
    </db:section>
  </xsl:template>
  
  <!-- Handle QML methods. -->
  <xsl:template name="content_qmlMeths">
    <xsl:param name="data" as="element(html:div)"/>
    <xsl:param name="type" as="xs:string" select="''"/>
    
    <db:section>
      <db:title>
        <xsl:choose>
          <xsl:when test="$type = 'signal'">
            Signals Documentation
          </xsl:when>
          <xsl:otherwise>
            Methods Documentation
          </xsl:otherwise>
        </xsl:choose>
      </db:title>
      
      <xsl:variable name="elts_translated">
        <xsl:apply-templates mode="content_qmlProps" select="$data/html:div[@class = 'qmlitem']"/>
      </xsl:variable>
      <xsl:if test="not(count($elts_translated/db:section) = count($data/html:div[@class = 'qmlitem']))">
        <xsl:message>WARNING: Missed at least one element!</xsl:message>
      </xsl:if>
      <xsl:copy-of select="$elts_translated"/>
    </db:section>
  </xsl:template>

  <!-- Catch-all for style sheet errors. -->
  <xsl:template match="*" mode="#all">
    <xsl:message terminate="no">WARNING: Unmatched element: <xsl:value-of select="name()"
      /></xsl:message>
  </xsl:template>

  <!-- 
    Handle HTML content and transform it into DocBook. 
    (Tables are implemented with HTML model, not CALS.) 
  -->
  <xsl:template mode="content" match="html:div[@class = 'table']">
    <xsl:apply-templates select="*" mode="content"/>
  </xsl:template>
  <xsl:template mode="content" match="html:table">
    <db:informaltable>
      <xsl:apply-templates select="*" mode="content_table"/>
    </db:informaltable>
  </xsl:template>
  <xsl:template mode="content" match="html:div">
    <xsl:choose>
      <xsl:when test="@class = 'multi-column'">
        <db:informaltable>
          <db:tbody>
            <db:tr>
              <xsl:for-each select="./html:div[@class = 'doc-column']">
                <db:td>
                  <xsl:apply-templates select="*" mode="content"/>
                </db:td>
              </xsl:for-each>
            </db:tr>
          </db:tbody>
        </db:informaltable>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="*" mode="content"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content" match="html:span"/>
  <xsl:template mode="content" match="html:hr">
    <!-- Due to lack of proper separator in DocBook… -->
    <db:bridgehead renderas="sect1">&#0151;</db:bridgehead>
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
        <!-- If title: <figure>. Otherwise: <informalfigure>. -->
        <db:informalfigure>
          <db:mediaobject>
            <xsl:if test="not(normalize-space(./html:img/@alt) = '')">
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
      <xsl:when test="child::html:img and count(child::html:*) = count(child::html:img) + count(child::html:br)">
        <!-- An implicit array of images. -->
        <db:informaltable>
          <db:tbody>
            <xsl:for-each-group select="child::html:*" group-starting-with="html:br">
              <db:tr>
                <xsl:for-each select="current-group()">
                  <xsl:if test="self::html:img">
                    <db:td>
                      <db:para>
                        <xsl:apply-templates mode="content_paragraph" select="."/>
                      </db:para>
                    </db:td>
                  </xsl:if>
               </xsl:for-each>
              </db:tr>
            </xsl:for-each-group>
          </db:tbody>
        </db:informaltable>
      </xsl:when>
      <xsl:when test="
        ./child::node()[1][self::html:b] and (
          starts-with(./html:b[1]/text()[1], 'Note') 
          or starts-with(./html:b[1]/text()[1], 'Warning') 
          or starts-with(./html:b[1]/text()[1], 'See also')
        )">
        <!-- Sometimes, some "titles" are in bold, but do not correspond to these special texts! They should flow normally, unmatched here. -->
        <xsl:choose>
          <xsl:when test="starts-with(./html:b[1]/text(), 'Note')">
            <db:note>
              <db:para>
                <xsl:apply-templates mode="content_paragraph">
                  <xsl:with-param name="forgetNotes" select="true()"/>
                </xsl:apply-templates>
              </db:para>
            </db:note>
          </xsl:when>
          <xsl:when test="starts-with(./html:b[1]/text(), 'Warning')">
            <db:warning>
              <db:para>
                <xsl:apply-templates mode="content_paragraph">
                  <xsl:with-param name="forgetNotes" select="true()"/>
                </xsl:apply-templates>
              </db:para>
            </db:warning>
          </xsl:when>
          <xsl:when test="starts-with(./html:b[1]/text(), 'See also')">
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
    <db:blockquote>
      <xsl:apply-templates mode="content_bq"/>
    </db:blockquote>
  </xsl:template>
  <!-- Titles are handled in template content_withTitles_before. -->
  <xsl:template mode="content" match="html:h2 | html:h3 | html:h4 | html:h5 | html:h6"/>
  <xsl:template mode="content" match="html:br"/>
  <xsl:template mode="content" match="html:pre">
    <db:programlisting>
      <!-- 
        All codes may have class="cpp", even JavaScript (qtqml-javascript-imports.xml), 
        even though it's sometimes correct: qtbluetooth-index.xml, qml-color.xml. 
        In other words: when it's not C++, it's reliable. 
      -->
      <xsl:if test="@class and not(@class = 'cpp')">
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
      select="($firstTitle, $firstTitle/following-sibling::*)"/>

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

        <!-- Handle title then subsections. In rare occasions, some sections are empty, and are directly followed by another title. -->
        <db:title>
          <xsl:copy-of select="./text()"/>
        </db:title>
        <xsl:if test="./following-sibling::html:*[1][self::html:a] and (./following-sibling::html:*[2][self::html:h2 or self::html:h3])">
          <db:para/>
        </xsl:if>
        <xsl:for-each-group select="current-group()" group-starting-with="html:h3">
          <xsl:choose>
            <xsl:when test="current-group()[self::html:h3]">
              <db:section>
                <db:title>
                  <xsl:copy-of select="./text()"/>
                </db:title>
                <xsl:if test="./following-sibling::html:*[1][self::html:a] and (./following-sibling::html:*[2][self::html:h3 or self::html:h4])">
                  <db:para/>
                </xsl:if>

                <xsl:for-each-group select="current-group()" group-starting-with="html:h4">
                  <xsl:choose>
                    <xsl:when test="current-group()[self::html:h4]">
                      <db:section>
                        <db:title>
                          <xsl:copy-of select="./text()"/>
                        </db:title>
                        <xsl:if test="./following-sibling::html:*[1][self::html:a] and (./following-sibling::html:*[2][self::html:h4 or self::html:h5])">
                          <db:para/>
                        </xsl:if>

                        <xsl:for-each-group select="current-group()" group-starting-with="html:h5">
                          <xsl:choose>
                            <xsl:when test="current-group()[self::html:h5]">
                              <db:section>
                                <db:title>
                                  <xsl:copy-of select="./text()"/>
                                </db:title>
                                <xsl:if test="./following-sibling::html:*[1][self::html:a] and (./following-sibling::html:*[2][self::html:h5 or self::html:h6])">
                                  <db:para/>
                                </xsl:if>
  
                                <xsl:for-each-group select="current-group()"
                                  group-starting-with="html:h6">
                                  <xsl:choose>
                                    <xsl:when test="current-group()[self::html:h6]">
                                      <db:section>
                                        <db:title>
                                          <xsl:copy-of select="./text()"/>
                                        </db:title>
                                        <xsl:if test="./following-sibling::html:*[1][self::html:a] and (./following-sibling::html:*[2][self::html:h6])">
                                          <db:para/>
                                        </xsl:if>
  
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

      <!-- 
        A cell in the table might contain directly a paragraph (or code, or a list), or text that
        should be wrapped in a paragraph. Except that it sometimes contains text to wrap, then a 
        paragraph! Example (designer-creating-custom-widgets): 
            <html:td>
              A <html:a href="qtwidgets/qwidget.html">QWidget</html:a> pointer to an instance 
              of the custom widget, constructed with the parent supplied.
              <html:p>
                <html:b>Note: </html:b>
                createWidget() is a factory function responsible for creating the widget only. 
                The custom widget's properties will not be available until load() returns.
              </html:p>
            </html:td>
      -->
      <xsl:choose>
        <xsl:when test="./child::node()[1][self::html:p] | ./child::node()[1][self::html:pre] | ./child::node()[1][self::html:ul] | ./child::node()[1][self::html:ol]">
          <xsl:apply-templates select="*" mode="content"/>
        </xsl:when>
        <xsl:when test="./child::html:p">
          <!-- There is some text to wrap, then already wrapped text. -->
          <xsl:variable name="startText" as="element(html:p)">
            <html:p>
              <xsl:for-each select="./child::node()">
                <xsl:if test="not(self::html:p)">
                  <xsl:copy-of select="."></xsl:copy-of>
                </xsl:if>
              </xsl:for-each>
            </html:p>
          </xsl:variable>
          <xsl:variable name="endText" as="element(html:p)+">
            <xsl:for-each select="./child::node()">
              <xsl:if test="self::html:p">
                <xsl:copy-of select="."></xsl:copy-of>
              </xsl:if>
            </xsl:for-each>
          </xsl:variable>
          
          <xsl:apply-templates select="$startText" mode="content"/>
          <xsl:apply-templates select="$endText" mode="content"/>
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
  <xsl:template mode="content_paragraph" match="html:span">
    <xsl:apply-templates mode="content_paragraph" select="child::node()"/>
  </xsl:template>
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
  <xsl:template mode="content_paragraph" match="html:br"/>
  <xsl:template mode="content_paragraph" match="html:font[@color = 'red']">
    <xsl:message>WARNING: Error at QDoc step. Given message: <xsl:value-of select="."/></xsl:message>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:a">
    <!-- 
      Output a link, maybe enclosing its content with <db:code> when it's a method (followed by parentheses) or a class. 
      
      Strange things to output <db:code> (output it as pure text, but unescaped), just to ensure there is no whitespace 
      between this tag and the link, i.e. visible space to the user!
      
      The content may have multiple text values, such as: 
          <html:a href="qtqml-syntax-objectattributes.html#the-id-attribute">The <html:i>id</html:i> Attribute</html:a>
    -->
    <xsl:choose>
      <xsl:when test="starts-with(./following-sibling::text()[1], '()')">
        <xsl:if test="not(ancestor::node()[self::html:code])">
          <xsl:text disable-output-escaping="yes">&lt;db:code&gt;</xsl:text>
        </xsl:if>
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
        
        <xsl:if test="not(ancestor::node()[self::html:code])">
          <xsl:text disable-output-escaping="yes">&lt;/db:code&gt;</xsl:text>
        </xsl:if>
      </xsl:when>
      <xsl:when test="count(./text()) = 1 and starts-with(./text(), 'Q') and not(contains(./text(), ' '))">
        <xsl:if test="not(ancestor::node()[self::html:code])">
          <xsl:text disable-output-escaping="yes">&lt;db:code&gt;</xsl:text>
        </xsl:if>
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
        
        <xsl:if test="not(ancestor::node()[self::html:code])">
          <xsl:text disable-output-escaping="yes">&lt;/db:code&gt;</xsl:text>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <db:link>
          <xsl:attribute name="xlink:href" select="@href"/>
          <xsl:apply-templates mode="content_paragraph"/>
        </db:link>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:b/html:u">
    <!-- 
      This defines an accelerator, e.g.:
          <html:li><html:b><html:u>A</html:u></html:b>bout</html:li>
          <html:li><html:b><html:u>C</html:u></html:b>opy (CDE: Ctrl+C, Ctrl+Insert)</html:li>
      Hard to guess anything about the surroundings. 
    -->
    <db:accel>
      <xsl:apply-templates mode="content_paragraph"/>
    </db:accel>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:b | html:strong">
    <xsl:param name="forgetNotes" select="false()"/>
    <xsl:if test="not($forgetNotes)">
      <xsl:choose>
        <xsl:when test="not(./html:u)">
          <db:emphasis role="bold">
            <xsl:apply-templates mode="content_paragraph"/>
          </db:emphasis>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates mode="content_paragraph"/>
        </xsl:otherwise>
      </xsl:choose>
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
    <xsl:choose>
      <xsl:when test="..[self::html:code]">
        <db:replaceable>
          <xsl:apply-templates mode="content_paragraph"/>
        </db:replaceable>
      </xsl:when>
      <xsl:otherwise>
        <db:emphasis>
          <xsl:apply-templates mode="content_paragraph"/>
        </db:emphasis>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:u">
    <db:accel>
      <xsl:apply-templates mode="content_list"/>
    </db:accel>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:sub">
    <db:subscript>
      <xsl:apply-templates mode="content_list"/>
    </db:subscript>
  </xsl:template>
  <xsl:template mode="content_paragraph" match="html:sup">
    <db:superscript>
      <xsl:apply-templates mode="content_list"/>
    </db:superscript>
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
  <xsl:template mode="content_paragraph" match="html:pre">
    <db:programlisting>
      <xsl:value-of select="."/>
    </db:programlisting>
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
  
  <!-- 
    Finally, take back those elements and handle block quotes.
    Something similar is required to handle correctly text(). 
  -->
  <xsl:template mode="content_bq" match="html:h1 | html:h2 | html:h3 | html:h4 | html:h5 | html:h6"/>
  <xsl:template mode="content_bq" match="text()"/>
  <xsl:template mode="content_bq" match="html:a">
    <xsl:choose>
      <xsl:when test="./html:h1">
        <db:bridgehead renderas="sect1">
          <xsl:value-of select="."/>
        </db:bridgehead>
      </xsl:when>
      <xsl:when test="./html:h2">
        <db:bridgehead renderas="sect2">
          <xsl:value-of select="."/>
        </db:bridgehead>
      </xsl:when>
      <xsl:when test="./html:h3">
        <db:bridgehead renderas="sect3">
          <xsl:value-of select="."/>
        </db:bridgehead>
      </xsl:when>
      <xsl:when test="./html:h4">
        <db:bridgehead renderas="sect4">
          <xsl:value-of select="."/>
        </db:bridgehead>
      </xsl:when>
      <xsl:when test="./html:h5">
        <db:bridgehead renderas="sect5">
          <xsl:value-of select="."/>
        </db:bridgehead>
      </xsl:when>
      <xsl:when test="./html:h6">
        <db:bridgehead renderas="sect6">
          <xsl:value-of select="."/>
        </db:bridgehead>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content_bq" match="html:p">
    <xsl:if test="not(. = '')">
      <db:para>
        <xsl:apply-templates mode="content_paragraph"/>
      </db:para>
    </xsl:if>
  </xsl:template>
  <xsl:template mode="content_bq" match="html:ul">
    <db:itemizedlist>
      <xsl:apply-templates mode="content_list"/>
    </db:itemizedlist>
  </xsl:template>
  <xsl:template mode="content_bq" match="html:ol">
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
  <xsl:template mode="content_bq" match="html:pre">
    <db:programlisting>
      <!-- 
        All codes may have class="cpp", even JavaScript (qtqml-javascript-imports.xml), 
        even though it's sometimes correct: qtbluetooth-index.xml, qml-color.xml. 
        In other words: when it's not C++, it's reliable. 
      -->
      <xsl:if test="@class and not(@class = 'cpp')">
        <xsl:attribute name="language" select="@class"/>
      </xsl:if>
      <xsl:value-of select="."/>
    </db:programlisting>
  </xsl:template>
</xsl:stylesheet>
