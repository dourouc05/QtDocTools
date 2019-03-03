<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    xmlns="http://docbook.org/ns/docbook"
    xmlns:db="http://docbook.org/ns/docbook"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs xsl"
    version="2.0">
    
    <xsl:param name="originalDocument" as="xs:string" select="'../tests/CPLEX_before.xml'"/>
    
    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="db:screen db:literallayout db:programlisting db:address"/>
    
    <xsl:template match="@*|node()" mode="#all" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="db:section">
        <section>
            <!-- If there is some metadata in the original document, copy it. -->
            
            <xsl:apply-templates/>
        </section>
    </xsl:template>
</xsl:stylesheet>