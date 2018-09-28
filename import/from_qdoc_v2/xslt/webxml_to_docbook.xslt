<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:map="http://www.w3.org/2005/xpath-functions/map" 
  xmlns:tc="http://dourouc05.github.io"
  exclude-result-prefixes="xsl xs html saxon map tc"
  version="3.0">
  
  <xsl:output method="xml" indent="yes"
    suppress-indentation="db:code db:emphasis db:link db:programlisting db:title"/>
  <xsl:strip-space elements="*"/>
  
  <xsl:param name="qt-version" as="xs:string" select="'1.2'"/>
  <!--<xsl:param name="qt-version" as="xs:string" required="true"/>-->
  <xsl:param name="local-folder" as="xs:string" select="'file:///C:/Qt/Doc/webxml/'"/>
  <!--<xsl:param name="local-foldern" as="xs:string" required="true"/>-->
  
  <xsl:template match="/">
    <xsl:apply-templates select="WebXML/document"/>
  </xsl:template>
  
  <xsl:function name="tc:sanitise-xml-id" as="xs:string">
    <xsl:param name="xml-id" as="xs:string"/>
    <xsl:choose>
      <xsl:when test="contains($xml-id, ':')">
        <xsl:value-of select="translate($xml-id, ':', '-')"/>
      </xsl:when>
      <xsl:when test="matches($xml-id, '^[0-9]')">
        <xsl:value-of select="concat('a', $xml-id)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$xml-id"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:template match="document">
    <xsl:variable name="mainTag" select="child::node()[1]" as="node()"/>
    
    <db:article version="5.2" xml:lang="en">
      <db:info>
        <db:title>
          <xsl:choose>
            <xsl:when test="$mainTag/@fulltitle"><xsl:value-of select="$mainTag/@fulltitle"/></xsl:when>
            <xsl:when test="$mainTag/@title"><xsl:value-of select="$mainTag/@title"/></xsl:when>
            <xsl:when test="$mainTag/@name"><xsl:value-of select="$mainTag/@name"/></xsl:when>
            <xsl:otherwise><xsl:message>WARNING: No title found.</xsl:message></xsl:otherwise>
          </xsl:choose>
        </db:title>
        
        <xsl:if test="child::node()[1]/@brief or child::node()[1]/description/relation">
          <db:abstract>
            <db:para>
              <xsl:value-of select="child::node()[1]/@brief"/>
            </db:para>
            <xsl:if test="$mainTag/description/relation">
              <db:para>
                <db:simplelist>
                  <xsl:for-each select="$mainTag/description/relation">
                    <db:member>
                      <db:link xlink:href="{@href}" xlink:title="{@meta}">
                        <xsl:choose>
                          <xsl:when test="@meta='previous'">
                            <xsl:text>&lt; </xsl:text>
                          </xsl:when>
                          <xsl:when test="@meta='contents'">
                            <xsl:text>^ </xsl:text>
                          </xsl:when>
                        </xsl:choose>
                        <xsl:value-of select="@description"/>
                        <xsl:choose>
                          <xsl:when test="@meta='next'">
                            <xsl:text> &gt;</xsl:text>
                          </xsl:when>
                          <xsl:when test="@meta='contents'">
                            <xsl:text> ^</xsl:text>
                          </xsl:when>
                        </xsl:choose>
                      </db:link>
                    </db:member>
                  </xsl:for-each>
                </db:simplelist>
              </db:para>
            </xsl:if>
          </db:abstract>
        </xsl:if>
        
        <db:pubdate><xsl:value-of select="current-date()"/></db:pubdate>
        <db:date><xsl:value-of select="current-date()"/></db:date>
        <db:productname>Qt</db:productname>
        <db:productnumber><xsl:value-of select="$qt-version"/></db:productnumber>
      </db:info>
      
      <!-- Deal with the rest of the content. -->
      <xsl:variable name="nChildren" select="count(child::node()[1]/description/*)"/>
      <xsl:choose>
        <xsl:when test="child::node()[1]/description[not(*)] or ($nChildren = 1 and child::node()[1]/description/brief) or ($nChildren = count(child::node()[1]/description/relation))">
          <db:para/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates mode="content"/>
        </xsl:otherwise>
      </xsl:choose>
    </db:article>
  </xsl:template>
  
  <!-- Deal with classes. -->
  <xsl:template mode="content" match="class">
    <!-- First, the synopsis (extract everything from the class tag). -->
    <xsl:call-template name="content_class_synopsis"/>
    <xsl:apply-templates mode="content_class_description" select="description"/>
    <xsl:call-template name="content_class_elements">
      <xsl:with-param name="elements" select="./description/following-sibling::node()"/>
    </xsl:call-template>
  </xsl:template>
  
  <xsl:template name="content_class_synopsis">
    <xsl:if test="@access='public' and @status='active'">
      <db:classsynopsis>
        <db:ooclass>
          <db:classname><xsl:value-of select="@name"/></db:classname>
        </db:ooclass>
        <xsl:if test="not(@threadsafety='unspecified')">
          <db:classsynopsisinfo role="threadsafety">
            <xsl:value-of select="@threadsafety"/>
          </db:classsynopsisinfo>
        </xsl:if>
        <db:classsynopsisinfo role="module">
          <xsl:value-of select="@module"/>
        </db:classsynopsisinfo> 
        <db:classsynopsisinfo role="headers">#include &lt;<xsl:value-of select="@location"/>&gt;</db:classsynopsisinfo>
        <xsl:if test="@since">
          <db:classsynopsisinfo role="since">
            <xsl:value-of select="@since"/>
          </db:classsynopsisinfo>
        </xsl:if>
        
        <xsl:for-each select="tokenize(@bases, ',')">
          <db:classsynopsisinfo role="inherits">
            <xsl:value-of select="."/>
          </db:classsynopsisinfo>
        </xsl:for-each>
        
        <xsl:for-each select="tokenize(@groups, ',')">
          <db:classsynopsisinfo role="group">
            <xsl:value-of select="."/>
          </db:classsynopsisinfo>
        </xsl:for-each>
      </db:classsynopsis>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_description" match="description">
    <db:section>
      <xsl:attribute name="xml:id" select="'details'"/>
      <db:title>Detailed Description</db:title>
      
      <xsl:apply-templates mode="content"/>
    </db:section>
  </xsl:template>
  
  <xsl:function name="tc:is-element-included" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="not($currentNode/@access='private') and (not($currentNode/@delete) or $currentNode/@delete='false') and $currentNode/@status='active' and count($currentNode/description/*[not(self::see-also)]) > 0"/>
  </xsl:function>
  
  <xsl:template name="content_class_elements">
    <xsl:param name="elements" as="node()*"/>
    
    <!-- Order of elements: namespaces, classes, member types, types, properties, member variables, member functions (first constructors, then others), related non-members, macros. Never forget to sort the items by name. -->
    <xsl:variable name="namespaces" select="$elements/self::namespace[(@access='public' or @access='protected')and description/node()]" as="node()*"/>
    <xsl:variable name="classes" select="$elements/self::class[(@access='public' or @access='protected')and description/node()]" as="node()*"/>
    <xsl:variable name="memberTypes" select="$elements/self::enum[(@access='public' or @access='protected') and description/node()] | $elements/self::typedef[(@access='public' or @access='protected')]" as="node()*"/>
    <xsl:variable name="properties" select="$elements/self::property[(@access='public' or @access='protected') and description/node()]" as="node()*"/>
    <xsl:variable name="memberVariables" select="$elements/self::variable[(@access='public' or @access='protected') and description/node()]" as="node()*"/>
    <xsl:variable name="functions" select="$elements/self::function[(@access='public' or @access='protected') and description/node() and not(@meta='macrowithoutparams' or @meta='macrowithparams')]" as="node()*"/>
    <xsl:variable name="macros" select="$elements/self::function[(@access='public' or @access='protected') and description/node() and (@meta='macrowithoutparams' or @meta='macrowithparams')]" as="node()*"/>
    
    <xsl:variable name="allTakenIntoAccount" select="($namespaces union $classes union $memberTypes union $properties union $memberVariables union $functions union $macros)"/>
    <xsl:if test="count($allTakenIntoAccount[(@access='public' or @access='protected') and description/node()]) != count($elements[(@access='public' or @access='protected') and description/node()])">
      <xsl:message>
        <xsl:text>WARNING: Page not fully parsed. Missing tags: </xsl:text>
        <xsl:for-each select="$elements[(@access='public' or @access='protected') and (description/brief or description/para)] except $allTakenIntoAccount[(@access='public' or @access='protected') and (description/brief or description/para)]">
          <xsl:value-of select="name()"/>
          <xsl:if test="position() &lt; last()">
            <xsl:text>,</xsl:text>
          </xsl:if>
        </xsl:for-each>
      </xsl:message>
    </xsl:if>
    
    <xsl:if test="$namespaces[tc:is-element-included(.)]">
      <db:section>
        <db:title>Namespaces</db:title>
        <xsl:for-each select="$namespaces[tc:is-element-included(.)]/self::namespace">
          <xsl:sort select="@fullname"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$classes[tc:is-element-included(.)] or $memberTypes[tc:is-element-included(.)]">
      <db:section>
        <db:title>Member Types Documentation</db:title>
        <xsl:for-each select="$classes[tc:is-element-included(.)]/self::class">
          <xsl:sort select="@fullname"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <xsl:for-each select="$memberTypes[tc:is-element-included(.)]/self::enum">
          <xsl:sort select="@fullname"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <!-- Many typedefs are just sets of flags, and thence have no description, but some are real. -->
        <xsl:for-each select="$memberTypes[tc:is-element-included(.)]/self::typedef[description/node()]">
          <xsl:sort select="@fullname"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
    
    <!--<xsl:if test="$types">
      <db:section>
        <db:title>Type Documentation</db:title>
        --><!-- Only happens in namespaces. --><!--
        
        <xsl:message>TYPES NOT IMPLEMENTED</xsl:message>
        
        <xsl:if test="@since and not(@since='')">
          <db:para>This type was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
        </xsl:if>
      </db:section>
    </xsl:if>-->
    
    <xsl:if test="$properties[tc:is-element-included(.)]">
      <db:section>
        <db:title>Properties</db:title>
        
        <xsl:for-each select="$properties[tc:is-element-included(.)]">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$memberVariables[tc:is-element-included(.)]">
      <db:section>
        <db:title>Member Variable Documentation</db:title>
        <xsl:for-each select="$memberVariables[tc:is-element-included(.)]/self::variable">
          <xsl:sort select="@fullname"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$functions[tc:is-element-included(.)]">
      <db:section>
        <db:title>Member Function Documentation</db:title>
        <!-- First constructors, then the other functions. -->
        
        <xsl:for-each select="$functions[tc:is-element-included(.)]/self::function[@meta='constructor']">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <xsl:for-each select="$functions[tc:is-element-included(.)]/self::function[@meta='destructor']">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <xsl:for-each select="$functions[tc:is-element-included(.)]/self::function[not(@meta='constructor') and not(@meta='destructor')]">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <!-- TODO: Why doesn't QWidget::paintEngine have documentation in WebXML (but it has some in .cpp)? Also misses in PySide2's doc: https://doc.qt.io/qtforpython/PySide2/QtWidgets/QWidget.html -->
      </db:section>
    </xsl:if>
    
    <!-- TODO --><!--
    <xsl:if test="$relatedNonMembers">
      <db:section>
        <db:title>Related Non-Members</db:title>
        --><!-- TODO: Not generated in WebXML. Example: http://doc.qt.io/qt-5/qpoint.html#related-non-members --><!--
        
        <xsl:message>RELATED NON MEMBERS NOT IMPLEMENTED</xsl:message>
      </db:section>
    </xsl:if>-->
    
    <xsl:if test="$macros[tc:is-element-included(.)]">
      <db:section>
        <db:title>Macro Documentation</db:title>
        
        <xsl:for-each select="$macros[tc:is-element-included(.)]">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="namespace">
    <xsl:if test="not(@access='private') and (not(@delete) or @delete='false') and @status='active'">
      <db:section>
        <db:title>namespace <xsl:value-of select="@fullname"/></db:title>
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:apply-templates mode="content" select="description"/>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This namespace was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_synopsis" match="namespace">
    <xsl:if test="tc:is-element-included(.)">
      <db:namespacesynopsis>
        <db:namespace>
          <db:namespacename><xsl:value-of select="@name"/></db:namespacename>
        </db:namespace>
        <db:namespacesynopsisinfo role="module"><xsl:value-of select="@module"/></db:namespacesynopsisinfo>
        <db:namespacesynopsisinfo role="headers">#include &lt;<xsl:value-of select="@location"/>&gt;</db:namespacesynopsisinfo>
      </db:namespacesynopsis>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="class">
    <xsl:if test="not(@access='private') and (not(@delete) or @delete='false') and @status='active'">
      <db:section>
        <db:title><xsl:value-of select="@fullname"/></db:title>
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:apply-templates mode="content" select="description"/>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This class was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
      </db:section>
    </xsl:if>
  </xsl:template> 
  
  <xsl:template mode="content_class_synopsis" match="class">
    <db:classsynopsis>
      <db:ooclass>
        <db:classname>
          <xsl:value-of select="@fullname"/>
        </db:classname>
      </db:ooclass>
      
      <xsl:if test="variable">
        <xsl:for-each select="variable">
          <xsl:apply-templates mode="content_class_synopsis" select="."/>
        </xsl:for-each>
      </xsl:if>
      
      <xsl:if test="function">
        <xsl:for-each select="function">
          <xsl:apply-templates mode="content_class_synopsis" select="."/>
        </xsl:for-each>
      </xsl:if>
      
      <xsl:if test="enum">
        <xsl:for-each select="enum">
          <xsl:apply-templates mode="content_class_synopsis" select="."/>
          <xsl:if test="following-sibling::*[1][self::typedef]">
            <xsl:apply-templates mode="content_class_synopsis" select="following-sibling::*[1][self::typedef]"/>
          </xsl:if>
        </xsl:for-each>
      </xsl:if>
      
      <xsl:if test="property">
        <xsl:for-each select="property">
          <xsl:apply-templates mode="content_class_synopsis" select="."/>
        </xsl:for-each>
      </xsl:if>
    </db:classsynopsis>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="variable">
    <xsl:if test="not(@access='private') and (not(@delete) or @delete='false') and @status='active'">
      <db:section>
        <db:title><xsl:value-of select="@fullname"/></db:title>
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:apply-templates mode="content" select="description"/>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This variable was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_synopsis" match="variable">
    <db:fieldsynopsis>
      <db:type><xsl:value-of select="@type"/></db:type>
      <db:varname><xsl:value-of select="@name"/></db:varname>
    </db:fieldsynopsis>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="function">
    <xsl:if test="tc:is-element-included(.)">
      <db:section>
        <db:title>
          <!-- For constructors: -->
          <!-- @name:      QWidget -->
          <!-- @fullname:  QWidget::QWidget -->
          <!-- @signature: QWidget(QWidget *parent, Qt::WindowFlags f) -->
          <!-- For methods: -->
          <!-- @name:      backingStore -->
          <!-- @fullname:  QWidget::backingStore -->
          <!-- @signature: QBackingStore * backingStore() const -->
          <xsl:variable name="sanitisedName" select="replace(replace(replace(replace(@name, '\+', '\\+'), '\[', '\\['), '\]', '\\]'), '\|', '\\|')" as="xs:string"/>
          <xsl:value-of select="
            if(contains(@fullname, '::')) then concat(@type, ' ', replace(@signature, concat('(^.*?)', $sanitisedName), @fullname)) else @signature "/>
        </db:title>
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:apply-templates mode="content" select="description"/>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This property was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_synopsis" match="function">
    <!-- Choose the tag depending on the type of function: either constructor, destructor, or any kind of function (including signal and slot). -->
    <xsl:element name="{if (@meta='constructor') then 'db:constructorsynopsis' else if (@meta='destructor') then 'db:destructorsynopsis' else 'db:methodsynopsis'}">
      <!-- Determine whether this function is a signal or a slot. -->
      <xsl:if test="@meta='signal'">
        <db:modifier>signal</db:modifier>
      </xsl:if>
      <xsl:if test="@meta='slot'">
        <db:modifier>slot</db:modifier>
      </xsl:if>
      
      <!-- Modifiers. -->
      <xsl:if test="not(@threadsafety='unspecified')">
        <db:modifier>
          <xsl:value-of select="@threadsafety"/>
        </db:modifier>
      </xsl:if>
      <db:modifier>
        <xsl:value-of select="@access"/>
      </db:modifier>
      <xsl:if test="not(@static='false')">
        <db:modifier>static</db:modifier>
      </xsl:if>
      <xsl:if test="not(@default='false')">
        <db:modifier>default</db:modifier>
      </xsl:if>
      <xsl:if test="not(@final='false')">
        <db:modifier>final</db:modifier>
      </xsl:if>
      <xsl:if test="not(@override='false')">
        <db:modifier>override</db:modifier>
      </xsl:if>
      
      <!-- Return type. Constructors have no type. -->
      <xsl:choose>
        <xsl:when test="not(@type='') and not(@type='void')">
          <db:type>
            <xsl:value-of select="@type"/>
          </db:type>
        </xsl:when>
        <xsl:when test="@type='void'">
          <db:void/>
        </xsl:when>
      </xsl:choose>
      
      <!-- Method name. -->
      <db:methodname>
        <xsl:value-of select="@name"/>
      </db:methodname>
      
      <!-- Parameters. -->
      <xsl:choose>
        <xsl:when test="parameter">
          <xsl:for-each select="parameter">
            <db:methodparam>
              <db:type>
                <xsl:value-of select="@type"/>
              </db:type>
              <db:parameter>
                <xsl:value-of select="@name"/>
              </db:parameter>
              <!-- TODO: Default value for QWidget constructor is ... instead of Qt::WindowFlags() in WebXML -->
              <xsl:if test="@default and not(@default='')">
                <db:initializer>
                  <xsl:value-of select="@default"/>
                </db:initializer>
              </xsl:if>
            </db:methodparam>
          </xsl:for-each>
        </xsl:when>
        <xsl:otherwise>
          <db:void/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:element>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="enum">
    <xsl:if test="tc:is-element-included(.)">
      <db:section>
        <db:title>
          <xsl:text>enum </xsl:text><xsl:value-of select="@fullname"/>
          <xsl:if test="following-sibling::typedef[1]">
            <xsl:text>, flags </xsl:text><xsl:value-of select="@typedef"/>
          </xsl:if>
        </db:title>
        <!-- The documentation is on the enum, but the typedef must be presented just after. -->
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:if test="following-sibling::*[1][self::typedef]">
          <xsl:apply-templates mode="content_class_synopsis" select="following-sibling::*[1][self::typedef]"/>
        </xsl:if>
        <xsl:apply-templates mode="content" select="description/*[not(self::see-also)]"/>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This enum was introduced or modified in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
          
          <xsl:if test="following-sibling::typedef[1]">
            <db:para>The <db:code><xsl:value-of select="./following-sibling::typedef[1]/@name"/></db:code> type is a typedef for <db:code>QFlags&lt;<xsl:value-of select="@name"/>&gt;</db:code>. It stores an OR combination of  values.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
        
        <!-- Ensure the see-also is always at the end of the section. -->
        <xsl:apply-templates mode="content" select="description/see-also"/>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_synopsis" match="enum">
    <db:enumsynopsis>
      <db:enumname><xsl:value-of select="@fullname"/></db:enumname>
      <xsl:if test="@since">
        <db:enumsynopsisinfo role="since">
          <xsl:value-of select="@since"/>
        </db:enumsynopsisinfo>
      </xsl:if>
      
      <xsl:for-each select="value">
        <db:enumitem>
          <db:enumidentifier>
            <xsl:value-of select="@name"/>
          </db:enumidentifier>
          <db:enumvalue>
            <xsl:value-of select="@value"/>
          </db:enumvalue>
        </db:enumitem>
      </xsl:for-each>
    </db:enumsynopsis>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="typedef">
    <!-- Typedefs appear mostly with enums, hence most of the work is done there. -->
    <xsl:if test="tc:is-element-included(.)">
      <db:section>
        <db:title>typedef <xsl:value-of select="@name"/></db:title>
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:if test="following-sibling::*[1][self::typedef]">
          <xsl:apply-templates mode="content_class_synopsis" select="following-sibling::*[1][self::typedef]"/>
        </xsl:if>
        <xsl:apply-templates mode="content" select="description"/>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This enum was introduced or modified in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
          
          <xsl:if test="./following-sibling::typedef[1]">
            <db:para>The <db:code><xsl:value-of select="./following-sibling::typedef[1]/@name"/></db:code> type is a typedef for <db:code>QFlags&lt;<xsl:value-of select="@name"/>&gt;</db:code>. It stores an OR combination of  values.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_synopsis" match="typedef">
    <!-- Typedefs mostly appear with enums, hence most of the work is done there. -->
    <db:typedefsynopsis>
      <db:typedefname><xsl:value-of select="if (@fullname) then @fullname else @name"/></db:typedefname>
    </db:typedefsynopsis>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="property">
    <xsl:if test="tc:is-element-included(.)">
      <db:section>
        <db:title><xsl:value-of select="@name"/> : <xsl:value-of select="@type"/></db:title>
        
        <xsl:apply-templates mode="content_class_synopsis" select="."/>
        <xsl:apply-templates mode="content" select="description"/>
        
        <xsl:if test="getter or setter">
          <db:para>
            <db:emphasis role="bold">Access Functions:</db:emphasis>
            
            <db:informaltable>
              <db:tbody>
                <xsl:if test="getter">
                  <db:tr>
                    <db:td>
                      <xsl:value-of select="@type"/>
                    </db:td>
                    <db:td>
                      <xsl:value-of select="getter/@name"/>() const
                    </db:td>
                  </db:tr>
                </xsl:if>
                <xsl:if test="setter">
                  <db:tr>
                    <db:td>
                      void
                    </db:td>
                    <db:td>
                      <xsl:value-of select="setter/@name"/>(<xsl:value-of select="@type"/> <xsl:value-of select="@name"/>)
                    </db:td>
                  </db:tr>
                </xsl:if>
              </db:tbody>
            </db:informaltable>
          </db:para>
        </xsl:if>
        
        <!-- Generate some text (sometimes in a section, UNLIKE the original documentation). -->
        <xsl:variable name="generatedText" as="node()*">
          <xsl:if test="@since and not(@since='')">
            <db:para>This property was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
          </xsl:if>
        </xsl:variable>
        
        <xsl:choose>
          <xsl:when test="description/*[not(self::see-also)][last()][self::section]">
            <db:section>
              <db:title>Notes</db:title>
              <xsl:copy-of select="$generatedText"></xsl:copy-of>
            </db:section>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="$generatedText"></xsl:copy-of>
          </xsl:otherwise>
        </xsl:choose>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_synopsis" match="property">
    <db:fieldsynopsis>
      <db:modifier>(Qt property)</db:modifier>
      <db:type><xsl:value-of select="@type"/></db:type>
      <db:varname><xsl:value-of select="@name"/></db:varname>
    </db:fieldsynopsis>
    
    <xsl:if test="getter">
      <db:methodsynopsis>
        <db:type>
          <xsl:value-of select="@type"/>
        </db:type>
        
        <db:methodname>
          <xsl:value-of select="getter/@name"/>
        </db:methodname>
        
        <db:void/>
        
        <db:modifier>const</db:modifier>
      </db:methodsynopsis>
    </xsl:if>
    
    <xsl:if test="setter">
      <db:methodsynopsis>
        <db:void/>
        
        <db:methodname>
          <xsl:value-of select="setter/@name"/>
        </db:methodname>
        
        <db:methodparam>
          <db:type><xsl:value-of select="@type"/></db:type>
          <db:parameter><xsl:value-of select="@name"/></db:parameter>
        </db:methodparam>
      </db:methodsynopsis>
    </xsl:if>
  </xsl:template>
  
  <!-- Deal with name spaces. -->
  <xsl:template mode="content" match="namespace">
    <xsl:apply-templates mode="content_class_synopsis" select="."/>
    <xsl:apply-templates mode="content_class_description" select="description"/>
    <xsl:call-template name="content_class_elements">
      <xsl:with-param name="elements" select="description/following-sibling::node()"/>
    </xsl:call-template>
  </xsl:template>
  
  <!-- Deal with modules and QML modules. -->
  <xsl:template mode="content" match="module">
    <xsl:apply-templates mode="content" select="description/generatedlist"/>
    
    <db:section>
      <xsl:attribute name="xml:id" select="'details'"/>
      <db:title>Detailed Description</db:title>
      
      <xsl:apply-templates mode="content" select="description/generatedlist/following-sibling::node()"/>
    </db:section>
  </xsl:template>
  
  <xsl:template mode="content" match="qmlmodule">
    <xsl:apply-templates mode="content" select="description/generatedlist"/>
    
    <db:section>
      <xsl:attribute name="xml:id" select="'details'"/>
      <db:title>Detailed Description</db:title>
      
      <xsl:apply-templates mode="content" select="description/brief/following-sibling::node()"/>
    </db:section>
  </xsl:template>
  
  <!-- Deal with concepts. -->
  <xsl:template mode="content" match="page">
    <xsl:apply-templates mode="content" select="description"/>
  </xsl:template>

  <!-- Deal with groups of examples. -->
  <xsl:template mode="content" match="group">
    <xsl:choose>
      <xsl:when test="not(boolean(section))">
        <db:section>
          <db:title>
            <xsl:value-of select="@title"/>
          </db:title>
          
          <xsl:apply-templates mode="content"/>
        </db:section>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates mode="content"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Generic content handling (paragraphs, sections, etc.) -->
  <xsl:template mode="content" match="brief">
    <db:para>
      <xsl:apply-templates mode="content_para"/>
    </db:para>
  </xsl:template>
  
  <xsl:template mode="content" match="raw">
    <xsl:choose>
      <xsl:when test="parent::node()[1]/self::quote">
        <db:programlisting>
          <xsl:value-of select="text()"/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <!-- Cheating, right. But we have no choice here. -->
        <db:programlisting role="raw-html">
          <xsl:apply-templates mode="content_para"/>
        </db:programlisting>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content" match="contents | keyword">
    <!-- Used for a table of contents, can be skipped. -->
  </xsl:template>
  
  <xsl:template mode="content" match="relation">
    <!-- Handled as extended links in the <info> tag. -->
  </xsl:template>
  
  <xsl:template mode="content" match="codeline"/>
  
  <xsl:template mode="content" match="description">
    <!-- Let templates flow through description to simplify code to handle classes. -->
    <xsl:apply-templates mode="content"/>
  </xsl:template>
  
  <xsl:template mode="content" match="section">
    <xsl:if test="count(child::node()) > 0">
      <db:section>
        <xsl:if test="@id">
          <xsl:attribute name="xml:id" select="tc:sanitise-xml-id(@id)"/>
        </xsl:if>
        
        <xsl:apply-templates mode="content"/>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content" match="see-also">
    <xsl:param name="allowSeeAlso" as="xs:boolean" select="true()"/>
    
    <xsl:if test="$allowSeeAlso and count(link) > 0">
      <xsl:variable name="content">
        <db:para>
          <db:emphasis role="bold">See Also:</db:emphasis>
          <db:simplelist type="vert">
            <xsl:for-each select="link">
              <db:member>
                <xsl:apply-templates mode="content_para" select="."/>
              </db:member>
            </xsl:for-each>
          </db:simplelist>
        </db:para>
      </xsl:variable>
      
      <xsl:choose>
        <xsl:when test="preceding-sibling::node()[1][self::section or self::generatedlist]">
          <db:section>
            <db:title>See Also</db:title>
            <xsl:copy-of select="$content"></xsl:copy-of>
          </db:section>
        </xsl:when>
        <xsl:otherwise>
          <xsl:copy-of select="$content"></xsl:copy-of>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content" match="heading">
    <db:title>
      <xsl:apply-templates mode="content_para"/>
    </db:title>
  </xsl:template>
  
  <xsl:template mode="content" match="generatedlist">
    <xsl:variable name="currentDocument" select="string(base-uri())" as="xs:string"/>
    <xsl:choose>
      <xsl:when test="count(child::node()) > 0">
        <!-- Qdoc already generated the whole list, yay! -->
        <xsl:apply-templates mode="content"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- Do the whole list by ourselves... -->
        <xsl:variable name="split" select="tokenize(@contents, ' ')" as="xs:string+"/>
        <xsl:variable name="type" select="$split[1]" as="xs:string"/>
        <xsl:variable name="argument" as="xs:string?">
          <xsl:if test="count($split) > 1">
            <xsl:value-of select="$split[2]"/>
          </xsl:if>
        </xsl:variable>
        
        <!-- Depending on the type of list to generate, do different things. -->
        <!-- Never check for correctness, qdoc has already done this. -->
        <!-- (Things would just crash if an argument is needed but not provided.) -->
        <xsl:choose>
          <xsl:when test="$type = 'examplefiles'">
            <!-- List the example files that match the regular expression. -->
            
            <!-- First, find back the folder containing the example files. -->
            <xsl:variable name="oneFileAtRandomTag" select="(preceding::quotefromfile[1] union preceding::quotefile[1] union preceding::snippet[1])[1]"/>
            <xsl:variable name="oneFileAtRandom" as="xs:string" select="if ($oneFileAtRandomTag/@location) then $oneFileAtRandomTag/@location else $oneFileAtRandomTag/text()"/>
            <xsl:variable name="oneFileAtRandomSplitPath" select="tokenize($oneFileAtRandom, '/')" as="xs:string*"/>
            <xsl:variable name="oneFileAtRandomFullPath" select="tc:find-file-path(ancestor::description/@path, $oneFileAtRandom)" as="xs:string"/>
            <xsl:variable name="oneFileAtRandomSplitFullPath" select="tokenize($oneFileAtRandomFullPath, '/')" as="xs:string*"/>
            
            <!-- Go back one level to ensure the needed files are always found. -->
            <xsl:variable name="folder" select="string-join($oneFileAtRandomSplitFullPath[position() &lt; last() - 1], '/')" as="xs:string"/>
            <xsl:variable name="printedFolder" select="string-join($oneFileAtRandomSplitPath[position() &lt; last()], '/')" as="xs:string"/>
            <xsl:variable name="hiddenFolder">
              <xsl:variable name="splitFolder" select="tokenize($folder, '/')"/>
              <xsl:variable name="splitPrintedFolder" select="tokenize($printedFolder, '/')"/>
              <xsl:value-of select="string-join($splitFolder[position() &lt; count($splitFolder) - count($splitPrintedFolder)], '/')"/>
            </xsl:variable>
            
            <!-- Iterate through the examples folder, retrieve the paths (if they match the condition from $argument). -->
            <xsl:variable name="files" as="xs:string*">
              <xsl:variable name="filesInDir" select="for $f in collection(concat('file:///', $folder, '?metadata=yes&amp;recurse=yes')) return translate($f?path, '\\', '/')" as="xs:string*"/>
              <xsl:for-each select="$filesInDir">
                <xsl:if test="matches(., $argument)">
                  <xsl:value-of select="replace(., concat($hiddenFolder, '/'), '')"/>
                </xsl:if>
              </xsl:for-each>
            </xsl:variable>
            
            <!-- Print what is needed. -->
            <db:para>
              Files: 
            </db:para>
            <db:itemizedlist>
              <xsl:for-each select="$files">
                <xsl:sort/>
                <db:listitem>
                  <db:para>
                    <xsl:variable name="correspondingWebXML" as="xs:string">
                      <xsl:variable name="name" select="tokenize($currentDocument, '/')[last()]"/>
                      <xsl:variable name="splitExtension" select="tokenize($name, '\.')"/>
                      <xsl:variable name="splitName" select="tokenize($splitExtension[1], '-')"/>
                      
                      <xsl:variable name="sourceFile" select="concat('chapter', substring-after(., 'chapter'))"/>
                      
                      <xsl:value-of select="concat(string-join($splitName[position() &lt; last()], '-'), '-', translate($sourceFile, '/.', '--'), '.webxml')"/>
                    </xsl:variable>
                    <db:link xlink:href="{$correspondingWebXML}">
                      <xsl:value-of select="."/>
                    </db:link>
                  </db:para>
                </db:listitem>
              </xsl:for-each>
            </db:itemizedlist>
          </xsl:when>
          <xsl:when test="$type = 'overviews'">
            <db:informaltable>
              <db:tbody>
                <xsl:variable name="overviews" as="map(xs:string, xs:string+)">
                  <xsl:map>
                    <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                      <xsl:if test="./WebXML/document/group">
                        <xsl:variable name="root" select="./WebXML/document/group" as="element(group)"/>
                        <xsl:map-entry key="string($root/@name)" select="(string($root/@title), string($root/@brief))"/>
                      </xsl:if>
                    </xsl:for-each>
                  </xsl:map>
                </xsl:variable>
                <xsl:for-each select="map:keys($overviews)">
                  <xsl:sort/>
                  
                  <db:tr>
                    <db:td>
                      <db:link xlink:href="{concat(lower-case(.), '.webxml')}" xlink:title="map:get($overviews, .)[1]" xrefstyle="class" annotations="{.}">
                        <xsl:value-of select="map:get($overviews, .)[1]"/>
                      </db:link>
                    </db:td>
                    <db:td>
                      <xsl:value-of select="map:get($overviews, .)[2]"/>
                    </db:td>
                  </db:tr>
                </xsl:for-each>
              </db:tbody>
            </db:informaltable>
          </xsl:when>
          <xsl:when test="$type = 'related'">
            <xsl:if test="not(/WebXML/document/child::node()[1]/@members) or /WebXML/document/child::node()[1]/@members=''">
              <xsl:message>WARNING: generatedlist not implemented with type related and no members attribute.</xsl:message>
              <!-- Would need to go through all the documents and check whether the groups match. -->
            </xsl:if>
            
            <db:informaltable>
              <db:tbody>
                <xsl:for-each select="tokenize(/WebXML/document/child::node()[1]/@members, ',')">
                  <xsl:sort/>
                  
                  <xsl:try>
                    <xsl:variable name="soughtFile" as="xs:string" select="concat($local-folder, lower-case(.), '.webxml')"/>
                    <xsl:variable name="soughtDocument" select="document($soughtFile)" as="node()"/>
                    <db:tr>
                      <db:td>
                        <db:link xlink:href="{concat(lower-case(.), '.webxml')}" xlink:title="{.}" xrefstyle="class" annotations="{.}">
                          <xsl:value-of select="."/>
                        </db:link>
                      </db:td>
                      <db:td>
                        <xsl:value-of select="$soughtDocument/WebXML/document/child::node()[1]/@brief"/>
                      </db:td>
                    </db:tr>
                    
                    <xsl:catch>
                      <!-- Sometimes, there is something strange in @members: just skip -->
                      <!-- (does not appear in the official documentation). -->
                    </xsl:catch>
                  </xsl:try>
                </xsl:for-each>
              </db:tbody>
            </db:informaltable>
          </xsl:when>
          <xsl:when test="$type = 'annotatedclasses'">
            <db:informaltable>
              <db:tbody>
                <xsl:variable name="classes" as="map(xs:string, xs:string)">
                  <xsl:map>
                    <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                      <xsl:if test="./WebXML/document/class">
                        <xsl:variable name="root" select="./WebXML/document/class" as="element(class)"/>
                        <xsl:variable name="className" select="if ($root/@fullname) then $root/@fullname else $root/@name" as="xs:string"/>
                        <xsl:map-entry key="$className" select="string($root/@brief)"/>
                      </xsl:if>
                    </xsl:for-each>
                  </xsl:map>
                </xsl:variable>
                <xsl:for-each select="map:keys($classes)">
                  <xsl:sort/>
                  
                  <db:tr>
                    <db:td>
                      <db:link xlink:href="{concat(lower-case(.), '.webxml')}" xlink:title="{.}" xrefstyle="class" annotations="{.}">
                        <xsl:value-of select="."/>
                      </db:link>
                    </db:td>
                    <db:td>
                      <xsl:value-of select="map:get($classes, .)"/>
                    </db:td>
                  </db:tr>
                </xsl:for-each>
              </db:tbody>
            </db:informaltable>
          </xsl:when>
          <xsl:when test="$type = 'classes'">
            <xsl:variable name="classes" as="map(xs:string, xs:string)">
              <xsl:map>
                <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                  <xsl:if test="./WebXML/document/class">
                    <xsl:variable name="root" select="./WebXML/document/class" as="element(class)"/>
                    <xsl:variable name="className" select="if ($root/@fullname) then $root/@fullname else $root/@name" as="xs:string"/>
                    <xsl:map-entry key="$className" select="string($root/@brief)"/>
                  </xsl:if>
                </xsl:for-each>
              </xsl:map>
            </xsl:variable>
            <xsl:for-each-group select="map:keys($classes)" group-by="substring(string(.), 2, 1)">
              <xsl:sort/>
              
              <db:section>
                <db:title>
                  <xsl:value-of select="current-grouping-key()"/>
                </db:title>
                  
                <db:informaltable>
                  <db:tbody>
                    <xsl:for-each select="current-group()">
                      <xsl:sort/>
                      
                      <db:tr>
                        <db:td>
                          <db:link xlink:href="{concat(lower-case(.), '.webxml')}" xlink:title="{.}" xrefstyle="class" annotations="{.}">
                            <xsl:value-of select="."/>
                          </db:link>
                        </db:td>
                        <db:td>
                          <xsl:value-of select="map:get($classes, .)"/>
                        </db:td>
                      </db:tr>
                    </xsl:for-each>
                  </db:tbody>
                </db:informaltable>
              </db:section>
            </xsl:for-each-group>
          </xsl:when>
          <xsl:when test="$type = 'functionindex'">
            <xsl:variable name="functions" as="map(xs:string, xs:string)">
              <xsl:map>
                <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                  <xsl:if test="./WebXML/document/class">
                    <xsl:variable name="root" select="./WebXML/document/class" as="element(class)"/>
                    <xsl:variable name="className" select="if ($root/@fullname) then $root/@fullname else $root/@name" as="xs:string"/>
                    <xsl:for-each select="$root/function">
                      <xsl:variable name="functionName" select="if (@fullname) then @fullname else @name"/>
                      <xsl:map-entry key="concat($functionName, '___', $className, '___', count(preceding-sibling::*))" select="$className"/>
                    </xsl:for-each>
                  </xsl:if>
                </xsl:for-each>
              </xsl:map>
            </xsl:variable>
            <xsl:for-each-group select="map:keys($functions)" group-by="substring(string(.), 1, 1)">
              <xsl:sort/>
              
              <db:section>
                <db:title>
                  <xsl:value-of select="current-grouping-key()"/>
                </db:title>
                  
                <db:itemizedlist>
                  <xsl:for-each-group select="current-group()" group-by="substring-before(., '___')">
                    <xsl:sort/>
                    
                    <db:listitem>
                      <db:para>
                        <xsl:value-of select="substring-before(., '___')"/>
                        <xsl:text>: </xsl:text>
                        <xsl:for-each select="current-group()">
                          <xsl:variable name="containingClass" select="map:get($functions, .)"/>
                          <db:link xlink:href="{concat(lower-case($containingClass), '.webxml')}" xlink:title="{$containingClass}" xrefstyle="class" annotations="{$containingClass}">
                            <xsl:value-of select="$containingClass"/>
                          </db:link>
                          <xsl:if test="position() &lt; last()">
                            <xsl:text>&#0160;</xsl:text>
                          </xsl:if>
                        </xsl:for-each>
                      </db:para>
                    </db:listitem>
                  </xsl:for-each-group>
                </db:itemizedlist>
              </db:section>
            </xsl:for-each-group>
          </xsl:when>
          <xsl:when test="$type = 'cpp-modules'">
            <xsl:variable name="modules" as="map(xs:string, xs:string)">
              <xsl:map>
                <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                  <xsl:if test="./WebXML/document/module">
                    <xsl:variable name="root" select="./WebXML/document/module" as="element(module)"/>
                    <xsl:variable name="moduleName" select="if ($root/@fullname) then $root/@fullname else $root/@name" as="xs:string"/>
                    <xsl:map-entry key="$moduleName" select="string($root/@brief)"/>
                  </xsl:if>
                </xsl:for-each>
              </xsl:map>
            </xsl:variable>
            <xsl:for-each-group select="map:keys($modules)" group-by="substring(string(.), 3, 2)">
              <xsl:sort/>
              
              <db:section>
                <db:title>
                  <xsl:value-of select="current-grouping-key()"/>
                </db:title>
                
                <db:informaltable>
                  <db:tbody>
                    <xsl:for-each select="current-group()">
                      <xsl:sort/>
                      
                      <db:tr>
                        <db:td>
                          <db:link xlink:href="{concat(lower-case(.), '.webxml')}" xlink:title="{.}" xrefstyle="class" annotations="{.}">
                            <xsl:value-of select="."/>
                          </db:link>
                        </db:td>
                        <db:td>
                          <xsl:value-of select="map:get($modules, .)"/>
                        </db:td>
                      </db:tr>
                    </xsl:for-each>
                  </db:tbody>
                </db:informaltable>
              </db:section>
            </xsl:for-each-group>
          </xsl:when>
          <xsl:when test="$type = 'qml-modules'">
            <xsl:variable name="modules" as="map(xs:string, xs:string)">
              <xsl:map>
                <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                  <xsl:if test="./WebXML/document/qmlmodule">
                    <xsl:variable name="root" select="./WebXML/document/qmlmodule" as="element(qmlmodule)"/>
                    <xsl:variable name="moduleName" select="if ($root/@fullname) then $root/@fullname else $root/@name" as="xs:string"/>
                    <xsl:map-entry key="concat($moduleName, '___', $root/@qml-module-version)" select="string($root/@brief)"/>
                  </xsl:if>
                </xsl:for-each>
              </xsl:map>
            </xsl:variable>
            <xsl:for-each-group select="map:keys($modules)" group-by="substring(string(.), 2, 1)">
              <xsl:sort/>
              
              <db:section>
                <db:title>
                  <xsl:value-of select="current-grouping-key()"/>
                </db:title>
                  
                <db:informaltable>
                  <db:tbody>
                    <xsl:for-each select="current-group()">
                      <xsl:sort/>
                      
                      <db:tr>
                        <db:td>
                          <db:link xlink:href="{concat(lower-case(substring-before(., '___')), '-qmlmodule.webxml')}" xlink:title="{substring-before(., '___')}" xrefstyle="class" annotations="{substring-before(., '___')}">
                            <xsl:value-of select="substring-before(., '___')"/>
                          </db:link>
                        </db:td>
                        <db:td>
                          <xsl:value-of select="map:get($modules, .)"/>
                        </db:td>
                      </db:tr>
                    </xsl:for-each>
                  </db:tbody>
                </db:informaltable>
              </db:section>
            </xsl:for-each-group>
          </xsl:when>
          <xsl:when test="$type = 'namespaces'">
            <xsl:variable name="namespaces" as="map(xs:string, xs:string)">
              <xsl:map>
                <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))">
                  <xsl:if test="./WebXML/document/namespace">
                    <xsl:variable name="root" select="./WebXML/document/namespace" as="element(namespace)"/>
                    <xsl:variable name="nsName" select="if ($root/@fullname) then $root/@fullname else $root/@name" as="xs:string"/>
                    <xsl:map-entry key="$nsName" select="string($root/@brief)"/>
                  </xsl:if>
                </xsl:for-each>
              </xsl:map>
            </xsl:variable>
            <xsl:for-each-group select="map:keys($namespaces)" group-by="substring(string(.), 2, 1)">
              <xsl:sort/>
              
              <db:section>
                <db:title>
                  <xsl:value-of select="current-grouping-key()"/>
                </db:title>
                
                <db:informaltable>
                  <db:tbody>
                    <xsl:for-each select="current-group()">
                      <xsl:sort/>
                      
                      <db:tr>
                        <db:td>
                          <db:link xlink:href="{concat(lower-case(.), '.webxml')}" xlink:title="{.}" xrefstyle="class" annotations="{.}">
                            <xsl:value-of select="substring-before(., '___')"/>
                          </db:link>
                        </db:td>
                        <db:td>
                          <xsl:value-of select="map:get($namespaces, .)"/>
                        </db:td>
                      </db:tr>
                    </xsl:for-each>
                  </db:tbody>
                </db:informaltable>
              </db:section>
            </xsl:for-each-group>
          </xsl:when>
          <xsl:when test="$type = 'groupsbymodule'">
            <!-- TODO: Currently not implemented, requires attributions -->
          </xsl:when>
          <xsl:otherwise>
            <xsl:message>WARNING: generatedlist type not implemented: <xsl:value-of select="$type"/></xsl:message>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content" match="para">
    <xsl:variable name="targetId" as="xs:string?">
      <xsl:variable name="potentialTargets" select="(preceding-sibling::node()[1]/self::target union following-sibling::node()[1]/self::target)"/>
      <xsl:variable name="targetIdSub" select="if ($potentialTargets) then $potentialTargets[1]/@name else ''" as="xs:string?"/>
      <xsl:value-of select="if ($targetIdSub and not($targetIdSub='')) then $targetIdSub else ''"/>
    </xsl:variable>
    
    <xsl:choose>
      <xsl:when test="child::node()[1]/text()='Note:'">
        <db:note>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          <db:para>
            <xsl:apply-templates mode="content_para"/>
          </db:para>
        </db:note>
      </xsl:when>
      <xsl:when test="child::node()[1]/text()='Important:'">
        <db:note>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          <db:para>
            <xsl:apply-templates mode="content_para"/>
          </db:para>
        </db:note>
      </xsl:when>
      <xsl:when test="para or generatedlist">
        <!-- WTF? A paragraph that contains paragraphs? Yes, indeed! -->
        <db:para>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          <xsl:apply-templates mode="content_para">
            <xsl:with-param name="silent" select="true()"/>
          </xsl:apply-templates>
        </db:para>
        
        <xsl:apply-templates mode="content">
          <xsl:with-param name="silent" select="true()"/>
        </xsl:apply-templates>
      </xsl:when>
      <xsl:otherwise>
        <db:para>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          <xsl:apply-templates mode="content_para"/>
        </db:para>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content" match="legalese">
    <db:note>
      <xsl:apply-templates mode="content"/>
    </db:note>
  </xsl:template>
  
  <xsl:function name="tc:file-exists" as="xs:boolean">
    <xsl:param name="filename" as="xs:string"/>
    <xsl:value-of select="unparsed-text-available(concat('file:///', $filename))" />
  </xsl:function>
  <xsl:function name="tc:find-file-path">
    <xsl:param name="sourceFilePath" as="xs:string"/>
    <xsl:param name="fileToQuote" as="xs:string"/>
    
    <xsl:variable name="sourceFilePathSplit" select="tokenize($sourceFilePath, '/')" as="xs:string*"/>
    
    <xsl:if test="$sourceFilePath[last()]=''">      
      <xsl:message terminate="yes">ERROR: tc:find-file-path received a folder path instead of a file path as $sourceFilePath. </xsl:message>
    </xsl:if>
    
    <xsl:variable name="sourceFolder" select="replace($sourceFilePath, replace($sourceFilePathSplit[last()], '\.', '\\.'), '')" as="xs:string"/>
    <xsl:variable name="filePathTentative1" select="concat($sourceFolder, $fileToQuote)"/>
    <xsl:variable name="filePathTentative2" select="concat($sourceFolder, '../snippets/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative3" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 3], '/'), '/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative4" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative5" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 3], '/'), '/', replace($fileToQuote, 'examples/', 'examples/corelib/'))"/>
    <xsl:variable name="filePathTentative6" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/examples/widgets/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative7" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 3], '/'), '/examples/qml/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative8" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 1], '/'), '/doc/snippets/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative9" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 2], '/'), '/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative10" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 6], '/'), '/examples/qml/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative11" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 5], '/'), '/examples/qml/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative12" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 2], '/'), '/snippets/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative13" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/examples/quickcontrols/controls/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative14" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/examples/quickcontrols/dialogs/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative15" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/examples/quickcontrols/extras/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative16" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/examples/', $sourceFilePathSplit[position() = last() - 3], '/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative17" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 4], '/'), '/examples/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative18" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 1], '/'), '/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative19" select="concat(string-join($sourceFilePathSplit[position() &lt; last()], '/'), '/doc/snippets/', $fileToQuote)"/>
    <xsl:variable name="filePathTentative20" select="concat(string-join($sourceFilePathSplit[position() &lt; last() - 3], '/'), '/examples/quick/', $fileToQuote)"/>
    
    <xsl:choose>
      <xsl:when test="tc:file-exists($filePathTentative1)">
        <xsl:value-of select="$filePathTentative1"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative2)">
        <xsl:value-of select="$filePathTentative2"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative3)">
        <xsl:value-of select="$filePathTentative3"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative4)">
        <xsl:value-of select="$filePathTentative4"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative5)">
        <xsl:value-of select="$filePathTentative5"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative6)">
        <xsl:value-of select="$filePathTentative6"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative7)">
        <xsl:value-of select="$filePathTentative7"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative8)">
        <xsl:value-of select="$filePathTentative8"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative9)">
        <xsl:value-of select="$filePathTentative9"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative10)">
        <xsl:value-of select="$filePathTentative10"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative11)">
        <xsl:value-of select="$filePathTentative11"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative12)">
        <xsl:value-of select="$filePathTentative12"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative13)">
        <xsl:value-of select="$filePathTentative13"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative14)">
        <xsl:value-of select="$filePathTentative14"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative15)">
        <xsl:value-of select="$filePathTentative15"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative16)">
        <xsl:value-of select="$filePathTentative16"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative17)">
        <xsl:value-of select="$filePathTentative17"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative18)">
        <xsl:value-of select="$filePathTentative18"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative19)">
        <xsl:value-of select="$filePathTentative19"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($filePathTentative20)">
        <xsl:value-of select="$filePathTentative20"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Unable to find the correct path for <xsl:value-of select="$fileToQuote"/></xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  <xsl:function name="tc:load-file">
    <xsl:param name="filename" as="xs:string"/>
    <!-- Read the file and get rid of the \r. -->
    <xsl:try select="replace(unparsed-text(concat('file:///', $filename)), codepoints-to-string(13), '')">
      <xsl:catch>
        <xsl:message>WARNING: File <xsl:value-of select="$filename"/> could not be loaded.</xsl:message>
      </xsl:catch>
    </xsl:try>
  </xsl:function>
  <xsl:function name="tc:parse-snippet">
    <xsl:param name="filename" as="xs:string"/>
    <xsl:param name="identifier" as="xs:string"/>
    
    <xsl:variable name="sanitisedIdentifier" select="replace($identifier, '\+', '\\+')"/>
    
    <xsl:variable name="fileContents" select="tc:load-file($filename)"/>
    <xsl:variable name="onlyInterestingSnippet" select="tokenize($fileContents, concat('//! \[', $sanitisedIdentifier, '\]'))[2]"/>
    <xsl:variable name="slicedSnippet" select="tokenize($onlyInterestingSnippet, '\n')"/>
    <xsl:variable name="filteredSlicedSnippet" select="$slicedSnippet[not(starts-with(., '//! '))]"/>
    <xsl:variable name="filteredSnippet" select="string-join($filteredSlicedSnippet, codepoints-to-string(10))"/>
    
    <xsl:value-of select="$filteredSnippet"/>
  </xsl:function>
  <xsl:function name="tc:generate-indent">
    <xsl:param name="indent" as="xs:integer"/>
    <xsl:value-of select="string-join((for $i in 1 to $indent return ' '))"/>
  </xsl:function>
  <xsl:function name="tc:gather-snippet">
    <xsl:param name="snippetNode" as="node()"/>
    
    <!-- Gather the whole snippet: parts of text and dots. -->
    <!-- Do it by recursion: treat the current node, recurse on the next one if it still belongs to the snippet. -->
    <xsl:variable name="recurse" select="if ($snippetNode/following-sibling::node()[1]/self::snippet or $snippetNode/following-sibling::node()[1]/self::dots) then tc:gather-snippet($snippetNode/following-sibling::node()[1]) else ''" as="xs:string"/>
    <xsl:choose>
      <xsl:when test="$snippetNode/self::snippet and $snippetNode/@path">
        <xsl:value-of select="concat(tc:parse-snippet($snippetNode/@path, $snippetNode/@identifier), $recurse)"/>
      </xsl:when>
      <xsl:when test="$snippetNode/self::snippet and $snippetNode/@location">
        <xsl:value-of select="concat(tc:parse-snippet(tc:find-file-path($snippetNode/ancestor::description/@path, $snippetNode/@location), $snippetNode/@identifier), $recurse)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="concat(tc:generate-indent($snippetNode/@indent), $recurse)"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:template mode="content" match="snippet[not(preceding-sibling::node()[1]/self::snippet or preceding-sibling::node()[1]/self::dots)]">
    <db:programlisting>
      <xsl:value-of select="tc:gather-snippet(.)"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content" match="snippet[preceding-sibling::node()[1]/self::snippet or preceding-sibling::node()[1]/self::dots]">
    <!-- Should be handled with the rest of the snippet. -->
  </xsl:template>
  
  <xsl:template mode="content" match="dots">
    <!-- Should be handled with snippets or quotefromfile. Except in rare cases, where there are only dots -->
    <!-- (several such tags may follow; as for the rest, only output something for the first one). -->
    <!-- Quite specific case (barely happens, except in QDoc documentation). -->
    <xsl:if test="not(preceding::*[1]/(self::printline | self::printto | self::printuntil | self::skipline | self::skipto | self::skipuntil | self::dots))">
      <db:programlisting>
        <xsl:value-of select="tc:generate-indent(@indent)"/>
        <xsl:text>...</xsl:text>
        
        <xsl:for-each select="following-sibling::dots">
          <!-- Not very generic select: all dots that follow with the same parent. -->
          <!-- Sufficient to handle the few cases where this specific bullshit happens. -->
          <xsl:text>&#xa;</xsl:text>
          <xsl:value-of select="tc:generate-indent(@indent)"/>
          <xsl:text>...</xsl:text>
        </xsl:for-each>
      </db:programlisting>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content" match="quotefile">
    <!-- No real path provided for quotefile, so must make up one. -->
    <db:programlisting>
      <xsl:value-of select="tc:load-file(tc:find-file-path(ancestor::description/@path, text()))"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:function name="tc:printfromfile-get-absolute-line-after-text">
    <xsl:param name="quotefromfileLines" as="xs:string*"/>
    <xsl:param name="soughtText" as="xs:string?"/>
    <xsl:param name="startAt" as="xs:integer"/>
    
    <xsl:choose>
      <xsl:when test="$soughtText">
        <!-- Usual case: looking for something. -->
        <xsl:variable name="matchedLines" as="xs:boolean*">
          <xsl:for-each select="$quotefromfileLines[position() >= $startAt]">
            <xsl:choose>
              <xsl:when test="starts-with($soughtText, '/') and ends-with($soughtText, '/')">
                <!-- Regular expression! Use the Java syntax (hence the ! flag) to avoid translating too many things (like \b for word boundary). -->
                <xsl:value-of select="matches(., substring($soughtText, 2, string-length($soughtText) - 2), '!')"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="contains(., $soughtText)"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </xsl:variable>
        
        <!-- Indexing starts at 1: if there is a match at the first line, index-of returns 1, -->
        <!-- but the function must return $startAt. -->
        <xsl:value-of select="$startAt + index-of($matchedLines, true())[1] - 1"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- Depraved developer case: looking for... nothing at all. Assume this will go until the end. -->
        <xsl:value-of select="count($quotefromfileLines)"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-skip" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    <xsl:value-of select="$currentNode[self::skipline] or $currentNode[self::skipto] or $currentNode[self::skipuntil]"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-print" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="$currentNode/self::printline or $currentNode/self::printto or $currentNode/self::printuntil"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-dots" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="boolean($currentNode/self::dots)"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-codeline" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="boolean($currentNode/self::codeline)"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-find-all-previous-nodes-until-printfromfile" as="node()*">
    <xsl:param name="currentNode" as="node()?"/>
    
    <xsl:choose>
      <!-- Base cases: no node, do nothing; printfromfile, last node to return. -->
      <xsl:when test="not($currentNode)"/>
      <xsl:when test="$currentNode/self::printfromfile">
        <xsl:sequence select="$currentNode"/>
      </xsl:when>
      <!-- Inductive cases, to build up the sequence (in order) with the elements that match. -->
      <xsl:when test="$currentNode/self::printline or $currentNode/self::printto or $currentNode/self::printuntil or $currentNode/self::skipline or $currentNode/self::skipline or $currentNode/self::skipto or $currentNode/self::skipuntil">
        <xsl:sequence select="$currentNode union tc:printfromfile-find-all-previous-nodes-until-printfromfile($currentNode/preceding::node()[1])"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="tc:printfromfile-find-all-previous-nodes-until-printfromfile($currentNode/preceding::node()[1])"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  <xsl:function name="tc:printfromfile-find-line-of" as="xs:integer">
    <xsl:param name="currentNode" as="node()?"/>
    <xsl:param name="quotefromfileSequenceLines" as="xs:string*"/>
    
    <xsl:choose>
      <!-- Base case: printfromfile, last node to process. -->
      <xsl:when test="$currentNode/self::quotefromfile">
        <xsl:value-of select="1"/>
      </xsl:when>
      <!-- Inductive cases, to build up the sequence (in order) with the elements that match. -->
      <xsl:otherwise>
        <xsl:variable name="previousInterestingNode" select="($currentNode/preceding::node()/(self::quotefromfile union self::printline union self::printto union self::printuntil union self::skipline union self::skipto union self::skipuntil union self::dots))[last()]"/>
        <xsl:variable name="recurse" select="tc:printfromfile-find-line-of($previousInterestingNode, $quotefromfileSequenceLines)"/>
        
        <xsl:choose>
          <!-- Advance $startAt until the end of the part to skip, then recurse. -->
          <xsl:when test="tc:printfromfile-is-skip($currentNode)">
            <xsl:variable name="lineMatched" select="tc:printfromfile-get-absolute-line-after-text($quotefromfileSequenceLines, $currentNode/text(), $recurse)"/>
            <xsl:value-of>
              <xsl:choose>
                <xsl:when test="$currentNode/self::skipline or $currentNode/self::skipuntil">
                  <xsl:value-of select="$lineMatched + 1"/>
                </xsl:when>
                <xsl:when test="$currentNode/self::skipto">
                  <xsl:value-of select="$lineMatched"/>
                </xsl:when>
              </xsl:choose>
            </xsl:value-of>
          </xsl:when>
          <!-- Print from $startAt (included) until the last line that must be printed, and recurse. -->
          <xsl:when test="tc:printfromfile-is-print($currentNode)">
            <xsl:variable name="lineMatched" select="tc:printfromfile-get-absolute-line-after-text($quotefromfileSequenceLines, $currentNode/text(), $recurse)"/>
            <xsl:choose>
              <xsl:when test="$currentNode/self::printline">
                <xsl:value-of select="$recurse + 1"/>
              </xsl:when>
              <xsl:when test="$currentNode/self::printto">
                <xsl:value-of select="$lineMatched + 1"/>
              </xsl:when> 
              <xsl:when test="$currentNode/self::printuntil">
                <xsl:value-of select="$lineMatched"/>
              </xsl:when> 
            </xsl:choose>
          </xsl:when>
          <!-- Base case: nothing to print, the current node is not about printfromfile. -->
          <xsl:otherwise>
            <xsl:value-of select="$recurse"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  <xsl:function name="tc:printfromfile-print-content" as="xs:string">
    <xsl:param name="currentNode" as="node()"/>
    <xsl:param name="quotefromfileSequenceLines" as="xs:string*"/>
    
    <xsl:variable name="previousInterestingNode" select="($currentNode/preceding::node()/(self::quotefromfile union self::printline union self::printto union self::printuntil union self::skipline union self::skipto union self::skipuntil))[last()]"/>
    <xsl:variable name="initialLine" as="xs:integer" select="tc:printfromfile-find-line-of($previousInterestingNode, $quotefromfileSequenceLines)"/> 
    <xsl:value-of select="tc:printfromfile-print-content($currentNode, $quotefromfileSequenceLines, $initialLine)"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-print-content" as="xs:string">
    <xsl:param name="currentNode" as="node()"/>
    <xsl:param name="quotefromfileSequenceLines" as="xs:string*"/>
    <xsl:param name="startAt" as="xs:integer"/>
    
    <xsl:variable name="nextNode" as="node()?" select="$currentNode/following-sibling::node()[1]"/>
    
    <xsl:choose>
      <!-- Advance $startAt until the end of the part to skip, then recurse. -->
      <xsl:when test="tc:printfromfile-is-skip($currentNode)">
        <xsl:variable name="lineMatched" select="tc:printfromfile-get-absolute-line-after-text($quotefromfileSequenceLines, $currentNode/text(), $startAt)"/>
        <xsl:variable name="newStartAt">
          <xsl:choose>
            <xsl:when test="$currentNode/self::skipline or $currentNode/self::skipuntil">
              <xsl:value-of select="$lineMatched + 1"/>
            </xsl:when>
            <xsl:when test="$currentNode/self::skipto">
              <xsl:value-of select="$lineMatched"/>
            </xsl:when>
          </xsl:choose>
        </xsl:variable>
        <xsl:value-of select="if ($nextNode) then tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, $newStartAt) else ''"/>
      </xsl:when>
      <!-- Generate properly indented dots. -->
      <xsl:when test="tc:printfromfile-is-dots($currentNode)">
        <xsl:value-of select="concat(codepoints-to-string(10), tc:generate-indent($currentNode/@indent), '...')"/>
      </xsl:when>
      <!-- Print from $startAt (included) until the last line that must be printed, and recurse. -->
      <xsl:when test="tc:printfromfile-is-print($currentNode)">
        <xsl:variable name="lineMatched" select="tc:printfromfile-get-absolute-line-after-text($quotefromfileSequenceLines, $currentNode/text(), $startAt)"/>
        <xsl:choose>
          <xsl:when test="$currentNode/self::printline">
            <xsl:variable name="currentLine" select="$quotefromfileSequenceLines[position() = xs:integer($lineMatched)]"/>
            <xsl:variable name="recurse" select="if ($nextNode) then tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, xs:integer($lineMatched + 1)) else ''"/>
            
            <xsl:if test="not(contains($currentLine, $currentNode/text()))">
              <xsl:message>CONSISTENCY CHECK WARNING: printfromfile-print-content returning a line not containing the requested text (maybe a problem in the original documentation).</xsl:message>
            </xsl:if>
            
            <xsl:value-of select="concat($currentLine, $recurse)"/>
          </xsl:when>
          <xsl:when test="$currentNode/self::printto">
            <xsl:variable name="endAt" select="xs:integer($lineMatched - 1)"/>
            <xsl:variable name="currentBlock" select="string-join($quotefromfileSequenceLines[position() = ($startAt to $endAt)], codepoints-to-string(10))"/>
            <xsl:variable name="recurse" select="if ($nextNode) then tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, xs:integer($lineMatched + 1)) else ''"/>
            <xsl:value-of select="concat($currentBlock, $recurse)"/>
          </xsl:when> 
          <xsl:when test="$currentNode/self::printuntil">
            <xsl:variable name="endAt" select="$lineMatched"/>
            <xsl:variable name="currentBlock" select="string-join($quotefromfileSequenceLines[position() = ($startAt to $endAt)], codepoints-to-string(10))"/> 
            <xsl:variable name="recurse" select="if ($nextNode) then tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, $lineMatched) else ''"/>
            <xsl:value-of select="concat($currentBlock, $recurse)"/>
          </xsl:when> 
        </xsl:choose>
      </xsl:when>
      <!-- codeline just prints a blank line, and recurse. -->
      <xsl:when test="tc:printfromfile-is-codeline($currentNode)">
        <xsl:variable name="recurse" select="if ($nextNode) then tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, $startAt) else ''"/>
        <xsl:value-of select="concat(codepoints-to-string(10), $recurse)"/>
      </xsl:when>
      <!-- Base case: nothing to print, the current node is not about printfromfile. -->
      <xsl:otherwise>
        <xsl:value-of select="''"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:template mode="content" match="quotefromfile">
    <!-- Don't do anything in this: this tag will be retrieved when needed (skipto, printuntil, and family). -->
  </xsl:template>
  <xsl:template mode="content" match="printline | printto | printuntil">
    <!-- Work through the intricacies behind quotefromfile. -->
    <!-- Retrieve the code from the last quotefromfile. -->
    <!-- Split it according to a series of tags: -->
    <!--     printline, printto, printuntil, skipline, skipto, skipuntil -->
    
    <!-- As QDoc, only print something with the print* tags. -->
    <!-- Contrary to QDoc, dots are handled by the print* tags, not when meeting a dots tag -->
    <!-- (within the tc:printfromfile-print-content function); this helps ensure that -->
    <!-- only one code block is output. -->
    
    <!-- Detailed explanation of each tag: -->
    <!--   - printline:  print one line, advance cursor by one line -->
    <!--   - printto:    print until (EXcluding) the first line that matches the argument -->
    <!--   - printuntil: print until (INcluding) the first line that matches the argument -->
    <!--   - skipline:  advance cursor by one line (or more, if there are nonblank lines) -->
    <!--   - skipto:    skip until (EXcluding) the first line that matches the argument -->
    <!--   - skipuntil: skip until (INcluding) the first line that matches the argument -->
    
    <xsl:variable name="quotefromfileFile" as="xs:string" select="tc:find-file-path(ancestor::description/@path, preceding::quotefromfile[1])"/>
    <xsl:variable name="quotefromfileSequenceLines" as="xs:string*" select="tokenize(tc:load-file($quotefromfileFile), codepoints-to-string(10))"/>
    
    <xsl:variable name="previousNode" select="preceding-sibling::node()[1]" as="node()"/>
    <xsl:variable name="previousDots" as="xs:string">
      <xsl:choose>
        <xsl:when test="$previousNode[self::dots]">
          <xsl:value-of select="concat(tc:generate-indent($previousNode/@indent), '...', codepoints-to-string(10))"/>
        </xsl:when>
        <xsl:when test="$previousNode[self::codeline]">
          <xsl:value-of select="codepoints-to-string(10)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="''"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="content" select="concat($previousDots, tc:printfromfile-print-content(., $quotefromfileSequenceLines))" as="xs:string"/>
    
    <xsl:choose>
      <xsl:when test="string-length($content) > 0">
        <db:programlisting language="other">
          <xsl:value-of select="$content"/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Quote from file with no content.</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template mode="content" match="skipline | skipto | skipuntil">
    <!-- Nothing to print. -->
  </xsl:template>
  
  <xsl:template mode="content" match="code">
    <!-- Language is C++, JS, or QML. -->
    <db:programlisting language="other">
      <xsl:value-of select="."/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content" match="badcode">
    <!-- As opposed to code, language is unknown. -->
    <db:programlisting language="other" role="badcode">
      <xsl:value-of select="."/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content" match="oldcode">
    <xsl:choose>
      <xsl:when test="parent::node()/self::quote">
        <db:programlisting language="other" role="badcode">
          <xsl:value-of select="."/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <!-- oldcode-newcode pair is used to automatically generate some text. -->
        <db:para>For example, if you have code like</db:para>
        <db:programlisting>
          <xsl:value-of select="."/>
        </db:programlisting>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content" match="newcode">
    <xsl:choose>
      <xsl:when test="parent::node()/self::quote">
        <db:programlisting language="other" role="newcode">
          <xsl:value-of select="."/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <!-- oldcode-newcode pair is used to automatically generate some text. -->
        <db:para>you can rewrite it as</db:para>
        <db:programlisting language="other" role="newcode">
          <xsl:value-of select="."/>
        </db:programlisting>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content" match="quote">
    <db:blockquote>
      <xsl:apply-templates mode="content"/>
    </db:blockquote>
  </xsl:template>
  
  <xsl:template mode="content" match="list[@type='ordered']">
    <db:orderedlist>
      <xsl:apply-templates mode="content"/>
    </db:orderedlist>
  </xsl:template>
  
  <xsl:template mode="content" match="list[@type='bullet']">
    <db:itemizedlist>
      <xsl:apply-templates mode="content"/>
    </db:itemizedlist>
  </xsl:template>
  
  <xsl:template mode="content" match="list[@type='enum']">
    <db:informaltable>
      <db:thead>
        <db:tr>
          <db:th>
            <db:para>Constant</db:para>
          </db:th>
          <db:th>
            <db:para>Value</db:para>
          </db:th>
          <db:th>
            <db:para>Description</db:para>
          </db:th>
        </db:tr>
      </db:thead>
      <db:tbody>
        <xsl:for-each select="definition">
          <db:tr>
            <db:td>
              <xsl:value-of select="term/text()"/>
            </db:td>
            <db:td>
              <xsl:variable name="neededName" select="text()"/>
              <xsl:value-of select="../../../value[@name=$neededName]/@value"/>
            </db:td>
            <db:td>
              <xsl:apply-templates mode="content" select="./following-sibling::item[1]/child::node()"/>
            </db:td>
          </db:tr>
        </xsl:for-each>
      </db:tbody>
    </db:informaltable>
  </xsl:template>
  
  <xsl:template mode="content" match="list[@type='definition']">
    <db:variablelist>
      <xsl:for-each select="definition">
        <db:varlistentry>
          <db:term>
            <xsl:apply-templates mode="content_para" select="term/child::node()"/>
          </db:term>
          <xsl:choose>
            <xsl:when test="count(following-sibling::item[1]/child::node()) = 0">
              <db:listitem>
                <db:para/>
              </db:listitem>
            </xsl:when>
            <xsl:otherwise>
              <xsl:apply-templates mode="content" select="following-sibling::item[1]"/>
            </xsl:otherwise>
          </xsl:choose>
        </db:varlistentry>
      </xsl:for-each>
    </db:variablelist>
  </xsl:template>
  
  <xsl:template mode="content" match="item">
    <xsl:choose>
      <xsl:when test="parent::list">
        <db:listitem>
          <xsl:apply-templates mode="content"/>
        </db:listitem>
      </xsl:when>
      <xsl:otherwise>
          <xsl:apply-templates mode="content"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content" match="image">
    <db:mediaobject>
      <db:imageobject>
        <db:imagedata fileref="{@href}"/>
      </db:imageobject>
    </db:mediaobject>
    
    <xsl:if test="following-sibling::node()[1][self::text() or self::italic or self::bold or self::link]">
      <db:para>
        <xsl:variable name="caption" as="node()*">
          <xsl:variable name="sibling" select="following-sibling::node()"/>
          <xsl:variable name="isText" as="xs:boolean*" select="for $i in 1 to count($sibling) return $sibling[$i]/[self::text() or self::italic or self::bold or self::link]"/>
          <xsl:copy-of select="$sibling[position() &lt; index-of($isText, false())[1]]"/>
        </xsl:variable>
        <xsl:apply-templates mode="content_para" select="$caption"/>
      </db:para>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content" match="table">
    <xsl:variable name="targetId" as="xs:string?">
      <xsl:variable name="potentialTargets" select="following-sibling::node()[1]/self::target"/>
      <xsl:variable name="targetIdSub" select="if ($potentialTargets) then $potentialTargets[1]/@name else ''" as="xs:string?"/>
      <xsl:value-of select="if ($targetIdSub and not($targetIdSub='')) then $targetIdSub else ''"/>
    </xsl:variable>
    
    <!-- In some cases, a table is used while it should not really (the markup really makes no sense). -->
    <xsl:choose>
      <xsl:when test="count(header/list) = 1">
        <db:informaltable>
          <xsl:if test="not($targetId = '')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          
          <db:thead>
            <db:tr>
              <xsl:for-each select="header/list/item">
                  <db:th>
                    <xsl:apply-templates mode="content"/>
                  </db:th>
              </xsl:for-each>
            </db:tr>
          </db:thead>
          <db:tbody>
            <xsl:for-each select="row/item">
              <xsl:if test="has-children()">
                <db:tr>
                 <xsl:for-each select="list/item">
                   <db:td>
                     
                   </db:td>
                 </xsl:for-each>
                </db:tr>
              </xsl:if>
            </xsl:for-each>
          </db:tbody>
        </db:informaltable>
      </xsl:when>
      <!-- In other cases, the markup is really inconsistent, and -->
      <!-- actual content is hidden within a table (not closed properly) -->
      <xsl:when test="count(child::node()) != xs:integer(count(child::header union child::row union child::item union child::heading))">
        <xsl:variable name="children" select="child::node()" as="node()*"/>
        <xsl:variable name="indexFirstOutsideTable" as="xs:integer">
          <xsl:variable name="isTable" select="for $i in 1 to count($children) return $children[$i]/header or $children[$i]/row or $children[$i]/item or $children[$i]/heading"/>
          <xsl:value-of select="index-of($isTable, false())[1]"/>
        </xsl:variable>
        
        <db:informaltable>
          <xsl:for-each select="$children[position() &lt; $indexFirstOutsideTable]">
            <xsl:apply-templates mode="content_table" select="."/>
          </xsl:for-each>
        </db:informaltable>
        <xsl:for-each select="$children[position() >= $indexFirstOutsideTable]">
          <xsl:apply-templates mode="content" select="."/>
        </xsl:for-each>
      </xsl:when>
      <!-- Okayish case: multiple header rows. DocBook forces to merge them. -->
      <xsl:when test="count(header) > 1">
        <db:informaltable>
          <db:thead>
            <xsl:for-each select="header">
              <db:tr>
                <xsl:apply-templates mode="content_table"/>
              </db:tr>
            </xsl:for-each>
          </db:thead>
          <db:tbody>
            <xsl:apply-templates mode="content_table" select="header[last()]/following-sibling::row"/>
          </db:tbody>
        </db:informaltable>
      </xsl:when>
      <!-- Finally, sometimes, things are just OK. -->
      <xsl:otherwise>
        <db:informaltable>
          <xsl:apply-templates mode="content_table"/>
        </db:informaltable>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_table" match="header">
    <db:thead>
      <db:tr>
        <xsl:apply-templates mode="content_table"/>
      </db:tr>
    </db:thead>
  </xsl:template>
  
  <xsl:template mode="content_table" match="row">
    <xsl:choose>
      <xsl:when test="row">
        <!-- Qdoc is at it again: it outputs rows within rows... -->
        <xsl:variable name="children" select="child::node()" as="node()*"/>
        <xsl:variable name="firstOutsideRow" as="xs:integer">
          <xsl:variable name="isInRow" select="for $i in 1 to count($children) return boolean($children[$i]/self::row) or name($children[$i]/row) = 'row'"/>
          <xsl:value-of select="index-of($isInRow, true())[1]"/>
        </xsl:variable>
        
        <db:tr>
          <xsl:apply-templates mode="content_table" select="$children[position() &lt; $firstOutsideRow]"/>
        </db:tr>
        <xsl:apply-templates mode="content_table" select="$children[position() >= $firstOutsideRow]"/>
      </xsl:when>
      <xsl:otherwise>
        <db:tr>
          <xsl:apply-templates mode="content_table"/>
        </db:tr>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
    
  <xsl:template mode="content_table" match="item | heading">
    <xsl:variable name="targetId" select="preceding-sibling::node()[1]/self::target/@name" as="xs:string?"/>
    
    <xsl:choose>
      <xsl:when test="parent::header">
        <db:th>
          <xsl:if test="$targetId and not($targetId='')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          <xsl:if test="@rowspan">
            <xsl:attribute name="rowspan" select="@rowspan"/>
          </xsl:if>
          <xsl:if test="@colspan">
            <xsl:attribute name="colspan" select="@colspan"/>
          </xsl:if>
          
          <xsl:choose>
            <xsl:when test="child::para">
              <xsl:apply-templates mode="content"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:apply-templates mode="content_para"/>
            </xsl:otherwise>
          </xsl:choose>
        </db:th>
      </xsl:when>
      <xsl:otherwise>
        <db:td>
          <xsl:if test="$targetId and not($targetId='')">
            <xsl:attribute name="xml:id" select="tc:sanitise-xml-id($targetId)"/>
          </xsl:if>
          <xsl:if test="@rowspan">
            <xsl:attribute name="rowspan" select="@rowspan"/>
          </xsl:if>
          <xsl:if test="@colspan">
            <xsl:attribute name="colspan" select="@colspan"/>
          </xsl:if>
          
          <xsl:choose>
            <xsl:when test="child::node()[1][self::image]">
              <!-- An image (not inline, hence block) and its caption. -->
              <xsl:apply-templates mode="content" select="child::node()[1]"/>
              <db:para>
                <xsl:apply-templates mode="content_para" select="child::node()[position() > 1]"/>
              </db:para>
            </xsl:when>
            <xsl:when test="child::para | child::list | child::code | child::snippet">
              <xsl:apply-templates mode="content"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:apply-templates mode="content_para"/>
            </xsl:otherwise>
          </xsl:choose>
        </db:td>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template mode="content_table" match="target">
    <!-- Ignore: xml:id is inserted in the right cell. -->
  </xsl:template>

  <!-- Inline. -->
  <xsl:template mode="content_para" match="footnote">
    <db:footnote>
      <xsl:apply-templates mode="content"/>
    </db:footnote>
  </xsl:template>
  
  <xsl:template mode="content_para" match="link">
    <xsl:choose>
      <xsl:when test="parent::*[not(self::teletype)] and (@type='class' or @type='enum' or @type='function' or @type='property')">
        <db:code>
          <db:link xlink:href="{@href}" xrefstyle="{@type}" annotations="{@raw}">
            <xsl:apply-templates mode="content_para"/>
          </db:link>
        </db:code>
      </xsl:when>
      <xsl:otherwise><!-- @type='page' -->
        <db:link xlink:href="{@href}" xrefstyle="{@type}" annotations="{@raw}">
          <xsl:apply-templates mode="content_para"/>
        </db:link>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_para" match="teletype">
    <xsl:choose>
      <xsl:when test="parent::teletype and @type='highlighted'">
        <db:emphasis>
          <xsl:apply-templates mode="content_para"/>
        </db:emphasis>
      </xsl:when>
      <xsl:when test="parent::teletype and not(@type='highlighted')">
        <xsl:apply-templates mode="content_para"/>
      </xsl:when>
      <xsl:otherwise>
        <db:code>
          <xsl:apply-templates mode="content_para"/>
        </db:code>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_para" match="italic">
    <db:emphasis>
      <xsl:apply-templates mode="content_para"/>
    </db:emphasis>
  </xsl:template>
    
  <xsl:template mode="content_para" match="bold[text()='Note:' or text()='Important:']">
    <!-- Do nothing, as this part of the text is converted into an admonition. -->
  </xsl:template>
  
  <xsl:template mode="content_para" match="bold[not(text()='Note:') and not(text()='Important:')]">
    <db:emphasis role="bold">
      <xsl:apply-templates mode="content_para"/>
    </db:emphasis>
  </xsl:template>
  
  <xsl:template mode="content_para" match="underline">
    <db:emphasis role="underline">
      <xsl:apply-templates mode="content_para"/>
    </db:emphasis>
  </xsl:template>
  
  <xsl:template mode="content_para" match="superscript">
    <db:superscript>
      <xsl:apply-templates mode="content_para"/>
    </db:superscript>
  </xsl:template>
  
  <xsl:template mode="content_para" match="subscript">
    <db:subscript>
      <xsl:apply-templates mode="content_para"/>
    </db:subscript>
  </xsl:template>
  
  <xsl:template mode="content_para" match="argument | index">
    <db:code role="{name()}">
      <xsl:apply-templates mode="content_para"/>
    </db:code>
  </xsl:template>
  
  <xsl:template mode="content_para" match="inlineimage">
    <db:inlinemediaobject>
      <db:imageobject>
        <db:imagedata fileref="{@href}"/>
      </db:imageobject>
    </db:inlinemediaobject>
  </xsl:template>
  
  <xsl:template mode="content_para" match="node()[preceding-sibling::image]" priority="-1">
    <!-- Ignore this thing: if it was not caught anywhere else, it is either -->
    <!-- the caption of the image or a bug in the stylesheet. -->
  </xsl:template>
  
  <xsl:template mode="content_para" match="raw">
    <!-- Cheating, right. But we have no choice here. -->
    <db:code role="raw-html">
      <xsl:apply-templates mode="content_para"/>
    </db:code>
  </xsl:template>
  
  <xsl:template mode="content content_para" match="target">
    <!-- IDs are already transformed into xml:id. -->
  </xsl:template>
  
  <!-- Blocks, but things that are not really errors. (Mostly heuristic, though.) -->
  <!-- Considered as a part of a figure caption. -->
  <xsl:template mode="content" match="italic[preceding-sibling::*[position() &lt; 5][self::image]]"/>
  <xsl:template mode="content" match="bold[preceding-sibling::*[position() &lt; 5][self::image]]"/>
  <xsl:template mode="content" match="link[preceding-sibling::*[position() &lt; 5][self::image]]"/>
  <xsl:template mode="content" match="text()[preceding-sibling::*[position() &lt; 5][self::image]]"/>
 
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template mode="content" match="text()">
    <xsl:param name="silent" select="false()" as="xs:boolean"/>
    
    <xsl:if test="not($silent)">
      <xsl:message>WARNING: Text outside a paragraph</xsl:message>
    </xsl:if>
  </xsl:template>
  
  <xsl:template match="*" mode="#all" priority="-2">
    <xsl:param name="silent" select="false()" as="xs:boolean"/>
    
    <xsl:if test="not($silent)">
      <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" /></xsl:message>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>