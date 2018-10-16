<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://dourouc05.github.io"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  
  <!-- TODO: <db:codelisting role="raw-html">, like qtquickcontrols2-universal.qdt -->
  
  <xsl:output method="xml" indent="yes" suppress-indentation="inline link i b paragraph code"/>
  <xsl:import-schema schema-location="article.xsd" use-when="system-property('xsl:is-schema-aware')='yes'"/>   
  
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
              <xsl:value-of select="db:info/db:title"/>
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
          
          <includebas>include($_SERVER['DOCUMENT_ROOT'] . '/doc/pied.php'); include($_SERVER['DOCUMENT_ROOT'] . '/template/pied.php');</includebas>
          
          <serveur>Qt</serveur>
          <xsl:variable name="url">
            <xsl:variable name="documentQdt" select="tokenize(base-uri(), '/')[last()]"/>
            <xsl:variable name="document" select="tokenize($documentQdt, '\.')[1]"/>
            <xsl:value-of select="concat(lower-case(db:info/db:productname), '/', db:info/db:productnumber, '/', $document)"/>
          </xsl:variable>
          <chemin>/doc/<xsl:value-of select="$url"/></chemin>
          <urlhttp>http://qt.developpez.com/doc/<xsl:value-of select="$url"/></urlhttp>
        </entete>
        
        <!-- If the synopsis has a specific form (last paragraph has only one children, a simple list), -->
        <!-- consider this list has links to linked documents. -->
        <xsl:if test="db:info/db:abstract/db:para[last()]/child::*[1][self::db:simplelist]">
          <voiraussi>
            <!-- First, the linked documents (previous/next). -->
            <xsl:for-each select="db:info/db:abstract/db:para[2]/db:simplelist/db:member">
              <lien>
                <texte><xsl:value-of select="db:link/text()"/></texte>
                <url>
                  <xsl:variable name="filename" select="substring-before(string(db:link/@xlink:href), '.html')"/>
                  <xsl:value-of select="concat('http://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"/>
                </url>
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
          <xsl:variable name="abstractParagraphs" as="node()*">
            <xsl:choose>
              <xsl:when test="db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()) > 0]">
                <!-- The abstract has paragraphs with something else than links to linked documents, great! -->
                <!-- Do some checks to ensure that no text is ignored (paragraphs outside sections). -->
                <xsl:if test="db:info/following-sibling::*[1][self::db:para]">
                  <xsl:message>WARNING: Paragraphs outside sections not being converted!</xsl:message>
                </xsl:if>
                
                <xsl:copy-of select="db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()) > 0]"/>
              </xsl:when>
              <xsl:when test="db:info/following-sibling::*[1][self::db:para]">
                <!-- Just links in the DocBook abstract, but something resembling an abstract -->
                <!-- (paragraphs before the first section). -->
                <xsl:copy-of select="db:info/following-sibling::*[not(preceding-sibling::db:section) and not(self::db:section)]"/>
              </xsl:when>
              <xsl:otherwise>
                <!-- Nothing to do, sorry about that... -->
                <db:para/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          <xsl:for-each select="$abstractParagraphs">
            <xsl:apply-templates mode="content" select="."/>
          </xsl:for-each>
        </synopsis>
        
        <summary>
          <xsl:choose>
            <xsl:when test="not(child::*[1][self::section])">
              <!-- A document must have a section. -->
              <section id="I" noNumber="1">
                <title><xsl:value-of select="db:info/db:title"/></title>
                
                <xsl:apply-templates mode="content" select="./*"/>
              </section>
            </xsl:when>
            <xsl:otherwise>
              <xsl:apply-templates mode="content" select="./*"/>
            </xsl:otherwise>
          </xsl:choose>
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
      <xsl:if test="@xml:id">
        <html-brut>
          <xsl:value-of select="'&lt;![CDATA['"/>
          <a name="{@xml:id}"/>
          <xsl:value-of select="']]>'"/>
        </html-brut>
      </xsl:if>
      
      <xsl:apply-templates mode="content"/>
    </section>
  </xsl:template>
  
  <xsl:template mode="content" match="db:title">
    <title><xsl:value-of select="."/></title>
  </xsl:template>
  
  <xsl:template mode="content" match="db:informaltable">
    <tableau width="80%" border="1" sautDePagePdf="0">
      <xsl:apply-templates mode="content"/>
    </tableau>
  </xsl:template>
  
  <xsl:template mode="content" match="db:formaltable">
    <tableau width="80%" border="1" sautDePagePdf="0" legende="{if (title) then title else info/title}">
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
      <xsl:choose>
        <xsl:when test="db:para | db:note | db:itemizedlist | db:orderedlist | db:mediaobject | db:programlisting">
          <xsl:apply-templates mode="content"/>
        </xsl:when>
        <xsl:otherwise>
          <paragraph>
            <xsl:apply-templates mode="content_para"/>
          </paragraph>
        </xsl:otherwise>
      </xsl:choose>
    </colonne>
  </xsl:template>
  
  <xsl:template mode="content" match="db:constructorsynopsis | db:destructorsynopsis | db:enumsynopsis | db:typedefsynopsis | db:fieldsynopsis | db:methodsynopsis | db:classsynopsis | db:fieldsynopsis | db:namespacesynopsis"/>
  
  <xsl:template mode="content" match="db:para">
    <xsl:if test="..[self::db:section] or ..[self::db:listitem] or ..[self::db:blockquote] or ..[self::db:th] or ..[self::db:td] or ..[self::db:footnote] or ..[self::db:note] or string-length(name(preceding-sibling::*[1])) = 0">
      <xsl:choose>
        <xsl:when test="db:informaltable | db:note | db:programlisting">
          <!-- Some content must be moved outside the paragraph (DocBook's model is really flexible). -->
          <xsl:variable name="children" select="child::node()" as="node()*"/>
          <xsl:variable name="firstTagOutsideParagraph" as="xs:integer">
            <xsl:variable name="isNotPara" select="for $i in 1 to count($children) return boolean($children[$i][self::db:informaltable | self::db:note | self::db:programlisting]) or name($children[$i]) = 'db:informaltable' or name($children[$i]) = 'db:note' or name($children[$i]) = 'db:programlisting'"/>
            <xsl:value-of select="index-of($isNotPara, true())"/>
          </xsl:variable>
          
          <paragraph>
            <xsl:apply-templates mode="content_para" select="node()[position() &lt; $firstTagOutsideParagraph]"/>
          </paragraph>
          <xsl:apply-templates mode="content" select="node()[position() >= $firstTagOutsideParagraph]"/>
        </xsl:when>
        <xsl:otherwise>
          <!-- A normal paragraph. -->
          <paragraph>
            <xsl:apply-templates mode="content_para"/>
          </paragraph>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  
  <xsl:template mode="content" match="db:blockquote">
    <tableau width="95%" border="3">
      <ligne>
        <colonne useText="0">
          <xsl:apply-templates mode="content"/>
        </colonne>
      </ligne>
    </tableau>
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
  
  <xsl:template mode="content content_para" match="db:simplelist">
    <xsl:for-each select="db:member">
      <xsl:variable name="test">
        <xsl:apply-templates mode="content_para"/>
      </xsl:variable>
      <xsl:for-each select="$test/child::node()">
        <xsl:choose>
          <xsl:when test=". instance of text()">
            <xsl:value-of select="normalize-space(.)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:copy-of select="."/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
      <xsl:if test="position() != last()">
        <xsl:text>, </xsl:text>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  
  <xsl:template mode="content" match="db:listitem">
    <element useText="0">
      <xsl:apply-templates mode="content"/>
    </element>
  </xsl:template>
  
  <xsl:template mode="content" match="db:variablelist">
    <liste>
      <xsl:apply-templates mode="content"/>
    </liste>
  </xsl:template>
  
  <xsl:template mode="content" match="db:varlistentry">
    <element useText="0">
      <b>
        <xsl:apply-templates mode="content_para" select="term"/>
      </b>
      <xsl:text>&#0160;: </xsl:text>
      <xsl:apply-templates mode="content_para" select="listitem/para"/>
    </element>
  </xsl:template>
  
  <xsl:template mode="content" match="db:note">
    <rich-imgtext type="info">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:mediaobject">
    <image src="{db:imageobject[1]/db:imagedata/@fileref}"/>
  </xsl:template>
  
  <xsl:template match="*[preceding-sibling::*[1][self::db:mediaobject]]" mode="content" priority="-1">
  </xsl:template>
  
  <!-- Within a paragraph. -->
  <xsl:template mode="content_para" match="db:emphasis">
    <xsl:choose>
      <xsl:when test="not(parent::node()[self::db:code])">
        <!-- Nesting tags this way is not allowed (just text within <inline>).  -->
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
  
  <xsl:template mode="content_para" match="db:subscript">
    <sub>
      <xsl:apply-templates mode="content_para"/>
    </sub>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:link">
    <xsl:variable name="translatedLink" as="xs:string">
      <xsl:choose>
        <xsl:when test="ends-with(string(@xlink:href), '.webxml')">
          <xsl:variable name="filename" select="substring-before(string(@xlink:href), '.webxml')"/>
          <xsl:value-of select="concat('http://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"/>
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
  
  <xsl:template mode="content_para" match="db:inlinemediaobject">
    <image src="{imageobject[1]/imagedata/@fileref}"/>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:footnote">
    <noteBasPage>
      <xsl:apply-templates mode="content"/>
    </noteBasPage>
  </xsl:template>
  
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="#all">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" /></xsl:message>
  </xsl:template>
</xsl:stylesheet>