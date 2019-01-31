<xsl:stylesheet version="1.0"
  xmlns="http://docbook.org/ns/docbook"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:h="http://www.w3.org/1999/xhtml"
  exclude-result-prefixes="h">
  
  <xsl:import href="w2x:xslt/docbook5.xslt"/>
  
  <xsl:template match="h:tt">
    <code>
      <xsl:call-template name="processCommonAttributes"/>
      <xsl:call-template name="simpleInlineContent2"/>
    </code>
  </xsl:template>
  
  <xsl:template match="h:span[../self::h:pre]">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="h:div[@class = 'role-note']">
    <note>
      <xsl:call-template name="processCommonAttributes"/>
      <xsl:apply-templates/>
    </note>
  </xsl:template>
  
  <!--
  <xsl:template match="h:div[@class = 'role-note']">
    <note>
      <xsl:call-template name="processCommonAttributes"/>
      <xsl:apply-templates/>
    </note>
  </xsl:template>
  -->  
</xsl:stylesheet>
