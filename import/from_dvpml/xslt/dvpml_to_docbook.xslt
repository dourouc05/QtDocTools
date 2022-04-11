<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc" version="3.0">

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
        <!-- Assumed format: DD-MM-YYYY. Directly understood by xs:date. -->
        <xsl:value-of select="xs:date($date)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: unable to parse date <xsl:value-of select="$date"/></xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:template match="document">
    <xsl:variable name="main-tag" as="xs:string">
      <xsl:choose>
        <xsl:when test="multi-page">
          <xsl:value-of select="'db:book'"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'db:article'"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <!-- Hypothesis about HTTP URLs: they are related to the FTP user and folder. -->
    <xsl:if test="entete/urlhttp and entete/serveur and entete/chemin">
      <xsl:variable name="guessed-http-url"
        select="concat('https://', entete/serveur, '.developpez.com/', entete/chemin)"/>
      <xsl:if test="not(entete/urlhttp = $guessed-http-url)">
        <xsl:message>WARNING: urlhttp is supposed to be the concatenation of serveur and chemin,
          i.e. <xsl:value-of select="$guessed-http-url"/></xsl:message>
      </xsl:if>
    </xsl:if>

    <xsl:variable name="output-json" as="xs:string" select="
        if (ends-with(current-output-uri(), '.xml')) then
          replace(current-output-uri(), '.xml', '.json')
        else
          concat(current-output-uri(), '.json')"/>
    <xsl:variable name="json-document" as="node()">
      <map xmlns="http://www.w3.org/2005/xpath-functions">
        <number key="section">
          <xsl:value-of select="entete/rubrique"/>
        </number>
        <xsl:if test="entete/lictype">
          <number key="license-number">
            <xsl:value-of select="entete/lictype"/>
          </number>
        </xsl:if>
        <xsl:if test="entete/licannee">
          <number key="license-year">
            <xsl:value-of select="entete/licannee"/>
          </number>
        </xsl:if>
        <xsl:if test="entete/licauteur">
          <number key="license-author">
            <xsl:value-of select="entete/licauteur"/>
          </number>
        </xsl:if>
        <xsl:if test="entete/licence">
          <number key="license-raw">
            <xsl:value-of select="entete/licence"/>
          </number>
        </xsl:if>
        <xsl:if test="entete/cssperso">
          <string key="extra-css">
            <xsl:value-of select="entete/cssperso"/>
          </string>
        </xsl:if>
        <xsl:if test="entete/extratag">
          <string key="extra-php">
            <xsl:value-of select="entete/extratag"/>
          </string>
        </xsl:if>
        <xsl:if test="entete/serveur">
          <string key="ftp-server">
            <xsl:value-of select="entete/serveur"/>
          </string>
        </xsl:if>
        <xsl:if test="entete/chemin">
          <string key="ftp-folder">
            <xsl:value-of select="entete/chemin"/>
          </string>
        </xsl:if>
        <!-- Don't encode entete/urlhttp: its value is guessed from ftp-server and ftp-folder. -->
        <xsl:if test="lecteur/niveau">
          <number key="reader-level">
            <xsl:value-of select="lecteur/niveau/@type"/>
          </number>
        </xsl:if>
        <xsl:if test="lecteur/duree">
          <string key="reader-duration">
            <xsl:value-of select="lecteur/duree"/>
          </string>
        </xsl:if>
        <xsl:if test="//lien-forum">
          <string key="forum-topic">
            <xsl:value-of select="//lien-forum[1]/@id"/>
          </string>
        </xsl:if>
        <xsl:if test="//lien-forum[1]/@idpost">
          <!-- idpost is optional on a lien-forum, while id is not. -->
          <string key="forum-post">
            <xsl:value-of select="//lien-forum[1]/@idpost"/>
          </string>
        </xsl:if>
      </map>
    </xsl:variable>
    <xsl:result-document method="text" href="{$output-json}">
      <xsl:value-of select="xml-to-json($json-document, map {'indent': false()})"/>
    </xsl:result-document>

    <xsl:element name="{$main-tag}" inherit-namespaces="yes">
      <xsl:attribute name="version" select="'5.2'"/>

      <db:info>
        <db:title>
          <xsl:apply-templates mode="content" select="entete/titre/article"/>
        </db:title>
        <xsl:if test="entete/titre/page">
          <db:titleabbrev>
            <xsl:apply-templates mode="content" select="entete/titre/page"/>
          </db:titleabbrev>
        </xsl:if>
        <xsl:if test="soustitre">
          <db:subtitle>
            <xsl:apply-templates mode="content" select="soustitre"/>
          </db:subtitle>
        </xsl:if>

        <db:abstract>
          <xsl:apply-templates mode="content" select="synopsis"/>

          <xsl:if test="voiraussi">
            <db:para>
              <db:simplelist role="see-also">
                <xsl:for-each select="lien">
                  <db:link xlink:href="{url}">
                    <xsl:value-of select="texte"/>
                  </db:link>
                </xsl:for-each>
              </db:simplelist>
            </db:para>
          </xsl:if>
        </db:abstract>

        <xsl:if test="entete/meta/description">
          <db:abstract role="description">
            <db:para>
              <xsl:value-of select="entete/meta/description"/>
            </db:para>
          </db:abstract>
        </xsl:if>

        <xsl:if test="entete/date">
          <db:pubdate>
            <xsl:value-of select="tc:parse-date(entete/date/text())"/>
          </db:pubdate>
        </xsl:if>
        <xsl:if test="entete/miseajour">
          <db:date>
            <xsl:value-of select="tc:parse-date(entete/miseajour/text())"/>
          </db:date>
        </xsl:if>

        <xsl:if test="entete/meta/keywords">
          <db:keywordset>
            <xsl:for-each select="tokenize(., ',')">
              <db:keyword>
                <xsl:value-of select="normalize-space(.)"/>
              </db:keyword>
            </xsl:for-each>
          </db:keywordset>
        </xsl:if>

        <xsl:for-each select="authorDescriptions/authorDescription">
          <xsl:element name="{if (@role='auteur') then 'db:author' else 'db:othercredit'}">
            <xsl:if
              test="@role = 'correcteur' or @role = 'gabarisateur' or @role = 'relecteur-technique' or @role = 'traducteur'">
              <xsl:attribute name="class">
                <xsl:choose>
                  <xsl:when test="@role = 'correcteur'">proofreader</xsl:when>
                  <xsl:when test="@role = 'gabarisateur'">conversion</xsl:when>
                  <xsl:when test="@role = 'relecteur-technique'">reviewer</xsl:when>
                  <xsl:when test="@role = 'traducteur'">translator</xsl:when>
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
              <db:surname>
                <xsl:value-of select="
                    string-join(for $i in 2 to last() + 1
                    return
                      tokenize(fullname, ' ')[$i], ' ')"/>
              </db:surname>
            </db:personname>

            <xsl:if test="url">
              <db:uri type="main-uri">
                <xsl:value-of select="url"/>
              </db:uri>
            </xsl:if>

            <xsl:if test="homepage">
              <db:uri type="homepage">
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
      </db:info>

      <!-- Main content of the article. -->
      <xsl:choose>
        <xsl:when test="multi-page">
          <xsl:for-each select="multi-page/page">
            <db:chapter>
              <db:title>
                <xsl:value-of select="title"/>
              </db:title>

              <xsl:for-each select="link">
                <xsl:variable name="current-link" select="."/>
                <xsl:apply-templates select="//summary/section[@id = $current-link/@href]"
                  mode="content"/>
              </xsl:for-each>
            </db:chapter>
          </xsl:for-each>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="summary" mode="content"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:element>
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
        <xsl:attribute name="linenumbering" select="
            if (@showLines = '0') then
              'unnumbered'
            else
              'numbered'"/>
      </xsl:if>
      <xsl:if test="@startLine">
        <xsl:attribute name="startinglinenumber" select="@startLine"/>
      </xsl:if>
      <!-- Attributes title, fichier, dissimulable are suppressed, as they have no matching in DocBook. -->

      <xsl:apply-templates mode="content"/>
    </db:programlisting>
  </xsl:template>
  <xsl:template mode="content" match="image">
    <xsl:element name="{if (parent::paragraph) then 'db:inlinemediaobject' else 'db:mediaobject'}">
      <db:imageobject>
        <db:imagedata fileref="{@src}">
          <xsl:if test="@align">
            <xsl:attribute name="align" select="@align"/>
          </xsl:if>
        </db:imagedata>
      </db:imageobject>

      <xsl:if test="@alt">
        <db:textobject>
          <db:para>
            <xsl:value-of select="@alt"/>
          </db:para>
        </db:textobject>
      </xsl:if>

      <xsl:if test="@legende and @titre">
        <xsl:message>WARNING: both a legend and a title for an image; what should I do with
          that?</xsl:message>
      </xsl:if>
      <xsl:if test="@legende">
        <db:caption>
          <db:para>
            <xsl:value-of select="@legende"/>
          </db:para>
        </db:caption>
      </xsl:if>
      <xsl:if test="@titre">
        <db:caption>
          <db:para>
            <xsl:value-of select="@titre"/>
          </db:para>
        </db:caption>
      </xsl:if>
    </xsl:element>
  </xsl:template>
  <xsl:template mode="content" match="animation">
    <db:mediaobject>
      <db:videoobject>
        <db:videodata fileref="{@src}">
          <xsl:if test="width">
            <xsl:attribute name="width" select="width"/>
          </xsl:if>
          <xsl:if test="height">
            <xsl:attribute name="height" select="height"/>
          </xsl:if>
          <xsl:if test="@type">
            <xsl:attribute name="role" select="@type"/>
          </xsl:if>

          <xsl:if test="param-movie">
            <db:multimediaparam name="param-movie" value="{param-movie}"/>
          </xsl:if>
          <xsl:if test="param-quality">
            <db:multimediaparam name="param-quality" value="{param-quality}"/>
          </xsl:if>
          <xsl:if test="param-loop">
            <db:multimediaparam name="param-loop" value="{param-loop}"/>
          </xsl:if>
          <xsl:if test="param-wmode">
            <db:multimediaparam name="param-wmode" value="{param-wmode}"/>
          </xsl:if>
        </db:videodata>
      </db:videoobject>

      <xsl:if test="@image">
        <db:imageobject>
          <db:imagedata fileref="{@image}"/>
        </db:imageobject>
      </xsl:if>

      <xsl:if test="@alt">
        <db:textobject>
          <db:para>
            <xsl:value-of select="@alt"/>
          </db:para>
        </db:textobject>
      </xsl:if>

      <xsl:if test="@legende and @title">
        <xsl:message>WARNING: both a legend and a title for an image; what should I do with
          that?</xsl:message>
      </xsl:if>
      <xsl:if test="@legende">
        <db:caption>
          <db:para>
            <xsl:value-of select="@legende"/>
          </db:para>
        </db:caption>
      </xsl:if>
      <xsl:if test="@title">
        <db:caption>
          <db:para>
            <xsl:value-of select="@title"/>
          </db:para>
        </db:caption>
      </xsl:if>
    </db:mediaobject>
  </xsl:template>
  
  <xsl:function name="tc:dvpml-imgtext-type-to-docbook-admonition" as="xs:string">
    <xsl:param name="type" as="xs:string"/>
    
    <xsl:choose>
      <xsl:when test="$type = 'idea'">
        <xsl:value-of select="'db:tip'"/>
      </xsl:when>
      <xsl:when test="$type = 'info'">
        <xsl:value-of select="'db:note'"/>
      </xsl:when>
      <xsl:when test="$type = 'warning'">
        <xsl:value-of select="'db:warning'"/>
      </xsl:when>
      <xsl:when test="$type = 'error'">
        <xsl:value-of select="'db:caution'"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: custom imgtexts are not handled. How would you encode them into
          DocBook?</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  <xsl:template mode="content" match="imgtext">
    <xsl:element name="{tc:dvpml-imgtext-type-to-docbook-admonition(@type)}">
      <db:para>
        <xsl:apply-templates mode="content"/>
      </db:para>
    </xsl:element>
  </xsl:template>
  <xsl:template mode="content" match="rich-imgtext">
    <xsl:element name="{tc:dvpml-imgtext-type-to-docbook-admonition(@type)}">
      <xsl:apply-templates mode="content"/>
    </xsl:element>
  </xsl:template>
  
  <xsl:template mode="content" match="citation">
    <db:blockquote>
      <xsl:apply-templates mode="content"/>
    </db:blockquote>
  </xsl:template>

  <xsl:template mode="content" match="liste">
    <xsl:element
      name="{if (@type='none' or not(@type)) then 'db:itemizedlist' else 'db:orderedlist'}">
      <!-- Numeration symbol for this ordered list (no if will match for itemizedlist). -->
      <xsl:if test="@type = 'i'">
        <xsl:attribute name="numeration" select="'lowerroman'"/>
      </xsl:if>
      <xsl:if test="@type = 'I'">
        <xsl:attribute name="numeration" select="'upperroman'"/>
      </xsl:if>
      <xsl:if test="@type = 'a'">
        <xsl:attribute name="numeration" select="'loweralpha'"/>
      </xsl:if>
      <xsl:if test="@type = 'A'">
        <xsl:attribute name="numeration" select="'upperalpha'"/>
      </xsl:if>
      <xsl:if test="@type = '1'">
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
      <xsl:when test="@useText = '0'">
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

  <xsl:template mode="content" match="tableau">
    <xsl:element name="{if(@legende) then 'db:table' else 'db:informaltable'}">
      <xsl:if test="@border">
        <xsl:attribute name="border" select="@border"/>
      </xsl:if>
      <xsl:if test="@width">
        <xsl:attribute name="width" select="@width"/>
      </xsl:if>

      <xsl:if test="@legende">
        <caption>
          <xsl:value-of select="@legende"/>
        </caption>
      </xsl:if>

      <xsl:apply-templates mode="content"/>
    </xsl:element>
  </xsl:template>
  <xsl:template mode="content" match="entete">
    <db:thead>
      <db:tr>
        <xsl:apply-templates mode="content"/>
      </db:tr>
    </db:thead>
  </xsl:template>
  <xsl:template mode="content" match="ligne">
    <db:tr>
      <xsl:apply-templates mode="content"/>
    </db:tr>
  </xsl:template>
  <xsl:template mode="content" match="colonne">
    <xsl:element name="{if (parent::entete) then 'db:th' else 'db:td'}">
      <xsl:if test="@align">
        <xsl:attribute name="align" select="@align"/>
      </xsl:if>
      <xsl:if test="@colspan">
        <xsl:attribute name="colspan" select="@colspan"/>
      </xsl:if>
      <!-- color and width are not handled by DocBook. -->

      <xsl:choose>
        <xsl:when test="@useText = '0'">
          <xsl:apply-templates mode="content"/>
        </xsl:when>
        <xsl:otherwise>
          <db:para>
            <xsl:apply-templates mode="content"/>
          </db:para>
        </xsl:otherwise>
      </xsl:choose>

    </xsl:element>
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
  <xsl:template mode="content" match="font">
    <db:phrase role="{concat('color:', @color)}">
      <xsl:apply-templates mode="content"/>
    </db:phrase>
  </xsl:template>
  <xsl:template mode="content" match="latex">
    <xsl:element name="{if (parent::paragraph) then 'db:inlineequation' else 'db:equation'}">
      <xsl:if test="@id">
        <xsl:attribute name="xml:id" select="@id"/>
      </xsl:if>

      <db:alt role="latex">
        <xsl:value-of select="."/>
      </db:alt>
    </xsl:element>
  </xsl:template>
  <!-- br intentionnally skipped (no meaning in DocBook). -->
  <xsl:template mode="content" match="link">
    <xsl:if test="@onclick">
      <xsl:message>WARNING: The onclick attribute is not supported for links.</xsl:message>
    </xsl:if>
    
    <db:link xlink:href="{@href}">
      <xsl:if test="@langue">
        <xsl:attribute name="role" select="@langue"/>
      </xsl:if>
      <xsl:if test="@title">
        <xsl:attribute name="title" select="@title"/>
      </xsl:if>
      <xsl:if test="@target">
        <xsl:choose>
          <xsl:when test="@target = '_blank'"><xsl:attribute name="xlink:show" select="'new'"/></xsl:when>
          <xsl:when test="@target = '_self'"><xsl:attribute name="xlink:show" select="'replace'"/></xsl:when>
          <xsl:otherwise>
            <xsl:message>WARNING: Unrecognised link target <xsl:value-of select="@target"/>.</xsl:message>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:if>

      <xsl:choose>
        <xsl:when test="child::node()">
          <xsl:apply-templates mode="content"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@href"/>
        </xsl:otherwise>
      </xsl:choose>
    </db:link>
  </xsl:template>
  <xsl:template mode="content" match="acronyme">
    <!-- Not a good mapping for the title, but no good solution is available in DocBook. -->
    <!-- DocBook prefers to use a glossary for the whole document. -->
    <!-- https://lists.oasis-open.org/archives/docbook-apps/200210/msg00030.html -->
    <!-- TODO: implement glossaries and use entries from there. -->
    <db:acronym xlink:title="{@title}">
      <xsl:apply-templates mode="content"/>
    </db:acronym>
  </xsl:template>
  <xsl:template mode="content" match="signet">
    <db:anchor xml:id="{@id}"/>
    <xsl:apply-templates mode="content"/>
  </xsl:template>
  <xsl:template mode="content" match="renvoi">
    <db:link linkend="{@id}">
      <xsl:apply-templates mode="content"/>
    </db:link>
  </xsl:template>
  <xsl:template mode="content" match="index">
    <db:indexterm>
      <xsl:choose>
        <xsl:when test="@id1">
          <db:primary>
            <xsl:value-of select="@id1"/>
          </db:primary>
          <xsl:if test="@id2">
            <db:secondary>
              <xsl:value-of select="@id2"/>
            </db:secondary>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <db:primary>
            <xsl:value-of select="."/>
          </db:primary>
        </xsl:otherwise>
      </xsl:choose>
    </db:indexterm>
    <xsl:apply-templates mode="content"/>
  </xsl:template>
  <xsl:template mode="content" match="lien-forum">
    <xsl:variable name="link">
      <xsl:choose>
        <xsl:when test="@idpost">
          <xsl:value-of
            select="concat('https://www.developpez.net/forums/showthread.php?t=', @id, '#post', @idpost)"
          />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="concat('https://www.developpez.net/forums/showthread.php?t=', @id)"
          />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <db:link xlink:href="{$link}" role="lien-forum">
      <xsl:attribute name="role" select="
          if (@avecnote and @avecnote = '1') then
            'lien-forum-avec-note'
          else
            'lien-forum'"/>
      <xsl:text>Commentez&#0160;!</xsl:text>
    </db:link>
  </xsl:template>
</xsl:stylesheet>
