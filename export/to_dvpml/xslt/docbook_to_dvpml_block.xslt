<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  <xsl:template mode="content" match="db:info">
    <!-- Everything is done in <db:article>. -->
  </xsl:template>
  
  <xsl:template mode="content" match="db:section">
    <xsl:variable name="sectionId">
      <xsl:number level="multiple" format="1"/>
    </xsl:variable>
    
    <section id="{$sectionId}">
      <xsl:choose>
        <xsl:when test="@xml:id">
          <!-- First the title, then some raw HTML. -->
          <title><xsl:value-of select="node()[db:title][1]"/></title>
          <html-brut>
            <xsl:value-of select="concat('&lt;![CDATA[&lt;a name=&#34;', @xml:id, '&#34;]]>')"/>
          </html-brut>
          <xsl:apply-templates mode="content" select="node()[not(db:title)]"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates mode="content" select="node()"/>
        </xsl:otherwise>
      </xsl:choose>
      
    </section>
  </xsl:template>
  
  <xsl:template mode="content" match="db:title">
    <title><xsl:value-of select="."/></title>
  </xsl:template>
  
  <xsl:template mode="content" match="db:informaltable">
    <tableau width="80%" border="1" sautDePagePdf="0">
      <xsl:apply-templates mode="content"/>
    </tableau>
  </xsl:template>
  
  <xsl:template mode="content" match="db:table">
    <tableau width="80%" border="1" legende="{if (title) then title else info/title}">
      <xsl:apply-templates mode="content"/>
    </tableau>
  </xsl:template>
  
  <xsl:template mode="content" match="db:thead | db:tbody">
    <xsl:apply-templates mode="content"/>
  </xsl:template>
  
  <xsl:template mode="content" match="db:tr">
    <xsl:element name="{if (ancestor::db:thead or db:th) then 'entete' else 'ligne'}">
      <xsl:apply-templates mode="content"/>
    </xsl:element>
  </xsl:template>
  
  <xsl:template mode="content" match="db:th | db:td">
    <colonne useText="0">
      <xsl:choose>
        <xsl:when test="db:para | db:note | db:itemizedlist | db:orderedlist | db:mediaobject | db:programlisting | db:screen">
          <xsl:apply-templates mode="content"/>
        </xsl:when>
        <xsl:otherwise>
          <paragraph>
            <xsl:apply-templates mode="content_para"/>
          </paragraph>
        </xsl:otherwise>
      </xsl:choose>
    </colonne>
  </xsl:template>
  
  <xsl:template mode="content" match="db:figure | db:informalfigure">
    <xsl:if test="@xml:id">
      <signet id="{@xml:id}"/>
    </xsl:if>
    
    <image>
      <xsl:attribute name="src">
        <xsl:value-of select="./db:mediaobject/db:imageobject/db:imagedata/@fileref"/>
      </xsl:attribute>
      
      <!-- A figure must have a title, unlike an informalfigure. -->
      <xsl:if test="./db:title">
        <xsl:attribute name="legende">
          <xsl:value-of select="./db:title"/>
        </xsl:attribute>
      </xsl:if>
    </image>
  </xsl:template>
  
  <xsl:template mode="content" match="db:constructorsynopsis | db:destructorsynopsis | db:enumsynopsis | db:typedefsynopsis | db:fieldsynopsis | db:methodsynopsis | db:classsynopsis | db:fieldsynopsis | db:namespacesynopsis"/>
  
  <xsl:template mode="content" match="db:para">
    <!-- The synopsis is done in <db:article>. -->
    <xsl:if test="..[self::db:section] or ..[self::db:listitem] or ..[self::db:blockquote] or ..[self::db:th] or ..[self::db:td] or ..[self::db:footnote] or ..[self::db:note] or ..[self::db:article] or string-length(name(preceding-sibling::*[1])) = 0">
      <xsl:choose>
        <xsl:when test="db:informaltable | db:note | db:programlisting | db:screen">
          <!-- Some content must be moved outside the paragraph (DocBook's model is really flexible). -->
          <xsl:variable name="children" select="child::node()" as="node()*"/>
          <xsl:variable name="firstTagOutsideParagraph" as="xs:integer">
            <xsl:variable name="isNotPara" select="for $i in 1 to count($children) return boolean($children[$i][self::db:informaltable | self::db:note | self::db:programlisting | self::db:screen]) or name($children[$i]) = 'db:informaltable' or name($children[$i]) = 'db:note' or name($children[$i]) = 'db:programlisting' or name($children[$i]) = 'db:screen'"/>
            <xsl:value-of select="index-of($isNotPara, true())"/>
          </xsl:variable>
          
          <paragraph>
            <xsl:apply-templates mode="content_para" select="node()[position() &lt; $firstTagOutsideParagraph]"/>
          </paragraph>
          <xsl:apply-templates mode="content" select="node()[position() >= $firstTagOutsideParagraph]"/>
        </xsl:when>
        <xsl:otherwise>
          <!-- A normal paragraph. -->
          <paragraph>
            <xsl:apply-templates mode="content_para"/>
          </paragraph>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content" match="db:blockquote">
    <!-- Dirty hack, I know. -->
    <tableau width="95%" border="3">
      <ligne>
        <colonne useText="0">
          <xsl:apply-templates mode="content"/>
        </colonne>
      </ligne>
    </tableau>
  </xsl:template>
  
  <xsl:template mode="content" match="db:programlisting[@role='raw-html']">
    <html-brut>
      <xsl:apply-templates mode="content_para"/>
    </html-brut>
  </xsl:template>
  
  <xsl:template mode="content" match="db:programlisting[not(@role='raw-html')] | db:screen">
    <code langage="{if (@language) then @language else 'other'}">
      <xsl:apply-templates mode="content_para"/>
    </code>
  </xsl:template>
  
  <xsl:template mode="content" match="db:itemizedlist">
    <liste>
      <xsl:apply-templates mode="content"/>
    </liste>
  </xsl:template>
  
  <xsl:template mode="content" match="db:orderedlist">
    <liste type="1">
      <xsl:apply-templates mode="content"/>
    </liste>
  </xsl:template>
  
  <xsl:template mode="content content_para" match="db:simplelist">
    <xsl:for-each select="db:member">
      <xsl:variable name="test">
        <xsl:apply-templates mode="content_para"/>
      </xsl:variable>
      <xsl:for-each select="$test/child::node()">
        <xsl:choose>
          <xsl:when test=". instance of text()">
            <xsl:value-of select="normalize-space(.)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="."/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
      <xsl:if test="position() != last()">
        <xsl:text>, </xsl:text>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  
  <xsl:template mode="content" match="db:listitem">
    <element useText="0">
      <xsl:apply-templates mode="content"/>
    </element>
  </xsl:template>
  
  <xsl:template mode="content" match="db:variablelist">
    <liste>
      <xsl:apply-templates mode="content"/>
    </liste>
  </xsl:template>
  
  <xsl:template mode="content" match="db:varlistentry">
    <element useText="0">
      <b>
        <xsl:apply-templates mode="content_para" select="term"/>
      </b>
      <xsl:text>&#0160;: </xsl:text>
      <xsl:apply-templates mode="content_para" select="listitem/para"/>
    </element>
  </xsl:template>
  
  <xsl:template mode="content" match="db:note">
    <rich-imgtext type="info">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:tip">
    <rich-imgtext type="idea">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:warning">
    <rich-imgtext type="warning">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:caution">
    <rich-imgtext type="error">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:important">
    <xsl:message>WARNING: Tag <xsl:value-of select="name(.)" /> has no matching construct in the target format. Content is output as a caution.</xsl:message>
    <rich-imgtext type="error">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:mediaobject | db:inlinemediaobject">
    <xsl:choose>
      <!-- First child is an image? You've got an image! -->
      <xsl:when test="child::node()[1][self::db:imageobject]">
        <image src="{db:imageobject[1]/db:imagedata/@fileref}">
          <xsl:if test="db:textobject">
            <xsl:attribute name="alt" select="normalize-space(db:textobject)"/>
          </xsl:if>
          
          <xsl:if test="db:caption">
            <xsl:attribute name="legende" select="normalize-space(db:caption)"/>
          </xsl:if>
        </image>
      </xsl:when>
      <!-- First child is a video? You've got a video! If there is an image, it is alternate content. -->
      <xsl:when test="child::node()[1][self::db:videoobject]">
        <animation>
          <xsl:if test="@role">
            <xsl:attribute name="type" select="@role"/>
          </xsl:if>
          
          <xsl:if test="@width">
            <width><xsl:value-of select="@width"/></width>
          </xsl:if>
          
          <xsl:if test="@height">
            <height><xsl:value-of select="@height"/></height>
          </xsl:if>
          
          <xsl:for-each select="multimediaparam">
            <xsl:element name="{@name}">
              <xsl:value-of select="@value"/>
            </xsl:element>
          </xsl:for-each>
        </animation>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_para content" match="db:equation | db:inlineequation">
    <latex id="{@xml:id}"><xsl:value-of select="mathphrase[@role='latex']"/></latex>
  </xsl:template>
  
  <xsl:template match="*[preceding-sibling::*[1][self::db:mediaobject]]" mode="content" priority="-1">
  </xsl:template>
</xsl:stylesheet>