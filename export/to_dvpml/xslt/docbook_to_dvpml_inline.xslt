<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  <xsl:template mode="content_para" match="db:emphasis">
    <xsl:choose>
      <xsl:when test="not(parent::node()[self::db:code])">
        <!-- Nesting tags this way is not allowed (just text within <inline>).  -->
        <xsl:choose>
          <xsl:when test="@role='bold' or @role='strong'">
            <b><xsl:apply-templates mode="content_para"/></b>
          </xsl:when>
          <xsl:when test="@role='underline'">
            <u><xsl:apply-templates mode="content_para"/></u>
          </xsl:when>
          <xsl:when test="@role='strike'">
            <s><xsl:apply-templates mode="content_para"/></s>
          </xsl:when>
          <xsl:otherwise>
            <i><xsl:apply-templates mode="content_para"/></i>
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
  
  <xsl:template mode="content_para" match="db:link[not(starts-with(@role, 'lien-forum') or @linkend)]">
    <xsl:variable name="translatedLink" as="xs:string">
      <xsl:choose>
        <xsl:when test="ends-with(string(@xlink:href), '.webxml')">
          <xsl:variable name="filename" select="substring-before(string(@xlink:href), '.webxml')"/>
          <xsl:value-of select="concat('https://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"/>
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
    <xsl:variable name="idpost" select="if (contains(@xlink:href, '#post')) then tokenize(@xlink:href, '#post')[2] else ''"/>
    <xsl:variable name="id" select="tokenize(replace(@xlink:href, '#post.*', ''), '?t=')[2]"/>
    
    <lien-forum id="{$id}">
      <xsl:if test="@idpost != ''">
        <xsl:attribute name="idpost" select="$idpost"/>
      </xsl:if>
      
      <xsl:if test="@role != 'lien-forum'">
        <!-- Should be 'lien-forum-avec-note' -->
        <xsl:attribute name="avecnote" select="'1'"></xsl:attribute>
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
        <index><xsl:value-of select="@db:primary"/></index>
      </xsl:when>
      <xsl:otherwise>
        <index id1="{db:primary}" id2="{db:secondary}"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>