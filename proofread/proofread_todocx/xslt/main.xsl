<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xfc="http://www.xmlmind.com/foconverter/xsl/extensions"
    xmlns:t="http://docbook.org/xslt/ns/template"
    xmlns:f="http://docbook.org/xslt/ns/extension"
    xmlns:m="http://docbook.org/xslt/ns/mode"
    xmlns:mp="http://docbook.org/xslt/ns/mode/private"
    xmlns:db="http://docbook.org/ns/docbook"
    exclude-result-prefixes="xs t f m mp"
    version="3.0">
    
    <xsl:import href="docbook_xsl2/fo/final-pass.xsl"/>
    
    <xsl:attribute-set name="monospace.verbatim.properties" use-attribute-sets="verbatim.properties monospace.properties">
        <xsl:attribute name="text-align">start</xsl:attribute>
        <xsl:attribute name="wrap-option">no-wrap</xsl:attribute>
        <xsl:attribute name="xfc:user-style">ProgramListing</xsl:attribute>
    </xsl:attribute-set>
    
    <!-- For sections: titlepage-mode.xsl -->
    <xsl:template match="db:title" mode="m:titlepage-mode">
        <xsl:variable name="xfcStyle" as="xs:string">
            <xsl:variable name="isSect" select="self::sect1 or self::sect2 or self::sect3 or self::sect4 or self::sect5 or self::sect6"/>
            <xsl:choose>
                <xsl:when test="count(ancestor::db:section) = 0 and not($isSect)"><xsl:value-of select="'Title'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 1 and not($isSect)) or self::sect1"><xsl:value-of select="'Heading 1'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 2 and not($isSect)) or self::sect2"><xsl:value-of select="'Heading 2'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 3 and not($isSect)) or self::sect3"><xsl:value-of select="'Heading 3'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 4 and not($isSect)) or self::sect4"><xsl:value-of select="'Heading 4'"/></xsl:when>
                <xsl:when test="(count(ancestor::db:section) = 5 and not($isSect)) or self::sect5"><xsl:value-of select="'Heading 5'"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="'Heading 6'"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <fo:block font-family="{$title.fontset}" xfc:user-style="{$xfcStyle}">
            <!-- The rest comes from the original style. -->
            <xsl:choose>
                <xsl:when test="not(parent::*)">
                    <xsl:apply-templates mode="m:titlepage-mode"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:variable name="context"
                        select="if (parent::db:info) then parent::db:info/parent::* else parent::*"/>
                    
                    <xsl:apply-templates select="$context" mode="m:object-title-markup">
                        <xsl:with-param name="allow-anchors" select="true()"/>
                    </xsl:apply-templates>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>
    
    <!-- For blocks of code, to output the language: verbatim.xsl -->
    <xsl:template match="db:programlisting|db:screen|db:synopsis|db:literallayout[@class='monospaced']" mode="m:verbatim">
        <xsl:param name="suppress-numbers" select="'0'"/>
        <xsl:variable name="id" select="f:node-id(.)"/>
        
        <xsl:variable name="pygments-pi" as="xs:string?" select="f:pi(/processing-instruction('dbhtml'), 'pygments')"/>
        <xsl:variable name="use-pygments" as="xs:boolean" select="$pygments-pi = 'true' or $pygments-pi = 'yes' or $pygments-pi = '1' or (contains(@role,'pygments') and not(contains(@role,'nopygments')))"/>
        
        <xsl:variable name="verbatim" as="node()*">
            <!-- n.b. look below where the class attribute is computed -->
            <xsl:choose>
                <xsl:when test="contains(@role,'nopygments') or string-length(.) &gt; 9000 or self::db:literallayout or exists(*)">
                    <xsl:sequence select="node()"/>
                </xsl:when>
                <xsl:when test="$pygments-default = 0 and not($use-pygments)">
                    <xsl:sequence select="node()"/>
                </xsl:when>
                <!-- Only for MarkLogic -->
                <!--
                <xsl:when use-when="function-available('xdmp:http-post')" test="$pygmenter-uri != ''">
                    <xsl:sequence select="ext:highlight(string(.), string(@language))"/>
                </xsl:when>
                -->
                <!-- Not using DocBook Java extensions -->
                <!--
                <xsl:when use-when="function-available('ext:highlight')" test="true()">
                    <xsl:sequence select="ext:highlight(string(.), string(@language))"/>
                </xsl:when>
                -->
                <xsl:otherwise>
                    <xsl:sequence select="node()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <xsl:variable name="qdoctoolsPrefix" as="text()">
            <!-- &#xa; is a new line -->
            <xsl:choose>
                <xsl:when test="@language and not(@language = '')">
                    <xsl:value-of select="concat('//&lt;QDOCTOOLS&gt; Programming language: ', @language, '&#xa;')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="'//&lt;QDOCTOOLS&gt; No programming language&#xa;'"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <xsl:variable name="xfcStyle" as="xs:string">
            <xsl:choose>
                <xsl:when test="db:screen">
                    <xsl:value-of select="'screen'"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="'programlisting'"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <xsl:choose>
            <xsl:when test="$shade.verbatim != 0">
                <fo:block id="{$id}" xsl:use-attribute-sets="monospace.verbatim.properties shade.verbatim.style" xfc:user-style="{$xfcStyle}">
                    <xsl:value-of select="$qdoctoolsPrefix"/>
                    <xsl:apply-templates select="$verbatim" mode="m:verbatim"/>
                </fo:block>
            </xsl:when>
            <xsl:otherwise>
                <fo:block id="{$id}" xsl:use-attribute-sets="monospace.verbatim.properties" xfc:user-style="{$xfcStyle}">
                    <xsl:value-of select="$qdoctoolsPrefix"/>
                    <xsl:apply-templates select="$verbatim" mode="m:verbatim"/>
                </fo:block>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- For abstract, clearly delimitate it by a box: titlepage-mode.xsl. -->
    <xsl:template match="db:abstract" mode="m:titlepage-mode">
        <fo:table-and-caption>
            <fo:table border="thin dotted black" table-layout="auto" space-before="1em">
                <fo:table-column column-number="1" column-width="proportional-column-width(1)"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell>
                            <!-- Original code. -->
                            <fo:block font-family="{$body.font.family}" space-before="1em">
                                <!-- Display the "abstract" title. -->
                                <xsl:call-template name="t:titlepage"/>
                                
                                <!-- Abstract content. -->
                                <xsl:variable name="dir" select="f:dir(.)"/>
                                <fo:block text-align="{if ($dir='ltr' or $dir='lro') then 'right' else 'left'}" line-height="1" border="thick solid black">
                                    <xsl:apply-templates/>
                                </fo:block>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:table-and-caption>
    </xsl:template>
</xsl:stylesheet>