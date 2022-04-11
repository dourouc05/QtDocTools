<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  exclude-result-prefixes="xsl xs html saxon tc db xlink map" version="3.0">
  <xsl:template mode="content_para" match="db:para">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:emphasis">
    <xsl:choose>
      <xsl:when test="not(parent::node()[self::db:code])">
        <!-- Nesting tags this way is not allowed (just text within <inline>). -->
        <xsl:choose>
          <xsl:when test="@role = 'bold' or @role = 'strong'">
            <b>
              <xsl:apply-templates mode="content_para"/>
            </b>
          </xsl:when>
          <xsl:when test="@role = 'underline'">
            <u>
              <xsl:apply-templates mode="content_para"/>
            </u>
          </xsl:when>
          <xsl:when test="@role = 'strike'">
            <s>
              <xsl:apply-templates mode="content_para"/>
            </s>
          </xsl:when>
          <xsl:otherwise>
            <i>
              <xsl:apply-templates mode="content_para"/>
            </i>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <!-- If nesting, just copy the text. -->
        <xsl:apply-templates mode="content_para"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template mode="content_para" match="db:code">
    <xsl:choose>
      <!-- To annoy people, <link>s cannot appear within <inline>. -->
      <!-- Handled within links, hence just let through here. -->
      <xsl:when test="db:link">
        <xsl:apply-templates mode="content_para"/>
      </xsl:when>
      <xsl:otherwise>
        <inline>
          <xsl:if test="@language">
            <xsl:attribute name="langage" select="@language"/>
          </xsl:if>

          <xsl:apply-templates mode="content_para"/>
        </inline>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template mode="content_para" match="db:superscript">
    <sup>
      <xsl:apply-templates mode="content_para"/>
    </sup>
  </xsl:template>

  <xsl:template mode="content_para" match="db:phrase[starts-with(@role, 'color:')]">
    <font color="{substring(@role, 7)}">
      <xsl:apply-templates mode="content_para"/>
    </font>
  </xsl:template>

  <xsl:template mode="content_para" match="db:subscript">
    <sub>
      <xsl:apply-templates mode="content_para"/>
    </sub>
  </xsl:template>

  <xsl:template mode="content_para"
    match="db:link[not(starts-with(@role, 'lien-forum')) and not(@linkend)]">
    <xsl:variable name="translatedLink" as="xs:string">
      <xsl:choose>
        <xsl:when test="$doc-qt and (ends-with(string(@xlink:href), '.webxml') or ends-with(string(@xlink:href), '.qdt'))">
          <xsl:variable name="filename" select="substring-before(substring-before(string(@xlink:href), '.webxml'), '.qdt')"/>
          <xsl:value-of
            select="concat('https://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"
          />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@xlink:href"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    
    <!-- Check the role, as it can match a "langue" in DvpML. -->
    <xsl:variable name="langue" as="xs:string?">
      <xsl:choose>
        <xsl:when test="lower-case(@role) = 'dico'"><xsl:value-of select="'Dico'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'en'"><xsl:value-of select="'En'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'faq'"><xsl:value-of select="'Faq'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'fr'"><xsl:value-of select="'Fr'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'icozip'"><xsl:value-of select="'Icozip'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'icopdf'"><xsl:value-of select="'Icopdf'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'src'"><xsl:value-of select="'Src'"/></xsl:when>
        <xsl:when test="lower-case(@role) = 'srcs'"><xsl:value-of select="'Srcs'"/></xsl:when>
      </xsl:choose>
    </xsl:variable>
    
    <!-- Generate the link. -->
    <xsl:variable name="generatedLink">
      <link href="{$translatedLink}">
        <xsl:if test="@xlink:title">
          <xsl:attribute name="title" select="@xlink:title"/>
        </xsl:if>
        <xsl:if test="$langue">
          <xsl:attribute name="langue" select="$langue"/>
        </xsl:if>
        
        <xsl:apply-templates mode="content_para"/>
      </link>
    </xsl:variable>

    <!-- Depending on the parent node, in order to fulfill the XSD's insane requirements,  -->
    <!-- conditionnally wrap the link (implemented here and not in the other tags). -->
    <xsl:choose>
      <xsl:when test="parent::node()[self::db:code]">
        <!-- No, this piece of "software" won't allow <inline><link>, that would be useful. -->
        <i>
          <xsl:copy-of select="$generatedLink"/>
        </i>
      </xsl:when>
      <xsl:otherwise>
        <xsl:copy-of select="$generatedLink"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template mode="content_para" match="db:link[starts-with(@role, 'lien-forum')]">
    <xsl:variable name="idpost" select="
        if (contains(@xlink:href, '#post')) then
          tokenize(@xlink:href, '#post')[2]
        else
          ''"/>
    <xsl:variable name="id" select="
        if (contains(@xlink:href, 'showthread.php')) then
          tokenize(replace(@xlink:href, '#post.*', ''), '\?t=')[2]
        else if (contains(@xlink:href, '/forums/d')) then
          tokenize(tokenize(@xlink:href, '/forums/d')[2], '/')[1]
        else ''"/>

    <lien-forum id="{$id}">
      <xsl:if test="$idpost">
        <xsl:attribute name="idpost" select="$idpost"/>
      </xsl:if>

      <xsl:if test="@role != 'lien-forum'">
        <!-- Should be 'lien-forum-avec-note' -->
        <xsl:attribute name="avecnote" select="'1'"/>
      </xsl:if>
    </lien-forum>
  </xsl:template>

  <xsl:template mode="content_para" match="db:link[@linkend]">
    <renvoi id="{@linkend}">
      <xsl:apply-templates mode="content_para"/>
    </renvoi>
  </xsl:template>

  <xsl:template mode="content_para" match="db:footnote">
    <noteBasPage>
      <xsl:for-each select="db:para">
        <xsl:apply-templates mode="content_para"/>

        <xsl:if test="position() &lt; last()">
          <br/>
        </xsl:if>
      </xsl:for-each>
    </noteBasPage>
  </xsl:template>

  <xsl:template mode="content_para" match="db:indexterm">
    <xsl:choose>
      <xsl:when test="db:primary and not(db:secondary)">
        <index>
          <xsl:value-of select="db:primary"/>
        </index>
      </xsl:when>
      <xsl:otherwise>
        <index id1="{db:primary}" id2="{db:secondary}"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template mode="content_para" match="db:biblioref">
    <renvoi id="{@endterm}">[<xsl:value-of select="$biblioRefs(xs:string(@endterm))"/>]</renvoi>
  </xsl:template>

  <xsl:template mode="content_para" match="db:inlinemediaobject">
    <xsl:if test="@xml:id">
      <signet id="{@xml:id}"/>
    </xsl:if>
    <xsl:call-template name="tc:generate-media">
      <xsl:with-param name="mediaobject" select="."/>
      <xsl:with-param name="alt" select="db:alt"/>
      <xsl:with-param name="link" select="@xlink:href"/>
      <xsl:with-param name="title" select="db:title"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template mode="content_para" match="db:xref">
    <!-- TODO: add chapter/section/appendix numbers if relevant. Quite complicated to do... -->
    <!-- See https://github.com/docbook/xslTNG/blob/main/src/main/xslt/modules/xref.xsl -->
    <xsl:variable name="soughtId" as="xs:string" select="@linkend"/>
    <xsl:variable name="pointee" select="$document//*[@xml:id = $soughtId]"/>
    <xsl:variable name="title" as="xs:string">
      <xsl:choose>
        <xsl:when test="$pointee/db:title">
          <xsl:value-of select="$pointee/db:title"/>
        </xsl:when>
        <xsl:when test="$pointee/db:info/db:title">
          <xsl:value-of select="$pointee/db:info/db:title"/>
        </xsl:when>
        <xsl:when test="$pointee/ancestor::db:bridgehead">
          <xsl:value-of select="$pointee/ancestor::db:bridgehead"/>
        </xsl:when>
        <xsl:otherwise>[]</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <renvoi id="{$soughtId}">
      <xsl:value-of select="normalize-space($title)"/>
    </renvoi>
  </xsl:template>

  <xsl:template mode="content_para" match="db:anchor">
    <signet id="{@xml:id}"/>
  </xsl:template>

  <xsl:template mode="content_para" match="db:personname">
    <!-- Semantic markup, no need to have a specific output. -->
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>

  <xsl:template mode="content_para" match="db:term">
    <!-- Allow recursion within variablelist entries. -->
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:simplelist">
    <xsl:message>WARNING: the current simplelist encoding implies losses in the target format,
      i.e. round tripping will not be exact.</xsl:message>
    
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

  <xsl:template mode="content_para" match="db:equation | db:inlineequation">
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
</xsl:stylesheet>
