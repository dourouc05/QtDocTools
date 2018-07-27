<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/"
  exclude-result-prefixes="xsl xs html saxon"
  version="3.0">
  
  <xsl:output method="xml" indent="yes"
    suppress-indentation="db:code db:emphasis db:link db:programlisting db:title"/>
  <xsl:strip-space elements="*"/>
  
  <xsl:template match="/">
    <xsl:apply-templates select="WebXML/document"/>
  </xsl:template>
  
  <xsl:template match="document">
    <xsl:variable name="mainTag" select="child::node()[1]" as="node()"/>
    
    <db:article>
      <!-- Info tag only when there is more than just a title: an abstract (brief) or extended links (relations). -->
      <xsl:choose>
        <xsl:when test="child::node()[1]/@brief or child::node()[1]/description/relation">
          <db:info>
            <db:title>
              <xsl:choose>
                <xsl:when test="$mainTag/@fulltitle"><xsl:value-of select="$mainTag/@fulltitle"/></xsl:when>
                <xsl:when test="$mainTag/@title"><xsl:value-of select="$mainTag/@title"/></xsl:when>
                <xsl:when test="$mainTag/@name"><xsl:value-of select="$mainTag/@name"/></xsl:when>
                <xsl:otherwise><xsl:message>WARNING: No title found.</xsl:message></xsl:otherwise>
              </xsl:choose>
            </db:title>
            <db:abstract>
              <db:para>
                <xsl:value-of select="child::node()[1]/@brief"/>
              </db:para>
            </db:abstract>
            
            <xsl:for-each select="$mainTag/description/relation">
              <db:extendedlink>
                <link xlink:type="arc" xlink:from="{$mainTag/@href}" xlink:to="{@href}" xlink:title="{@meta}" xlink:label="{@description}" />
              </db:extendedlink>
            </xsl:for-each>
          </db:info>
        </xsl:when>
        <xsl:otherwise>
          <db:title>
            <xsl:choose>
              <xsl:when test="$mainTag/@fulltitle"><xsl:value-of select="$mainTag/@fulltitle"/></xsl:when>
              <xsl:when test="$mainTag/@title"><xsl:value-of select="$mainTag/@title"/></xsl:when>
              <xsl:when test="$mainTag/@name"><xsl:value-of select="$mainTag/@name"/></xsl:when>
              <xsl:otherwise><xsl:message>WARNING: No title found.</xsl:message></xsl:otherwise>
            </xsl:choose>
          </db:title>
        </xsl:otherwise>
      </xsl:choose>
      
      <!-- Deal with the rest of the content. -->
      <xsl:apply-templates mode="content"/>
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
        <db:namespacesynopsisinfo role="headers">#include &lt;<xsl:value-of select="@location"/>&gt;</db:namespacesynopsisinfo>
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
    <db:section xml:id="details">
      <db:title>Detailed Description</db:title>
      
      <xsl:apply-templates mode="content_generic"/>
    </db:section>
  </xsl:template>
  
  <xsl:template name="content_class_elements">
    <xsl:param name="elements" as="node()*"/>
    
    <!-- Order of elements: classes, member types, types, properties, member variables, member functions (first constructors, then others), related non-members, macros. Never forget to sort the items by name. -->
    <xsl:variable name="classes" select="$elements/self::class[@access='public' and (./description/brief or ./description/para)]" as="node()*"/>
    <xsl:variable name="memberTypes" select="$elements/self::enum[@access='public' and (./description/brief or ./description/para)] | $elements/self::typedef[@access='public' and not(./description/brief or ./description/para)]" as="node()*"/>
    <xsl:variable name="types" select="$elements/self::variable[@access='public' and (./description/brief or ./description/para)]" as="node()*"/>
    <xsl:variable name="properties" select="$elements/self::property[@access='public' and (./description/brief or ./description/para)]" as="node()*"/>
    <xsl:variable name="memberVariables" select="$elements/self::variable[@access='public' and (./description/brief or ./description/para)]" as="node()*"/>
    <xsl:variable name="functions" select="$elements/self::function[(@access='public' or @access='protected') and (./description/brief or ./description/para) and not(@meta='macrowithoutparams' or @meta='macrowithparams')]" as="node()*"/>
    <xsl:variable name="relatedNonMembers" select="$elements/self::variable[@access='public' and (./description/brief or ./description/para)]"/>
    <xsl:variable name="macros" select="$elements/self::function[@access='public' and (./description/brief or ./description/para) and (@meta='macrowithoutparams' or @meta='macrowithparams')]" as="node()*"/>
    
    <xsl:if test="$classes">
      <db:section>
        <db:title>Classes</db:title>
        
        <xsl:if test="@since and not(@since='')">
          <db:para>This class was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
        </xsl:if>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$memberTypes">
      <db:section>
        <db:title>Member Type Documentation</db:title>
        <xsl:for-each select="$memberTypes/self::enum">
          <xsl:sort select="@fullname"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$types">
      <db:section>
        <db:title>Type Documentation</db:title>
        <!-- Only happens in namespaces. -->
        
        <xsl:if test="@since and not(@since='')">
          <db:para>This type was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
        </xsl:if>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$properties">
      <db:section>
        <db:title>Properties</db:title>
        
        <xsl:for-each select="$properties">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$memberVariables">
      <db:section>
        <db:title>Member Variable Documentation</db:title>
        
        <xsl:if test="@since and not(@since='')">
          <db:para>This variable was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
        </xsl:if>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$functions">
      <db:section>
        <db:title>Member Function Documentation</db:title>
        <!-- First constructors, then the other functions. -->
        
        <xsl:for-each select="$functions/self::function[@meta='constructor']">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <xsl:for-each select="$functions/self::function[@meta='destructor']">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <xsl:for-each select="$functions/self::function[not(@meta='constructor') and not(@meta='destructor')]">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
        
        <!-- TODO: Why doesn't QWidget::paintEngine have documentation in WebXML (but it has some in .cpp)? Also misses in PySide2's doc: https://doc.qt.io/qtforpython/PySide2/QtWidgets/QWidget.html -->
      </db:section>
    </xsl:if>
    
    <!-- TODO -->
    <xsl:if test="$relatedNonMembers">
      <db:section>
        <db:title>Related Non-Members</db:title>
        <!-- TODO: Not generated in WebXML. Example: http://doc.qt.io/qt-5/qpoint.html#related-non-members -->
      </db:section>
    </xsl:if>
    
    <xsl:if test="$macros">
      <db:section>
        <db:title>Macro Documentation</db:title>
        
        <xsl:for-each select="$macros">
          <xsl:sort select="@signature"/>
          <xsl:apply-templates mode="content_class_elements" select="."/>
        </xsl:for-each>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="variable">
    VAR
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="function">
    <xsl:if test="not(@access='private') and (not(@delete) or @delete='false') and @status='active'">
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
          <xsl:variable name="sanitisedName" select="replace(replace(replace(@name, '\+', '\\+'), '\[', '\\['), '\]', '\\]')" as="xs:string"/>
          <xsl:value-of select="
            if(contains(@fullname, '::')) then concat(@type,' ', replace(@signature, concat('(^.*?)', $sanitisedName), @fullname)) else @signature "/>
        </db:title>
        
        <!-- Choose the tag depending on the type of function: either constructor, destructor, or any kind of function (including signal and slot). -->
        <xsl:element name="{if (@meta='constructor') then 'db:constructorsynopsis' else if (@meta='destructor') then 'db:destructorsynopsis' else 'db:methodsynopsis'}">
          <!-- Determine whether this function is a signal or a slot. -->
          <xsl:if test="@meta='signal'">
            <db:modifier>signal</db:modifier>
          </xsl:if>
          <xsl:if test="@meta='slot'">
            <db:modifier>slot</db:modifier>
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
        </xsl:element>
        
        <xsl:apply-templates mode="content_generic" select="description"/>
        
        <xsl:if test="@since and not(@since='')">
          <db:para>This property was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
        </xsl:if>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="enum">
    <db:section>
      <db:title>enum <xsl:value-of select="@fullname"/>, flags <xsl:value-of select="@typedef"/></db:title>
      <!-- The documentation is on the enum, but the typedef must be presented just after. -->
      
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
      
      <xsl:if test="./following-sibling::typedef[1]">
        <xsl:apply-templates mode="content_class_elements" select="./following-sibling::typedef[1]"/>
      </xsl:if>
      <xsl:apply-templates mode="content_generic" select="description"/>
      
      <xsl:if test="@since and not(@since='')">
        <db:para>This enum was introduced or modified in Qt <xsl:value-of select="@since"/>.</db:para>
      </xsl:if>
      
      <xsl:if test="./following-sibling::typedef[1]">
        <db:para>The <db:code><xsl:value-of select="./following-sibling::typedef[1]/@name"/></db:code> type is a typedef for <db:code>QFlags&lt;<xsl:value-of select="@name"/>&gt;</db:code>. It stores an OR combination of  values.</db:para>
      </xsl:if>
    </db:section>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="typedef">
    <db:typedefsynopsis>
      <db:typedefname><xsl:value-of select="@fullname"/></db:typedefname>
    </db:typedefsynopsis>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="property">
    <db:section>
      <db:title><xsl:value-of select="@name"/> : <xsl:value-of select="@type"/></db:title>
      
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
      
      <xsl:apply-templates mode="content_generic" select="description"/>
      
      <xsl:if test="@since and not(@since='')">
        <db:para>This property was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
      </xsl:if>
      
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
    </db:section>
  </xsl:template>
  
  <!-- Deal with name spaces. -->
  <xsl:template mode="content" match="namespace">
    <xsl:call-template name="content_namespace_synopsis"/>
    <xsl:apply-templates mode="content_class_description" select="description"/>
    <xsl:call-template name="content_class_elements">
      <xsl:with-param name="elements" select="./description/following-sibling::node()"/>
    </xsl:call-template>
  </xsl:template>
  
  <xsl:template name="content_namespace_synopsis">
    <xsl:if test="@access='public' and @status='active'">
      <db:namespacesynopsis>
        <db:namespace>
          <db:namespacename><xsl:value-of select="@name"/></db:namespacename>
        </db:namespace>
        <db:namespacesynopsisinfo role="module"><xsl:value-of select="@module"/></db:namespacesynopsisinfo>
        <db:namespacesynopsisinfo role="headers">#include &lt;<xsl:value-of select="@location"/>&gt;</db:namespacesynopsisinfo>
      </db:namespacesynopsis>
    </xsl:if>
  </xsl:template>
  
  <!-- Deal with modules. -->
  <xsl:template mode="content" match="module">
    <xsl:apply-templates mode="content_generic" select="description/generatedlist"/>
    
    <db:section xml:id="details">
      <db:title>Detailed Description</db:title>
      
      <xsl:apply-templates mode="content_generic" select="description/generatedlist/following-sibling::node()"/>
    </db:section>
  </xsl:template>
  
  <!-- Deal with concepts. -->
  <xsl:template mode="content" match="page">
    <xsl:apply-templates mode="content_generic"/>
  </xsl:template>
  
  <!-- Generic content handling (paragraphs, sections, etc.) -->
  <xsl:template mode="content_generic" match="brief">
    <db:para>
      <xsl:apply-templates mode="content_generic"/>
    </db:para>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="target">
    <!-- IDs are already transformed into xml:id. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="contents | keyword">
    <!-- Used for a table of contents, can be skipped. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="relation">
    <!-- Handled as extended links in the <info> tag. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="codeline"/>
  
  <xsl:template mode="content_generic" match="description">
    <!-- Let templates flow through description to simplify code to handle classes. -->
    <xsl:apply-templates mode="content_generic"/>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="section">
    <db:section>
      <xsl:if test="@id">
        <xsl:attribute name="xml:id" select="@id"/>
      </xsl:if>
      
      <xsl:apply-templates mode="content_generic"/>
    </db:section>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="see-also">
    <db:para>
      <db:emphasis role="bold">See Also:</db:emphasis>
      <db:simplelist type="vert">
        <xsl:for-each select="link">
          <db:member>
            <xsl:apply-templates mode="content_generic" select="."/>
          </db:member>
        </xsl:for-each>
      </db:simplelist>
    </db:para>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="heading">
    <db:title>
      <xsl:apply-templates mode="content_generic"/>
    </db:title>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="generatedlist">
    <xsl:apply-templates mode="content_generic"/>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="para">
    <xsl:choose>
      <xsl:when test="child::node()[1]/text()='Note:'">
        <db:note>
          <db:para>
            <xsl:apply-templates mode="content_generic"/>
          </db:para>
        </db:note>
      </xsl:when>
      <xsl:when test="child::node()[1]/text()='Important:'">
        <db:note>
          <db:para>
            <xsl:apply-templates mode="content_generic"/>
          </db:para>
        </db:note>
      </xsl:when>
      <xsl:otherwise>
        <db:para>
          <xsl:apply-templates mode="content_generic"/>
        </db:para>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="snippet">
    <db:programlisting>
      <xsl:value-of select="unparsed-text(concat('file:///', @path))"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="code">
    <!-- Language is C++, JS, or QML. -->
    <db:programlisting>
      <xsl:apply-templates mode="content_generic"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="badcode">
    <!-- As opposed to code, language is unknown. -->
    <db:programlisting language="other">
      <xsl:apply-templates mode="content_generic"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="quote">
    <xsl:choose>
      <xsl:when test="para/teletype">
        <db:screen>
          <xsl:apply-templates mode="content_generic" select="para/teletype/child::node()"/>
        </db:screen>
      </xsl:when>
      <xsl:otherwise>
        <db:quote>
          <xsl:apply-templates mode="content_generic"/>
        </db:quote>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="list[@type='ordered']">
    <db:orderedlist>
      <xsl:apply-templates mode="content_generic"/>
    </db:orderedlist>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="list[@type='bullet']">
    <db:itemizedlist>
      <xsl:apply-templates mode="content_generic"/>
    </db:itemizedlist>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="list[@type='enum']">
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
              <xsl:apply-templates mode="content_generic" select="./following-sibling::item[1]/child::node()"/>
            </db:td>
          </db:tr>
        </xsl:for-each>
      </db:tbody>
    </db:informaltable>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="list[@type='definition']">
    <db:variablelist>
      <xsl:for-each select="definition">
        <db:varlistentry>
          <db:term>
            <xsl:apply-templates mode="content_generic" select="term/child::node()"/>
          </db:term>
          <xsl:apply-templates mode="content_generic" select="following-sibling::item[1]"/>
        </db:varlistentry>
      </xsl:for-each>
    </db:variablelist>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="item">
    <db:listitem>
      <xsl:apply-templates mode="content_generic"/>
    </db:listitem>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="image">
    <db:mediaobject>
      <db:imageobject>
        <db:imagedata fileref="{@href}"/>
      </db:imageobject>
    </db:mediaobject>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="inlineimage">
    <db:inlinemediaobject>
      <db:imageobject>
        <db:imagedata fileref="{@href}"/>
      </db:imageobject>
    </db:inlinemediaobject>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="table">
    <db:informaltable>
      <xsl:apply-templates mode="content_generic_table"/>
    </db:informaltable>
  </xsl:template>
  
  <xsl:template mode="content_generic_table" match="header">
    <db:thead>
      <db:tr>
        <xsl:apply-templates mode="content_generic_table"/>
      </db:tr>
    </db:thead>
  </xsl:template>
  
  <xsl:template mode="content_generic_table" match="row">
    <db:tr>
      <xsl:apply-templates mode="content_generic_table"/>
    </db:tr>
  </xsl:template>
  
  <xsl:template mode="content_generic_table" match="item | heading">
    <xsl:choose>
      <xsl:when test="parent::header">
        <db:th>
          <xsl:apply-templates mode="content_generic"/>
        </db:th>
      </xsl:when>
      <xsl:otherwise>
        <db:td>
          <xsl:apply-templates mode="content_generic"/>
        </db:td>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="link">
    <xsl:choose>
      <xsl:when test="@type='class' or @type='enum' or @type='function' or @type='property'">
        <db:code>
          <db:link xlink:href="{@href}" xrefstyle="{@type}" annotations="{@raw}">
            <xsl:apply-templates mode="content_generic"/>
          </db:link>
        </db:code>
      </xsl:when>
      <xsl:otherwise><!-- @type='page' -->
        <db:link xlink:href="{@href}" xrefstyle="{@type}" annotations="{@raw}">
          <xsl:apply-templates mode="content_generic"/>
        </db:link>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="teletype">
    <db:code>
      <xsl:apply-templates mode="content_generic"/>
    </db:code>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="italic">
    <db:emphasis>
      <xsl:apply-templates mode="content_generic"/>
    </db:emphasis>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="bold[text()='Note:' or text()='Important:']">
    <!-- Do nothing, as this part of the text is converted into an admonition. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="bold[not(text()='Note:') and not(text()='Important:')]">
    <db:emphasis role="bold">
      <xsl:apply-templates mode="content_generic"/>
    </db:emphasis>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="underline">
    <db:emphasis role="underline">
      <xsl:apply-templates mode="content_generic"/>
    </db:emphasis>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="argument">
    <db:code role="argument">
      <xsl:apply-templates mode="content_generic"/>
    </db:code>
  </xsl:template>
  
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="#all">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" /></xsl:message>
  </xsl:template>
</xsl:stylesheet>