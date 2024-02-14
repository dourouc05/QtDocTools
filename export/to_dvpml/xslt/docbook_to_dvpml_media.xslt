<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  exclude-result-prefixes="xsl xs html saxon tc db xlink map" version="3.0">
  <xsl:template name="tc:generate-media">
    <xsl:param name="mediaobject"/>
    <xsl:param name="caption"/>
    <xsl:param name="title"/>
    <xsl:param name="link"/>
    <xsl:param name="alt"/>
    <xsl:param name="extension"/>
    <xsl:param name="format"/>
    
    <xsl:if test="not($mediaobject[self::db:mediaobject]) and not($mediaobject[self::db:inlinemediaobject])">
      <xsl:message terminate="yes">ASSERTION FAILED: not a mediaobject or inlinemediaobject.</xsl:message>
    </xsl:if>
    
    <xsl:if test="count($mediaobject//db:imageobject) &gt; 1">
      <xsl:message>WARNING: Multiple imageobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count($mediaobject//db:imagedata) &gt; 1">
      <xsl:message>WARNING: Multiple imagedata within an imageobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count($mediaobject//db:imageobject) &gt; 1 and (count(//db:videoobject) &gt; 1 or count(//db:audioobject) &gt; 1)">
      <xsl:message>WARNING: Multiple objects in a mediaobject, including an imageobject that is ignored.</xsl:message>
      <!-- Rationale: if there is a video plus an image, the image is likely a placeholder while the video loads. -->
    </xsl:if>
    <xsl:if test="count($mediaobject//db:videoobject) &gt; 1">
      <xsl:message>WARNING: Multiple videoobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count($mediaobject//db:videodata) &gt; 1">
      <xsl:message>WARNING: Multiple videodata within a videoobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count($mediaobject//db:audioobject) &gt; 1">
      <xsl:message>WARNING: Multiple audioobject: only the first one is considered.</xsl:message>
    </xsl:if>
    <xsl:if test="count($mediaobject//db:audiodata) &gt; 1">
      <xsl:message>WARNING: Multiple audiodata within an audioobject: only the first one is considered.</xsl:message>
    </xsl:if>
    
    <xsl:variable name="link_" as="xs:string?">
      <xsl:choose>
        <xsl:when test="$link">
          <xsl:value-of select="$link"/>
        </xsl:when>
        <xsl:when test="$mediaobject/@xlink:href">
          <xsl:value-of select="$mediaobject/@xlink:href"/>
        </xsl:when>
        <xsl:when test="$mediaobject/db:videoobject[1]/@xlink:href">
          <xsl:value-of select="$mediaobject/db:videoobject[1]/@xlink:href"/>
        </xsl:when>
        <xsl:when test="$mediaobject/db:audioobject[1]/@xlink:href">
          <xsl:value-of select="$mediaobject/db:audioobject[1]/@xlink:href"/>
        </xsl:when>
        <xsl:when test="$mediaobject/db:imageobject[1]/@xlink:href">
          <xsl:value-of select="$mediaobject/db:imageobject[1]/@xlink:href"/>
        </xsl:when>
        <!-- db:imagedata and friends do not have linking attributes. -->
      </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="alt_" as="xs:string?">
      <xsl:choose>
        <xsl:when test="$alt">
          <xsl:value-of select="$alt"/>
        </xsl:when>
        <xsl:when test="$mediaobject/db:alt">
          <xsl:value-of select="$mediaobject/db:alt"/>
        </xsl:when>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="title_" as="xs:string?">
      <xsl:choose>
        <xsl:when test="$title">
          <xsl:value-of select="$title"/>
        </xsl:when>
        <xsl:when test="$mediaobject/db:title">
          <xsl:value-of select="$mediaobject/db:title"/>
        </xsl:when>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="caption_" as="xs:string?">
      <xsl:choose>
        <xsl:when test="$caption">
          <xsl:value-of select="$caption"/>
        </xsl:when>
        <xsl:when test="$mediaobject/db:caption">
          <xsl:value-of select="$mediaobject/db:caption"/>
        </xsl:when>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="svg-filename" as="xs:string?">
      <xsl:if test="$mediaobject/db:imageobject[1]/db:imagedata[1]//@version">
        <xsl:variable name="svg-id" as="xs:int" select="$mediaobject/db:imageobject[1]/db:imagedata[1]/child::*[1]/count(preceding::db:imagedata)"/>
        <xsl:value-of select="concat('./images/svg_', $svg-id, '.svg')"/>
      </xsl:if>
    </xsl:variable>
    <xsl:if test="$svg-filename">
      <xsl:result-document method="xml" href="{$svg-filename}">
        <xsl:copy-of select="$mediaobject/db:imageobject[1]/db:imagedata[1]/child::*[1]"></xsl:copy-of>
      </xsl:result-document>
    </xsl:if>
    
    <xsl:variable name="filename_" as="xs:string">
      <xsl:choose>
        <xsl:when test="$svg-filename"><xsl:value-of select="$svg-filename"/></xsl:when>
        <xsl:when test="$mediaobject/db:videoobject"><xsl:value-of select="$mediaobject/db:videoobject[1]/db:videodata[1]/@fileref"/></xsl:when>
        <xsl:when test="$mediaobject/db:audioobject"><xsl:value-of select="$mediaobject/db:audioobject[1]/db:audiodata[1]/@fileref"/></xsl:when>
        <xsl:when test="$mediaobject/db:imageobject"><xsl:value-of select="$mediaobject/db:imageobject[1]/db:imagedata[1]/@fileref"/></xsl:when>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="video-type" as="xs:string">
      <xsl:choose>
        <xsl:when test="$format = 'youtube'"><xsl:value-of select="'youtube'"/></xsl:when>
        <xsl:when test="$extension"><xsl:value-of select="$extension"/></xsl:when>
        <xsl:otherwise><xsl:value-of select="tokenize($filename_, '\.')[last()]"/></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:choose>
      <xsl:when test="$mediaobject/db:imageobject and not($mediaobject/db:videoobject) and not($mediaobject/db:audioobject)">        
        <image>
          <xsl:attribute name="src" select="$filename_"/>
          
          <xsl:if test="$alt_">
            <xsl:attribute name="alt" select="$alt_"/>
          </xsl:if>
          <xsl:if test="$title_">
            <xsl:attribute name="titre" select="$title_"/>
          </xsl:if>
          <xsl:if test="$caption_">
            <xsl:attribute name="legende" select="$caption_"/>
          </xsl:if>
          <xsl:if test="string-length($link_) &gt; 0">
            <xsl:attribute name="href" select="$link_"/>
          </xsl:if>
          <!-- TODO: support zoom. -->
        </image>
      </xsl:when>
      <xsl:when test="$mediaobject/db:videoobject or $mediaobject/db:audioobject">
        <xsl:variable name="width" select="if ($mediaobject/db:videoobject[1]/db:videodata[1]/@width) then $mediaobject/db:videoobject[1]/db:videodata[1]/@width else if ($mediaobject/db:videoobject[1]/db:videodata[1]/@width) then $mediaobject/db:videoobject[1]/db:videodata[1]/@contentwidth else -1"/>
        <xsl:variable name="height" select="if ($mediaobject/db:videoobject[1]/db:videodata[1]/@depth) then $mediaobject/db:videoobject[1]/db:videodata[1]/@depth else if ($mediaobject/db:videoobject[1]/db:videodata[1]/@contentdepth) then $mediaobject/db:videoobject[1]/db:videodata[1]/@contentdepth else -1"/>
        
        <animation type="{$video-type}">
          <xsl:if test="$mediaobject/db:videoobject">
            <xsl:if test="$width &gt; 0">
              <width><xsl:value-of select="$width"/></width>
            </xsl:if>
            <xsl:if test="$height &gt; 0">
              <height><xsl:value-of select="$height"/></height>
            </xsl:if>
          </xsl:if>
          
          <xsl:if test="$title_">
            <title><xsl:value-of select="$title_"/></title>
          </xsl:if>
          
          <param-movie>
            <xsl:choose>
              <xsl:when test="$video-type = 'youtube'">
                <!-- If you have: https://www.youtube.com/watch?v=XXXX&feature=related -->
                <!-- Output: https://www.youtube.com/v/XXXX -->
                <xsl:value-of select="tokenize(replace($filename_, 'watch?v=', 'v/'), '&amp;')[1]"/>
              </xsl:when>
              <xsl:otherwise><xsl:value-of select="$filename_"/></xsl:otherwise>
            </xsl:choose>
          </param-movie>
        </animation>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
