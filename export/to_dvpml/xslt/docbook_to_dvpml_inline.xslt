<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  exclude-result-prefixes="xsl xs html saxon tc db xlink map" version="3.0">
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
        <xsl:when test="ends-with(string(@xlink:href), '.webxml')">
          <xsl:variable name="filename" select="substring-before(string(@xlink:href), '.webxml')"/>
          <xsl:value-of
            select="concat('https://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"
          />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@xlink:href"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <!-- Generate the link. -->
    <xsl:variable name="generatedLink">
      <link href="{$translatedLink}">
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
    <xsl:choose>
      <xsl:when test="db:imageobject and not(db:videoobject) and not(db:audioobject)">
        <xsl:variable name="link" as="xs:string?">
          <xsl:choose>
            <xsl:when test="@xlink:href">
              <xsl:value-of select="@xlink:href"/>
            </xsl:when>
            <xsl:when test="db:imageobject/@xlink:href">
              <xsl:value-of select="db:imageobject/@xlink:href"/>
            </xsl:when>
            <!-- db:imagedata does not have linking attributes. -->
          </xsl:choose>
        </xsl:variable>
        
        <image>
          <xsl:attribute name="src" select="db:imageobject[1]/db:imagedata[1]/@fileref"/>
          
          <xsl:if test="db:alt">
            <xsl:attribute name="alt" select="db:alt"/>
          </xsl:if>
          <xsl:if test="db:title">
            <xsl:attribute name="titre" select="db:title"/>
          </xsl:if>
          <xsl:if test="string-length($link) &gt; 0">
            <xsl:attribute name="href" select="$link"/>
          </xsl:if>
        </image>
      </xsl:when>
      <xsl:when test="db:videoobject or db:audioobject">
        <xsl:variable name="filename" select="if (db:videoobject) then db:videoobject[1]/db:videodata[1]/@fileref else db:audioobject[1]/db:audiodata[1]/@fileref"/>
        <xsl:variable name="extension" select="tokenize($filename, '\.')[last()]"/>
        <xsl:variable name="width" select="if (db:videoobject[1]/db:videodata[1]/@width) then db:videoobject[1]/db:videodata[1]/@width else if (db:videoobject[1]/db:videodata[1]/@width) then db:videoobject[1]/db:videodata[1]/@contentwidth else -1"/>
        <xsl:variable name="height" select="if (db:videoobject[1]/db:videodata[1]/@depth) then db:videoobject[1]/db:videodata[1]/@depth else if (db:videoobject[1]/db:videodata[1]/@contentdepth) then db:videoobject[1]/db:videodata[1]/@contentdepth else -1"/>
        
        <animation type="{$extension}">
          <xsl:if test="db:videoobject">
            <xsl:if test="$width &gt; 0">
              <width><xsl:value-of select="$width"/></width>
            </xsl:if>
            <xsl:if test="$height &gt; 0">
              <height><xsl:value-of select="$height"/></height>
            </xsl:if>
          </xsl:if>
          
          <xsl:if test="db:title">
            <title><xsl:value-of select="db:title"></xsl:value-of></title>
          </xsl:if>
          
          <param-movie><xsl:value-of select="$filename"/></param-movie>
        </animation>
      </xsl:when>
    </xsl:choose>
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

  <xsl:template mode="content_para" match="db:inlineequation">
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
