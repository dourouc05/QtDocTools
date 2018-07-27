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
    <xsl:if test="$elements/self::class[@access='public' and (./description/brief or ./description/para)]">
      <db:section>
        <db:title>Classes</db:title>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::enum[@access='public' and (./description/brief or ./description/para)] or $elements/self::typedef[@access='public' and not(./description/brief or ./description/para)]">
      <db:section>
        <db:title>Member Type Documentation</db:title>
        <!-- The documentation is on the enum, but the typedef must be presented just after. -->
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::variable[@access='public' and (./description/brief or ./description/para)]">
      <db:section>
        <db:title>Type Documentation</db:title>
        <!-- Only happens in namespaces. -->
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::property[@access='public' and (./description/brief or ./description/para)]">
      <db:section>
        <db:title>Properties</db:title>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::variable[@access='public' and (./description/brief or ./description/para)]">
      <db:section>
        <db:title>Member Variable Documentation</db:title>
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::function[(@access='public' or @access='protected') and (./description/brief or ./description/para) and (not(@static) or not(@static='true'))]">
      <db:section>
        <db:title>Member Function Documentation</db:title>
        <!-- First constructors, then the other functions. -->
        <!-- TODO: Why doesn't QWidget::paintEngine have documentation in WebXML (but it has some in .cpp)? Also misses in PySide2's doc: https://doc.qt.io/qtforpython/PySide2/QtWidgets/QWidget.html -->
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::function[@access='public' and (./description/brief or ./description/para) and @static='true']">
      <db:section>
        <db:title>Static Member Function Documentation</db:title>
        <!-- First constructors, then the other functions. -->
      </db:section>
    </xsl:if>
    
    <!-- TODO -->
    <xsl:if test="$elements/self::variable[@access='public' and ./description/brief]">
      <db:section>
        <db:title>Related Non-Members</db:title>
        <!-- TODO: Not generated in WebXML. Example: http://doc.qt.io/qt-5/qpoint.html#related-non-members -->
      </db:section>
    </xsl:if>
    
    <xsl:if test="$elements/self::function[@access='public' and ./description/brief and (@meta='macrowithoutparams' or @meta='macrowithparams')]">
      <db:section>
        <db:title>Macro Documentation</db:title>
      </db:section>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content_class_elements" match="variable">
    VAR
  </xsl:template>
  
  <!-- Generic content handling (paragraphs, sections, etc.) -->
  <xsl:template mode="content_generic" match="brief">
    <!-- Ignore brief, as there is already some abstract before. -->
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
      <xsl:when test="@type='class' or @type='enum'">
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
    <db:emphasis role="bold">
      <xsl:apply-templates mode="content_generic"/>
    </db:emphasis>
  </xsl:template>
  
  <xsl:template mode="content_generic" match="bold[not(text()='Note:') and not(text()='Important:')]">
    <db:emphasis role="bold">
      <xsl:apply-templates mode="content_generic"/>
    </db:emphasis>
  </xsl:template>
  
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="#all">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" /></xsl:message>
  </xsl:template>
</xsl:stylesheet>