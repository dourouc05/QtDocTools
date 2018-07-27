<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/"
  exclude-result-prefixes="xsl xs html saxon"
  version="2.0">
  
  <xsl:output method="xml" indent="yes"
    saxon:suppress-indentation="db:code db:emphasis db:link db:programlisting db:title"/>
  <xsl:strip-space elements="*"/>
  
  <xsl:template match="/">
    <xsl:apply-templates select="WebXML/document"/>
  </xsl:template>
  
  <xsl:template match="document">
    <db:article>
      <!-- Info tag: either just a title, or a title and an abstract. -->
      <xsl:choose>
        <xsl:when test="child::node()[1]/@brief">
          <db:info>
            <db:title>
              <xsl:choose>
                <xsl:when test="child::node()[1]/@title"><xsl:value-of select="child::node()[1]/@title"/></xsl:when>
                <xsl:when test="child::node()[1]/@name"><xsl:value-of select="child::node()[1]/@name"/></xsl:when>
                <xsl:otherwise><xsl:message>WARNING: No title found.</xsl:message></xsl:otherwise>
              </xsl:choose>
            </db:title>
            <db:abstract>
              <db:para>
                <xsl:value-of select="child::node()[1]/@brief"/>
              </db:para>
            </db:abstract>
          </db:info>
        </xsl:when>
        <xsl:otherwise>
          <db:title>
            <xsl:choose>
              <xsl:when test="child::node()[1]/@title"><xsl:value-of select="child::node()[1]/@title"/></xsl:when>
              <xsl:when test="child::node()[1]/@name"><xsl:value-of select="child::node()[1]/@name"/></xsl:when>
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
        <!-- The documentation is on the enum, but the typedef must be presented just after. -->
        
        <xsl:variable name="memberEnums" select="$memberTypes/self::enum" as="node()*"/>
        <xsl:variable name="memberTypedefs" select="$memberTypes/self::typedef" as="node()*"/>
        
        <xsl:for-each select="$memberEnums">
          <xsl:sort select="@fullname"/>
          
          <db:section>
            <db:title>enum <xsl:value-of select="@fullname"/>, flags <xsl:value-of select="@typedef"/></db:title>
            
            <xsl:variable name="enumFullName" select="@fullname"/>
            <xsl:variable name="correspondingTypedef" select="$memberTypedefs/self::typedef[@enum=$enumFullName]"/>
            
            <xsl:apply-templates mode="content_class_elements" select=".">
              <xsl:with-param name="justSynopsis" select="true()"/>
            </xsl:apply-templates>
            <xsl:apply-templates mode="content_class_elements" select="$correspondingTypedef"/>
            <xsl:apply-templates mode="content_class_elements" select=".">
              <xsl:with-param name="justContent" select="true()"/>
            </xsl:apply-templates>
          </db:section>
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
        
        <xsl:if test="@since and not(@since='')">
          <db:para>This property was introduced in Qt <xsl:value-of select="@since"/>.</db:para>
        </xsl:if>
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
          <!-- For methods: -->
          <!-- @fullname:  QWidget::QWidget -->
          <!-- @signature: QWidget(QWidget *parent, Qt::WindowFlags f) -->
          <xsl:value-of select="
            if(contains(@fullname, '::')) then concat(@fullname, replace(@signature, concat('(^.*?)', @name), '$1')) else @signature "/>
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
            <xsl:when test="methodparam">
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
    <xsl:param name="justSynopsis" as="xs:boolean" select="false()"/>
    <xsl:param name="justContent" as="xs:boolean" select="false()"/>
    
    <xsl:variable name="outputSynopsis" as="xs:boolean" select="not($justContent)"/>
    <xsl:variable name="outputContent" as="xs:boolean" select="not($justSynopsis)"/>
    
    <xsl:if test="$outputSynopsis">
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
    </xsl:if>
    
    <xsl:if test="$outputContent">
      <xsl:apply-templates mode="content_generic" select="description"/>
      
      <xsl:if test="@since and not(@since='')">
        <db:para>This enum was introduced or modified in Qt <xsl:value-of select="@since"/>.</db:para>
      </xsl:if>
      
      <db:para>The <db:code><xsl:value-of select="@name"/>s</db:code> type is a typedef for <db:code>QFlags&lt;<xsl:value-of select="@name"/>&gt;</db:code>. It stores an OR combination of  values.</db:para>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="typedef">
    <db:typedefsynopsis>
      <db:typedefname><xsl:value-of select="@fullname"/></db:typedefname>
    </db:typedefsynopsis>
  </xsl:template>
  
  <!-- Generic content handling (paragraphs, sections, etc.) -->
  <xsl:template mode="content_generic" match="brief">
    <!-- Ignore brief, as there is already some abstract before. -->
  </xsl:template>
  
  <xsl:template mode="content_generic" match="description">
    <!-- Let templates flow through description to simplify code to handle classes. -->
    <xsl:apply-templates mode="content_generic"/>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="section">
    <db:section>
      <xsl:apply-templates mode="content_generic"/>
    </db:section>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="see-also">
    <db:section>
      <db:title>See Also</db:title>
      <db:simplelist type="vert">
        <xsl:for-each select="link">
          <db:member>
            <xsl:apply-templates mode="content_generic" select="."/>
          </db:member>
        </xsl:for-each>
      </db:simplelist>
    </db:section>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="heading">
    <db:title>
      <xsl:apply-templates mode="content_generic"/>
    </db:title>
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
  
  <xsl:template mode="content_generic_table" match="item">
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