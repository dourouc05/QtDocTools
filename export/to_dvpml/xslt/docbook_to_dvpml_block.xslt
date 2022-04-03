<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink" version="3.0">
  <xsl:template mode="content" match="db:info">
    <xsl:apply-templates mode="content"/>
  </xsl:template>

  <xsl:template mode="content"
    match="db:section | db:sect1 | db:sect2 | db:sect3 | db:sect4 | db:sect5 | db:sect6 | db:chapter">
    <xsl:variable name="sectionId">
      <xsl:number level="multiple"
        count="db:section | db:sect1 | db:sect2 | db:sect3 | db:sect4 | db:sect5 | db:sect6 | db:chapter"
        format="I.1"/>
    </xsl:variable>

    <section id="{$sectionId}">
      <xsl:choose>
        <xsl:when test="@xml:id">
          <!-- First the title, then some raw HTML to encode the ID, then the rest of the section/chapter. -->
          <!-- Generic code cannot be applied because of the specific position of the ID. -->
          <xsl:apply-templates mode="content" select="db:info"/>
          <xsl:apply-templates mode="content" select="db:title"/>
          <signet id="{@xml:id}"/>

          <xsl:apply-templates mode="content"
            select="./*[not(self::db:title) and not(self::db:info)]"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates mode="content" select="node()"/>
        </xsl:otherwise>
      </xsl:choose>
    </section>
  </xsl:template>

  <xsl:template mode="content" match="db:title">
    <title>
      <xsl:apply-templates mode="content_para_no_formatting"/>
    </title>

    <!-- Index elements are only allowed within the text. -->
    <xsl:if test="db:indexterm">
      <paragraph>
        <xsl:apply-templates mode="content_para" select="db:indexterm"/>
      </paragraph>
    </xsl:if>
  </xsl:template>

  <xsl:function name="tc:table-width">
    <xsl:param name="table"/><!-- as="element(db:table | db:informaltable)" -->
    <xsl:param name="default-width"/>
    
    <xsl:choose>
      <xsl:when test="$table/@width">
        <xsl:choose>
          <xsl:when test="$table/@width castable as xs:integer">
            <xsl:value-of select="concat($table/@width, 'px')"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="$table/@width"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$default-width"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:function name="tc:table-border">
    <xsl:param name="table"/><!-- as="element(db:table | db:informaltable)" -->
    <xsl:param name="default-border"/>
    
    <xsl:choose>
      <xsl:when test="$table/@border">
        <xsl:value-of select="$table/@border"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$default-border"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:template mode="content" match="db:informaltable | db:table">
    <xsl:variable name="caption" as="xs:string?">
      <xsl:choose>
        <xsl:when test="db:title">
          <xsl:value-of select="db:title"/>
        </xsl:when>
        <xsl:when test="db:info/db:title">
          <xsl:value-of select="db:info/db:title"/>
        </xsl:when>
        <xsl:when test="db:caption">
          <xsl:value-of select="db:caption"/>
        </xsl:when>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:if test="@xml:id">
      <renvoi id="{@xml:id}"/>
    </xsl:if>
    
    <tableau width="{tc:table-width(., '80%')}" border="{tc:table-border(., 1)}" sautDePagePdf="0">
      <xsl:if test="$caption">
        <xsl:attribute name="legende" select="$caption"/>
      </xsl:if>

      <xsl:apply-templates mode="content"
        select="child::node()/*[not(db:title) and not(db:info) and not(db:caption)]"/>
    </tableau>
  </xsl:template>
  
  <xsl:template mode="content" match="db:tfoot">
    <xsl:message>WARNING: Table footers are not supported in the destination format.</xsl:message>
  </xsl:template>

  <xsl:template mode="content" match="db:thead | db:tbody | db:tgroup">
    <xsl:apply-templates mode="content"/>
  </xsl:template>

  <xsl:template mode="content" match="db:tr | db:row">
    <xsl:element name="{if (ancestor::db:thead or db:th) then 'entete' else 'ligne'}">
      <xsl:apply-templates mode="content"/>
    </xsl:element>
  </xsl:template>

  <xsl:template mode="content" match="db:th | db:td | db:entry">
    <colonne useText="0">
      <xsl:choose>
        <xsl:when
          test="db:para | db:note | db:itemizedlist | db:orderedlist | db:variablelist | db:mediaobject | db:programlisting | db:screen | db:informaltable">
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
    
    <xsl:if test="count(db:mediaobject) &gt; 1">
      <xsl:message>WARNING: Multiple mediaobject within a figure: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:imageobject) &gt; 1">
      <xsl:message>WARNING: Multiple imageobject within a mediaobject of a figure: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:imagedata) &gt; 1">
      <xsl:message>WARNING: Multiple imagedata within a imageobject in a mediaobject of a figure: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:imageobject) &gt; 1 and (count(//db:videoobject) &gt; 1 or count(//db:audioobject) &gt; 1)">
      <xsl:message>WARNING: Multiple objects in a figure mediaobject, including an imageobject that is ignored.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:videoobject) &gt; 1">
      <xsl:message>WARNING: Multiple videoobject within an inlinemediaobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:videodata) &gt; 1">
      <xsl:message>WARNING: Multiple videodata within a imageobject of an inlinemediaobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:audioobject) &gt; 1">
      <xsl:message>WARNING: Multiple audioobject within an inlinemediaobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count(//db:audiodata) &gt; 1">
      <xsl:message>WARNING: Multiple audiodata within a imageobject of an inlinemediaobject: only the first one is considered.</xsl:message>
    </xsl:if>

    <image>
      <xsl:attribute name="src" select="db:mediaobject[1]/db:imageobject[1]/db:imagedata[1]/@fileref"/>

      <xsl:if test="db:alt">
        <xsl:attribute name="alt" select="db:alt"/>
      </xsl:if>
      <!-- A figure must have a title, unlike an informalfigure. -->
      <xsl:if test="db:title">
        <xsl:attribute name="legende" select="db:title"/>
      </xsl:if>
    </image>
  </xsl:template>

  <xsl:template mode="content"
    match="db:constructorsynopsis | db:destructorsynopsis | db:enumsynopsis | db:typedefsynopsis | db:fieldsynopsis | db:methodsynopsis | db:classsynopsis | db:fieldsynopsis | db:namespacesynopsis"/>

  <xsl:template mode="content" match="db:para">
    <!-- The synopsis is done in <db:article>. -->
    <xsl:if
      test="..[self::db:section] or ..[self::db:sect1] or ..[self::db:listitem] or ..[self::db:blockquote] or ..[self::db:th] or ..[self::db:td] or ..[self::db:footnote] or ..[self::db:note] or ..[self::db:article] or string-length(name(preceding-sibling::*[1])) = 0">
      <xsl:choose>
        <xsl:when test="db:informaltable | db:note | db:programlisting | db:screen">
          <!-- Some content must be moved outside the paragraph (DocBook's model is really flexible). -->
          <xsl:variable name="children" select="child::node()" as="node()*"/>
          <xsl:variable name="firstTagOutsideParagraph" as="xs:integer">
            <xsl:variable name="isNotPara" select="
                for $i in 1 to count($children)
                return
                  boolean($children[$i][self::db:informaltable | self::db:note | self::db:programlisting | self::db:screen]) or name($children[$i]) = 'db:informaltable' or name($children[$i]) = 'db:note' or name($children[$i]) = 'db:programlisting' or name($children[$i]) = 'db:screen'"/>
            <xsl:value-of select="index-of($isNotPara, true())"/>
          </xsl:variable>

          <paragraph>
            <xsl:apply-templates mode="content_para"
              select="node()[position() &lt; $firstTagOutsideParagraph]"/>
          </paragraph>
          <xsl:apply-templates mode="content"
            select="node()[position() >= $firstTagOutsideParagraph]"/>
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

  <xsl:template mode="content" match="db:programlisting[@role = 'raw-html']">
    <html-brut>
      <xsl:apply-templates mode="content_para"/>
    </html-brut>
  </xsl:template>

  <xsl:template mode="content" match="db:programlisting[not(@role = 'raw-html')] | db:screen">
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

  <xsl:template mode="content" match="db:simplelist">
    <paragraph>
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
    </paragraph>
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
    <xsl:choose>
      <xsl:when test="count(db:listitem/node()[not(self::text())]) > 1">
        <!-- Complex case: several paragraphs in the listitem. Output several paragraphs. -->
        <element useText="0">
          <paragraph>
            <b>
              <xsl:apply-templates mode="content_para" select="db:term"/>
            </b>
            <xsl:text>&#0160;: </xsl:text>
          </paragraph>

          <xsl:apply-templates mode="content" select="db:listitem/*"/>
        </element>
      </xsl:when>
      <xsl:otherwise>
        <!-- Simple case: only one paragraph in the listitem. -->
        <element>
          <b>
            <xsl:apply-templates mode="content_para" select="db:term"/>
          </b>
          <xsl:text>&#0160;: </xsl:text>
          <xsl:apply-templates mode="content_para" select="db:listitem/db:para/*"/>
        </element>
      </xsl:otherwise>
    </xsl:choose>
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
    <xsl:message>WARNING: Tag <xsl:value-of select="name(.)"/> has no matching construct in the
      target format. Content is output as a caution.</xsl:message>
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
            <width>
              <xsl:value-of select="@width"/>
            </width>
          </xsl:if>

          <xsl:if test="@height">
            <height>
              <xsl:value-of select="@height"/>
            </height>
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

  <xsl:template mode="content_para content" match="db:equation">
    <xsl:if test="not(db:alt[@role = 'tex' or @role = 'latex'])">
      <xsl:message terminate="yes">ERROR: informalequation with no TeX or LaTeX encoding. MathML is
        not supported.</xsl:message>
    </xsl:if>

    <xsl:variable name="index" select="
        if (@xml:id) then
          @xml:id
        else
          generate-id()"/>
    <latex id="{$index}">
      <xsl:value-of select="db:alt[@role = 'tex' or @role = 'latex']/text()"/>
    </latex>
  </xsl:template>

  <xsl:template match="*[preceding-sibling::*[1][self::db:mediaobject]]" mode="content"
    priority="-1"> </xsl:template>

  <xsl:template mode="content" match="db:bridgehead">
    <!-- Due to poor formatting possibilities in the output format (like font size), just do the text in bold. -->
    <paragraph>
      <b>
        <xsl:apply-templates mode="content_para"/>
      </b>
    </paragraph>
  </xsl:template>

  <xsl:template mode="content" match="db:address">
    <!-- Due to poor formatting possibilities in the output format (like alignment), just do a plain paragraph. -->
    <paragraph>
      <xsl:apply-templates mode="content_para"/>
    </paragraph>
  </xsl:template>

  <xsl:template mode="content" match="db:anchor">
    <signet id="{@xml:id}"/>
  </xsl:template>

  <xsl:template mode="content" match="db:informalequation">
    <xsl:if test="not(db:alt[@role = 'tex' or @role = 'latex'])">
      <xsl:message terminate="yes">ERROR: informalequation with no TeX or LaTeX encoding. MathML is
        not supported.</xsl:message>
    </xsl:if>

    <xsl:variable name="index" select="
        if (@xml:id) then
          @xml:id
        else
          generate-id()"/>
    <latex id="{$index}">
      <xsl:value-of select="db:alt[@role = 'tex' or @role = 'latex']/text()"/>
    </latex>
  </xsl:template>

  <xsl:template mode="content" match="db:formalgroup">
    <xsl:if test="@xml:id">
      <signet id="{@xml:id}"/>
    </xsl:if>

    <tableau width="80%" border="0" legende="{if (db:title) then db:title else db:info/db:title}">
      <xsl:for-each select="*[not(self::db:title) and not(self::db:info)]">
        <ligne>
          <colonne useText="0">
            <xsl:apply-templates mode="content" select="."/>
          </colonne>
        </ligne>
      </xsl:for-each>
    </tableau>
  </xsl:template>
</xsl:stylesheet>
