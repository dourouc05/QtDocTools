<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  exclude-result-prefixes="xsl xs html saxon tc db xlink map" version="3.0">
  <xsl:template mode="content_para_no_formatting" match="db:emphasis | db:code | db:superscript | db:subscript | db:phrase | db:link | db:personname | db:term">
    <xsl:apply-templates mode="content_para_no_formatting"/>
  </xsl:template>
  
  <xsl:template mode="content_para_no_formatting" match="db:footnote | db:indexterm | db:biblioref | db:inlinemediaobject | db:xref | db:anchor | db:inlineequation"/>
</xsl:stylesheet>
