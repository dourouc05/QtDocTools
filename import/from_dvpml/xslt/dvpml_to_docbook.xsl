<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
    xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
    exclude-result-prefixes="xsl xs html saxon tc"
    version="3.0">
    
    <xsl:output method="xml" indent="yes"/>
    
    <xsl:function name="tc:parse-date" as="xs:date">
        <xsl:param name="date" required="true" as="xs:string"/>
        
        <xsl:choose>
            <xsl:when test="contains($date, '/')">
                <!-- Assumed format: DD/MM/YYYY -->
                <xsl:variable name="parsed" select="tokenize($date, '/')"/>
                <xsl:value-of select="xs:date(concat($parsed[3], '-', $parsed[2], '-', $parsed[1]))"/>
            </xsl:when>
            <xsl:when test="contains($date, '-')">
                <!-- Assumed format: DD-MM-YYYY -->
                <xsl:value-of select="xs:date($date)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message>WARNING: unable to parse date <xsl:value-of select="$date"/></xsl:message>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:template match="document">
        <db:article version="5.2">
            <db:info>
                <db:title><xsl:value-of select="entete/titre/article"/></db:title>
                
                <db:abstract>
                    <xsl:apply-templates mode="content" select="synopsis"/>
                </db:abstract>
                
                <xsl:if test="entete/date">
                    <db:pubdate><xsl:value-of select="tc:parse-date(entete/date/text())"/></db:pubdate>
                </xsl:if>
                <xsl:if test="entete/miseajour">
                    <db:date><xsl:value-of select="tc:parse-date(entete/miseajour/text())"/></db:date>
                </xsl:if>
                
                <db:authorgroup>
                    <xsl:for-each select="authorDescriptions/authorDescription">
                        <xsl:element name="{if (@role='auteur') then 'db:author' else 'db:othercredit'}">
                            <xsl:if test="@role='correcteur' or @role='gabarisateur' or @role='relecteur-technique' or @role='traducteur'">
                                <xsl:attribute name="class">
                                    <xsl:choose>
                                        <xsl:when test="@role='correcteur'">proofreader</xsl:when>
                                        <xsl:when test="@role='gabarisateur'">conversion</xsl:when>
                                        <xsl:when test="@role='relecteur-technique'">reviewer</xsl:when>
                                        <xsl:when test="@role='traducteur'">translator</xsl:when>
                                    </xsl:choose>
                                </xsl:attribute>
                            </xsl:if>
                            
                            <db:personname>
                                <db:othername role="pseudonym">
                                    <xsl:value-of select="@name"/>
                                </db:othername>
                                <!-- Heuristic to decide first and last name: split on the FIRST space. -->
                                <!-- Works for most cases. -->
                                <db:firstname>
                                    <xsl:value-of select="tokenize(fullname, ' ')[1]"/>
                                </db:firstname>
                                <db:lastname>
                                    <xsl:value-of select="string-join(for $i in 2 to last() + 1 return tokenize(fullname, ' ')[$i], ' ')"/>
                                </db:lastname>
                            </db:personname>
                            
                            <xsl:if test="url">
                                <db:uri type="main-uri">
                                    <xsl:value-of select="url"/>
                                </db:uri>
                            </xsl:if>
                            
                            <xsl:if test="homepage">
                                <db:uri type="{homepage/title}">
                                    <xsl:value-of select="homepage/url"/>
                                </db:uri>
                            </xsl:if>
                            
                            <xsl:if test="blog">
                                <db:uri type="blog-uri">
                                    <xsl:value-of select="blog"/>
                                </db:uri>
                            </xsl:if>
                            
                            <xsl:if test="google-plus">
                                <db:uri type="google-plus">
                                    <xsl:value-of select="google-plus"/>
                                </db:uri>
                            </xsl:if>
                            
                            <xsl:if test="linkedin">
                                <db:uri type="linkedin">
                                    <xsl:value-of select="linkedin"/>
                                </db:uri>
                            </xsl:if>
                            
                            <!-- Element "liens" is voluntarily ignored (not really used). -->
                        </xsl:element>
                    </xsl:for-each>
                </db:authorgroup>
            </db:info>
            
            <xsl:apply-templates select="summary" mode="content"/>
        </db:article>
    </xsl:template>
    
    <xsl:template mode="content" match="section">
        <db:section>
            <xsl:apply-templates mode="content"/>
        </db:section>
    </xsl:template>
    <xsl:template mode="content" match="title">
        <db:title>
            <xsl:apply-templates mode="content"/>
        </db:title>
    </xsl:template>
    
    <xsl:template mode="content" match="paragraph">
        <db:para>
            <xsl:apply-templates mode="content"/>
        </db:para>
    </xsl:template>
    <xsl:template mode="content" match="code">
        <db:programlisting>
            <xsl:if test="@langage">
                <xsl:attribute name="language" select="@language"/>
            </xsl:if>
            <xsl:if test="@showLines">
                <xsl:attribute name="linenumbering" select="if (@showLines = '0') then 'unnumbered' else 'numbered'"/>
            </xsl:if>
            <xsl:if test="@startLine">
                <xsl:attribute name="startinglinenumber" select="@startLine"/>
            </xsl:if>
            <!-- Attributes title, fichier, dissimulable are suppressed, as they have no matching in DocBook. -->
            
            <xsl:apply-templates mode="content"/>
        </db:programlisting>
    </xsl:template>
    
    <xsl:template mode="content" match="liste">
        <xsl:element name="{if (@type='none' or not(@type)) then 'db:itemizedlist' else 'db:orderedlist'}">
            <!-- Numeration symbol for this ordered list (no if will match for itemizedlist). -->
            <xsl:if test="@type='i'">
                <xsl:attribute name="numeration" select="'lowerroman'"/>
            </xsl:if>
            <xsl:if test="@type='I'">
                <xsl:attribute name="numeration" select="'upperroman'"/>
            </xsl:if>
            <xsl:if test="@type='a'">
                <xsl:attribute name="numeration" select="'loweralpha'"/>
            </xsl:if>
            <xsl:if test="@type='A'">
                <xsl:attribute name="numeration" select="'upperalpha'"/>
            </xsl:if>
            <xsl:if test="@type='1'">
                <xsl:attribute name="numeration" select="'arabic'"/>
            </xsl:if>
            
            <!-- First number of the list. -->
            <!-- Limitation: DocBook only allows restarting at 1 (default) -->
            <!-- or continuing the previous list (this case). -->
            <xsl:if test="@debut-numerotation">
                <xsl:attribute name="continuation" select="'continues'"/>
            </xsl:if>
            
            <!-- Title -->
            <xsl:if test="@titre">
                <db:title>
                    <xsl:value-of select="@titre"/>
                </db:title>
            </xsl:if>
            
            <xsl:apply-templates mode="content"/>
        </xsl:element>
    </xsl:template>
    <xsl:template mode="content" match="element">
        <xsl:choose>
            <xsl:when test="@useText='0'">
                <!-- With useText="0", to be valid, the input document needs paragraphs inside. -->
                <db:listitem>
                    <xsl:apply-templates mode="content"/>
                </db:listitem>
            </xsl:when>
            <xsl:otherwise>
                <!-- Otherwise, add it here. -->
                <db:listitem>
                    <db:para>
                        <xsl:apply-templates mode="content"/>
                    </db:para>
                </db:listitem>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template mode="content" match="i">
        <db:emphasis>
            <xsl:apply-templates mode="content"/>
        </db:emphasis>
    </xsl:template>
    <xsl:template mode="content" match="b | important">
        <db:emphasis role="bold">
            <xsl:apply-templates mode="content"/>
        </db:emphasis>
    </xsl:template>
    <xsl:template mode="content" match="u">
        <db:emphasis role="underline">
            <xsl:apply-templates mode="content"/>
        </db:emphasis>
    </xsl:template>
    <xsl:template mode="content" match="s">
        <db:emphasis role="strike">
            <xsl:apply-templates mode="content"/>
        </db:emphasis>
    </xsl:template>
    <xsl:template mode="content" match="sup">
        <db:superscript>
            <xsl:apply-templates mode="content"/>
        </db:superscript>
    </xsl:template>
    <xsl:template mode="content" match="sub">
        <db:subscript>
            <xsl:apply-templates mode="content"/>
        </db:subscript>
    </xsl:template>
    <xsl:template mode="content" match="inline">
        <db:code>
            <xsl:apply-templates mode="content"/>
        </db:code>
    </xsl:template>
    <!-- br intentionnally skipped (no meaning in DocBook). -->
    <xsl:template mode="content" match="link">
        <db:link xlink:href="{@href}">
            <!-- All other attributes are lost (target, onclick, title, langue). -->
            <xsl:apply-templates mode="content"/>
        </db:link>
    </xsl:template>
    <xsl:template mode="content" match="acronyme">
        <db:acronym>
            <xsl:apply-templates mode="content"/>
            <xsl:value-of select="concat(' (', @title, ')')"/>
        </db:acronym>
    </xsl:template>
    <xsl:template mode="content" match="signet">
        <db:anchor xml:id="{@id}"/>
        <xsl:apply-templates mode="content"/>
    </xsl:template>
    <xsl:template mode="content" match="signet">
        <db:indexterm>
            <db:primary><xsl:value-of select="@id1"/></db:primary>
            <xsl:if test="@id2">
                <db:secondary><xsl:value-of select="@id2"/></db:secondary>
            </xsl:if>
        </db:indexterm>
        <xsl:apply-templates mode="content"/>
    </xsl:template>
    <xsl:template mode="content" match="lien-forum">
        <!-- Stars will not be shown here. -->
        <db:link xlink:href="{concat('https://www.developpez.net/forums/showthread.php?t=', @id)}">
            <xsl:apply-templates mode="content"/>
        </db:link>
    </xsl:template>
</xsl:stylesheet>