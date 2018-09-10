<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://dourouc05.github.io"
  exclude-result-prefixes="xsl xs html saxon tc db"
  version="3.0">
  
  <xsl:output method="xml" indent="yes"
    suppress-indentation="db:code db:emphasis db:link db:programlisting db:title"/>
  <xsl:strip-space elements="*"/>
  <xsl:import-schema schema-location="article.xsd"/>   
  
  <xsl:template match="db:article">
    <xsl:result-document validation="lax">
      <document>
        <entete>
          <rubrique>65</rubrique>
          <meta>
            <description>
              <xsl:choose>
                <xsl:when test="db:info/db:abstract/db:para">
                  <xsl:value-of select="db:info/db:abstract/db:para[1]/text()"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="db:info/db:title"/>
                </xsl:otherwise>
              </xsl:choose>
            </description>
            <keywords>documentation, qt, français</keywords>
          </meta>
          <titre>
            <page>
              <xsl:value-of select="info/title"/>
            </page>
            <article>
              <xsl:value-of select="db:info/db:title"/>
            </article>
          </titre>
          <date>
            <xsl:value-of select="format-date(db:info/db:pubdate, '[Y0001]-[M01]-[D01]')"/>
          </date>
          <miseajour>
            <xsl:value-of select="format-date(db:info/db:date, '[Y0001]-[M01]-[D01]')"/>
          </miseajour>
          
          <includebas>include($_SERVER['DOCUMENT_ROOT'] . '/doc/pied.php');include($_SERVER['DOCUMENT_ROOT'] . '/template/pied.php');</includebas>
          
          <serveur>Qt</serveur>
          <xsl:variable name="url">
            <xsl:variable name="documentQdt" select="tokenize(base-uri(), '/')[last()]"/>
            <xsl:variable name="document" select="tokenize($documentQdt, '\.')[1]"/>
            <xsl:value-of select="concat('/doc/', lower-case(db:info/db:productname), '/', db:info/db:productnumber, '/', $document)"/>
          </xsl:variable>
          <chemin><xsl:value-of select="$url"/></chemin>
          <urlhttp>http://qt.developpez.com/<xsl:value-of select="$url"/></urlhttp>
        </entete>
        
        <xsl:if test="db:info/db:abstract/db:para[2]">
          <voiraussi>
            <!-- First, the linked documents (previous/next). -->
            <xsl:for-each select="db:info/db:abstract/db:para[2]/db:simplelist/db:member">
              <lien>
                <texte><xsl:value-of select="db:link/text()"/></texte>
                <url><xsl:value-of select="db:link/@href"/></url>
              </lien>
            </xsl:for-each>
            <!-- Then, anything else? -->
          </voiraussi>
        </xsl:if>
        
        <authorDescriptions>
          <xsl:choose>
            <xsl:when test="db:info/db:authorgroup">
              <xsl:for-each select="db:info/db:authorgroup/db:othercredit">
                <authorDescription name="{db:personname/db:othername[@role='pseudonym']}" role="traducteur">
                  <fullname>
                    <xsl:choose>
                      <xsl:when test="db:personname/db:firstname and not(db:personname/db:surname)">
                        <xsl:value-of select="db:personname/db:firstname"/>
                      </xsl:when>
                      <xsl:when test="not(db:personname/db:firstname) and db:personname/db:surname">
                        <xsl:value-of select="db:personname/db:surname"/>
                      </xsl:when>
                      <xsl:when test="db:personname/db:firstname and db:personname/db:surname">
                        <xsl:value-of select="concat(db:personname/db:firstname, ' ', db:personname/db:surname)"/>
                      </xsl:when>
                      <xsl:otherwise>
                        <xsl:value-of select="db:personname/db:othername[@role='pseudonym']"/>
                      </xsl:otherwise>
                    </xsl:choose>
                  </fullname>
                  <xsl:if test="db:uri">
                    <url><xsl:value-of select="db:uri"/></url>
                  </xsl:if>
                  <xsl:if test="db:uri">
                    <xsl:variable name="profileId" select="translate(tokenize(db:uri, '/')[5], 'u', '')"/>
                    <badge>https://www.developpez.com/ws/badgeimg?user=<xsl:value-of select="$profileId"/>&amp;v=1</badge>
                  </xsl:if>
                </authorDescription>
              </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
              <authorDescription name="Dummy" role="auteur">
                <fullname>Dummy</fullname>
                <url>https://www.developpez.net/forums/u1/dummy/</url>
              </authorDescription>
            </xsl:otherwise>
          </xsl:choose>
        </authorDescriptions>
        
        <synopsis>
          <paragraph>
            <xsl:apply-templates mode="content_para" select="db:info/db:abstract/db:para[1]/node()"/>
          </paragraph>
         </synopsis>
        
        <summary>
          <xsl:apply-templates mode="content" select="./*"/>
        </summary>
      </document>
    </xsl:result-document>
  </xsl:template>
  
  <!-- Block elements. -->
  <xsl:template mode="content" match="db:info"/>
  
  <xsl:template mode="content" match="db:section">
    <xsl:variable name="sectionId">
      <xsl:number level="multiple" format="1"/>
    </xsl:variable>
    <section id="{$sectionId}">
      <xsl:apply-templates mode="content"/>
    </section>
  </xsl:template>
  
  <xsl:template mode="content" match="db:title">
    <title><xsl:value-of select="."/></title>
  </xsl:template>
  
  <xsl:template mode="content" match="db:informaltable">
    <tableau>
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
      <xsl:apply-templates mode="content"/>
    </colonne>
  </xsl:template>
 
  <xsl:template mode="content" match="db:constructorsynopsis | db:destructorsynopsis | db:enumsynopsis | db:typedefsynopsis | db:fieldsynopsis | db:methodsynopsis"/>
 
  <xsl:template mode="content" match="db:para">
    <paragraph>
      <xsl:apply-templates mode="content_para"/>
    </paragraph>
  </xsl:template>
  
  <xsl:template mode="content" match="db:programlisting">
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
  
  <xsl:template mode="content" match="db:listitem">
    <element useText="0">
      <xsl:apply-templates mode="content"/>
    </element>
  </xsl:template>
  
  <!-- Within a paragraph. -->
  <xsl:template mode="content_para" match="db:emphasis">
    <xsl:choose>
      <xsl:when test="@role='bold'">
        <b><xsl:apply-templates mode="content_para"/></b>
      </xsl:when>
      <xsl:when test="@role='underline'">
        <u><xsl:apply-templates mode="content_para"/></u>
      </xsl:when>
      <xsl:otherwise>
        <i><xsl:apply-templates mode="content_para"/></i>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:code">
    <inline>
      <xsl:apply-templates mode="content_para"/>
    </inline>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:superscript">
    <sup>
      <xsl:apply-templates mode="content_para"/>
    </sup>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:subscript">
    <sub>
      <xsl:apply-templates mode="content_para"/>
    </sub>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:link">
    <link href="{@xlink:href}">
      <xsl:apply-templates mode="content_para"/>
    </link> 
  </xsl:template>
  
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="#all">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" /></xsl:message>
  </xsl:template>
</xsl:stylesheet>