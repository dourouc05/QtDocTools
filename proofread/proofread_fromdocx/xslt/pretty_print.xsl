<?xml version='1.0'?>
<xsl:stylesheet version="2.0" 
    xmlns="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:xlink="http://www.w3.org/1999/xlink">
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <xsl:template match="*">
    <xsl:copy-of select="."></xsl:copy-of>
  </xsl:template>
</xsl:stylesheet>