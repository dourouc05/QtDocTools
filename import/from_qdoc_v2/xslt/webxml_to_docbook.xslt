<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://dourouc05.github.io"
  exclude-result-prefixes="xsl xs html saxon tc"
  version="3.0">
  
  <xsl:output method="xml" indent="yes"
    suppress-indentation="db:code db:emphasis db:link db:programlisting db:title"/>
  <xsl:strip-space elements="*"/>
  
  <xsl:template match="/">
    <xsl:apply-templates select="WebXML/document"/>
  </xsl:template>
  
  <xsl:template match="document">
    <xsl:variable name="mainTag" select="child::node()[1]" as="node()"/>
    
    <db:article version="5.2">
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
          <xsl:variable name="sanitisedName" select="replace(replace(replace(replace(@name, '\+', '\\+'), '\[', '\\['), '\]', '\\]'), '\|', '\\|')" as="xs:string"/>
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
    <xsl:apply-templates mode="content_generic" select="description"/>
  </xsl:template>

  <!-- Deal with groups of examples. -->
  <xsl:template mode="content" match="group">
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
  
  <xsl:template mode="content_generic" match="raw">
    <!-- Must skip, no way to encode raw HTML here (except when it's not for tweaking the output). -->
    <xsl:if test="parent::node()[1]/self::quote">
      <db:programlisting>
        <xsl:value-of select="text()"/>
      </db:programlisting>
    </xsl:if>
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
    <xsl:variable name="targetIdSub" select="preceding-sibling::node()[1]/self::target/@name" as="xs:string?"/>
    <xsl:variable name="targetId" select="if ($targetIdSub and not($targetIdSub='')) then $targetIdSub else ''" as="xs:string?"/>
    
    <xsl:choose>
      <xsl:when test="child::node()[1]/text()='Note:'">
        <db:note>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="$targetId"/>
          </xsl:if>
          <db:para>
            <xsl:apply-templates mode="content_generic"/>
          </db:para>
        </db:note>
      </xsl:when>
      <xsl:when test="child::node()[1]/text()='Important:'">
        <db:note>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="$targetId"/>
          </xsl:if>
          <db:para>
            <xsl:apply-templates mode="content_generic"/>
          </db:para>
        </db:note>
      </xsl:when>
      <xsl:otherwise>
        <db:para>
          <xsl:if test="not($targetId='')">
            <xsl:attribute name="xml:id" select="$targetId"/>
          </xsl:if>
          <xsl:apply-templates mode="content_generic"/>
        </db:para>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="legalese">
    <db:note>
      <xsl:apply-templates mode="content_generic"/>
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
    <xsl:variable name="filePathTentative" select="concat($sourceFolder, $fileToQuote)"/>
    <xsl:variable name="filePath" select="if  (tc:file-exists($filePathTentative)) then $filePathTentative else concat($sourceFolder, '../snippets/', $fileToQuote)"/>
    
    <xsl:if test="not(tc:file-exists($filePath))">
      <xsl:message>WARNING: Unable to find the correct path for <xsl:value-of select="$fileToQuote"/></xsl:message>
    </xsl:if>
    
    <xsl:value-of select="$filePath"/>
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
    
    <xsl:variable name="fileContents" select="tc:load-file($filename)"/>
    <xsl:variable name="onlyInterestingSnippet" select="tokenize($fileContents, concat('//! \[', $identifier, '\]'))[2]"/>
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
      <xsl:when test="$snippetNode/self::snippet">
        <xsl:value-of select="concat(tc:parse-snippet($snippetNode/@path, $snippetNode/@identifier), $recurse)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="concat(tc:generate-indent($snippetNode/@indent), $recurse)"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:template mode="content_generic" match="snippet[not(preceding-sibling::node()[1]/self::snippet or preceding-sibling::node()[1]/self::dots)]">
    <db:programlisting>
      <xsl:value-of select="tc:gather-snippet(.)"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="snippet[preceding-sibling::node()[1]/self::snippet or preceding-sibling::node()[1]/self::dots]">
    <!-- Should be handled with the rest of the snippet. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="dots">
    <!-- Should be handled with snippets or quotefromfile. Except in rare cases, where there are only dots -->
    <!-- (several such tags may follow; as for the rest, only output something for the first one). -->
    <!-- Quite specific case (barely happens, except in qdoc documentation). -->
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
  
  <xsl:template mode="content_generic" match="quotefile">
    <!-- No real path provided for quotefile, so must make up one. -->
    <db:programlisting>
      <xsl:value-of select="tc:load-file(tc:find-file-path(ancestor::description/@path, text()))"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="quotefromfile">
    <!-- Don't do anything in this: this tag will be retrieved when needed (skipto, printuntil, and family). -->
  </xsl:template>
  
  <xsl:function name="tc:printfromfile-get-absolute-line-after-text">
    <xsl:param name="quotefromfileLines" as="xs:string*"/>
    <xsl:param name="soughtText" as="xs:string"/>
    <xsl:param name="startAt" as="xs:integer"/>
    
    <xsl:variable name="matchedLines" as="xs:boolean*">
      <xsl:for-each select="$quotefromfileLines[position() >= $startAt]">
        <xsl:choose>
          <xsl:when test="starts-with($soughtText, '/') and ends-with($soughtText, '/')">
            <!-- Regular expression! -->
            <xsl:value-of select="matches(., substring($soughtText, 2, string-length($soughtText) - 2))"/>
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
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-skip" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="$currentNode/self::skipline or $currentNode/self::skipto or $currentNode/self::skipuntil"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-print" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="$currentNode/self::printline or $currentNode/self::printto or $currentNode/self::printuntil"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-is-dots" as="xs:boolean">
    <xsl:param name="currentNode" as="node()"/>
    
    <xsl:value-of select="boolean($currentNode/self::dots)"/>
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
            
            <xsl:if test="$lineMatched &lt; $recurse">
              <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a value less than its starting line.</xsl:message>
            </xsl:if>
            <xsl:if test="not(contains($quotefromfileSequenceLines[position() = $lineMatched], $currentNode/text()))">
              <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a line not containing the requested text.</xsl:message>
            </xsl:if>
            
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
            
            <xsl:if test="$lineMatched &lt; $recurse">
              <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a value less than its starting line.</xsl:message>
            </xsl:if>
            <xsl:if test="not(contains($quotefromfileSequenceLines[position() = $lineMatched], $currentNode/text()))">
              <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a line not containing the requested text.</xsl:message>
            </xsl:if>
            
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
    
    <xsl:variable name="previousInterestingNode" select="($currentNode/preceding::node()/(self::quotefromfile union self::printline union self::printto union self::printuntil union self::skipline union self::skipto union self::skipuntil union self::dots))[last()]"/>
    <xsl:variable name="initialLine" as="xs:integer" select="tc:printfromfile-find-line-of($previousInterestingNode, $quotefromfileSequenceLines)"/> 
    <xsl:message>!!! STARTING AT LINE: <xsl:value-of select="$initialLine"/></xsl:message>
    <xsl:value-of select="tc:printfromfile-print-content($currentNode, $quotefromfileSequenceLines, $initialLine)"/>
  </xsl:function>
  <xsl:function name="tc:printfromfile-print-content" as="xs:string">
    <xsl:param name="currentNode" as="node()?"/>
    <xsl:param name="quotefromfileSequenceLines" as="xs:string*"/>
    <xsl:param name="startAt" as="xs:integer"/>
    
    <xsl:variable name="nextNode" as="node()?" select="$currentNode/following-sibling::node()[1]"/>
    
    <xsl:choose>
      <!-- Advance $startAt until the end of the part to skip, then recurse. -->
      <xsl:when test="tc:printfromfile-is-skip($currentNode)">
        <xsl:variable name="lineMatched" select="tc:printfromfile-get-absolute-line-after-text($quotefromfileSequenceLines, $currentNode/text(), $startAt)"/>
        
        <xsl:if test="$lineMatched &lt; $startAt">
          <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a value less than its starting line.</xsl:message>
        </xsl:if>
        <xsl:if test="not(contains($quotefromfileSequenceLines[position() = $lineMatched], $currentNode/text()))">
          <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a line not containing the requested text.</xsl:message>
        </xsl:if>
        
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
        <xsl:value-of select="tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, $newStartAt)"/>
      </xsl:when>
      <!-- Generate properly indented dots and recurse. -->
      <xsl:when test="tc:printfromfile-is-dots($currentNode)">
        <xsl:value-of select="concat(codepoints-to-string(10), tc:generate-indent($currentNode/@indent), '...')"/>
      </xsl:when>
      <!-- Print from $startAt (included) until the last line that must be printed, and recurse. -->
      <xsl:when test="tc:printfromfile-is-print($currentNode)">
        <xsl:variable name="lineMatched" select="tc:printfromfile-get-absolute-line-after-text($quotefromfileSequenceLines, $currentNode/text(), $startAt)"/>
        
        <xsl:if test="$lineMatched &lt; $startAt">
          <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a value less than its starting line.</xsl:message>
        </xsl:if>
        <xsl:if test="not(contains($quotefromfileSequenceLines[position() = $lineMatched], $currentNode/text()))">
          <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-get-absolute-line-after-text returned a line not containing the requested text.</xsl:message>
        </xsl:if>
        
        <xsl:choose>
          <xsl:when test="$currentNode/self::printline">
            <xsl:variable name="currentLine" select="$quotefromfileSequenceLines[position() = xs:integer($lineMatched)]"/>
            <xsl:variable name="recurse" select="tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, xs:integer($lineMatched + 1))"/>
            
            <xsl:if test="not(contains($currentLine, $currentNode/text()))">
              <xsl:message>CONSISTENCY CHECK WARNING: printfromfile-print-content returning a line not containing the requested text (maybe a problem in the original documentation).</xsl:message>
            </xsl:if>
            
            <xsl:value-of select="concat($currentLine, $recurse)"/>
          </xsl:when>
          <xsl:when test="$currentNode/self::printto">
            <xsl:variable name="endAt" select="xs:integer($lineMatched - 1)"/>
            <xsl:variable name="currentBlock" select="string-join($quotefromfileSequenceLines[position() = ($startAt to $endAt)], codepoints-to-string(10))"/>
            <xsl:variable name="recurse" select="tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, xs:integer($lineMatched + 1))"/>
            
            <xsl:if test="contains($quotefromfileSequenceLines[position() = $endAt], $currentNode/text())">
              <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-print-content returning a line containing text that should not be there.</xsl:message>
            </xsl:if>
            
            <xsl:value-of select="concat($currentBlock, $recurse)"/>
          </xsl:when> 
          <xsl:when test="$currentNode/self::printuntil">
            <xsl:variable name="endAt" select="$lineMatched"/>
            <xsl:variable name="currentBlock" select="string-join($quotefromfileSequenceLines[position() = ($startAt to $endAt)], codepoints-to-string(10))"/> 
            <xsl:variable name="recurse" select="tc:printfromfile-print-content($nextNode, $quotefromfileSequenceLines, $lineMatched)"/>
            
            <xsl:if test="not(contains($quotefromfileSequenceLines[position() = $endAt], $currentNode/text()))">
              <xsl:message>CONSISTENCY CHECK ERROR: printfromfile-print-content returning a line not containing requested text.</xsl:message>
            </xsl:if>
            
            <xsl:value-of select="concat($currentBlock, $recurse)"/>
          </xsl:when> 
        </xsl:choose>
      </xsl:when>
      <!-- Base case: nothing to print, the current node is not about printfromfile. -->
      <xsl:otherwise>
        <xsl:value-of select="''"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:template mode="content_generic" match="printline | printto | printuntil">
    <!-- Work through the intricacies behind quotefromfile. -->
    <!-- Retrieve the code from the last quotefromfile. -->
    <!-- Split it according to a series of tags: -->
    <!--     printline, printto, printuntil, skipline, skipto, skipuntil -->
    
    <!-- As QDoc, only print something with the print* tags. -->
    <!-- Contrary to QDoc, dots are handled by the print* tags, not when meeting a dots tag -->
    <!-- (within the tc:printfromfile-print-content function). -->
    
    <!-- Detailed explanation of each tag: -->
    <!--   - printline:  print one line, advance cursor by one line -->
    <!--   - printto:    print until (EXcluding) the first line that matches the argument -->
    <!--   - printuntil: print until (INcluding) the first line that matches the argument -->
    <!--   - skipline:  advance cursor by one line (or more, if there are nonblank lines) -->
    <!--   - skipto:    skip until (EXcluding) the first line that matches the argument -->
    <!--   - skipuntil: skip until (INcluding) the first line that matches the argument -->
    
    <xsl:variable name="descriptionPath" as="xs:string" select="//description/@path"/>
    <xsl:variable name="quotefromfileFile" as="xs:string" select="tc:find-file-path($descriptionPath, preceding::quotefromfile[1])"/>
    <xsl:variable name="quotefromfileSequenceLines" as="xs:string*" select="tokenize(tc:load-file($quotefromfileFile), codepoints-to-string(10))"/>
    
    <xsl:variable name="content" select="tc:printfromfile-print-content(., $quotefromfileSequenceLines)" as="xs:string"/>
    
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
  <xsl:template mode="content_generic" match="skipline | skipto | skipuntil">
    <!-- Nothing to print. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="code">
    <!-- Language is C++, JS, or QML. -->
    <db:programlisting language="other">
      <xsl:apply-templates mode="content_generic"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="badcode">
    <!-- As opposed to code, language is unknown. -->
    <db:programlisting language="other" role="badcode">
      <xsl:apply-templates mode="content_generic"/>
    </db:programlisting>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="oldcode">
    <xsl:choose>
      <xsl:when test="parent::node()/self::quote">
        <db:programlisting language="other" role="badcode">
          <xsl:apply-templates  mode="content_generic"/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <db:para>For example, if you have code like</db:para>
        <db:programlisting>
          <xsl:apply-templates mode="content_generic"/>
        </db:programlisting>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="newcode">
    <xsl:choose>
      <xsl:when test="parent::node()/self::quote">
        <db:programlisting language="other" mode="newcode">
          <xsl:apply-templates mode="content_generic"/>
        </db:programlisting>
      </xsl:when>
      <xsl:otherwise>
        <db:para>you can rewrite it as</db:para>
        <db:programlisting language="other" mode="newcode">
          <xsl:apply-templates mode="content_generic"/>
        </db:programlisting>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="quote">
    <db:blockquote>
      <xsl:apply-templates mode="content_generic"/>
    </db:blockquote>
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
    <xsl:variable name="targetId" select="preceding-sibling::node()[1]/self::target/@name" as="xs:string?"/>
    
    <xsl:choose>
      <xsl:when test="parent::header">
        <db:th>
          <xsl:if test="$targetId and not($targetId='')">
            <xsl:attribute name="xml:id" select="$targetId"/>
          </xsl:if>
          <xsl:apply-templates mode="content_generic"/>
        </db:th>
      </xsl:when>
      <xsl:otherwise>
        <db:td>
          <xsl:if test="$targetId and not($targetId='')">
            <xsl:attribute name="xml:id" select="$targetId"/>
          </xsl:if>
          <xsl:apply-templates mode="content_generic"/>
        </db:td>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_generic_table" match="target">
    <!-- Ignore: xml:id is inserted in the right cell. -->
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
  
  <xsl:template mode="content_generic" match="superscript">
    <db:superscript>
      <xsl:apply-templates mode="content_generic"/>
    </db:superscript>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="subscript">
    <db:subscript>
      <xsl:apply-templates mode="content_generic"/>
    </db:subscript>
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