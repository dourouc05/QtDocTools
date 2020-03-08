<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  
  <xsl:output method="xml" indent="yes" suppress-indentation="inline link i b paragraph code"/>
  <xsl:import-schema schema-location="../../../schemas/dvpml/article.xsd" use-when="system-property('xsl:is-schema-aware')='yes'"/>
  
  <xsl:param name="doc-qt" as="xs:boolean" select="false()"/>
  <xsl:param name="section" as="xs:integer" select="1"/>
  <xsl:param name="license-number" as="xs:integer" select="-1"/>
  <xsl:param name="license-year" as="xs:integer" select="-1"/>
  <xsl:param name="license-author" as="xs:string" select="''"/>
  <xsl:param name="license-text" as="xs:string" select="''"/>
  <xsl:param name="forum-topic" as="xs:integer" select="-1"/>
  <xsl:param name="forum-post" as="xs:integer" select="-1"/>
  <xsl:param name="ftp-user" as="xs:string" select="''"/>
  <xsl:param name="ftp-folder" as="xs:string" select="''"/>
  <xsl:param name="google-analytics" as="xs:string" select="''"/>
  <xsl:param name="related" as="xs:string" select="''"/>
  
  <xsl:template match="db:article">
    <xsl:result-document validation="lax">
      <document>
        <entete>
          <rubrique><xsl:value-of select="$section"/></rubrique>
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
            <keywords>
              <xsl:choose>
                <xsl:when test="db:info/db:keywordset">
                  <xsl:for-each select="db:info/db:keywordset/db:keyword">
                    <xsl:value-of select="."/>
                    <xsl:if test="position() &lt; last()">
                      <xsl:value-of select="','"/>
                    </xsl:if>
                  </xsl:for-each>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="translate(translate(db:info/db:title, ',', ''), ' ', ',')"/>
                </xsl:otherwise>
              </xsl:choose>
            </keywords>
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
            <xsl:choose>
              <xsl:when test="db:info/db:pubdate">
                <xsl:value-of select="format-date(db:info/db:pubdate, '[Y0001]-[M01]-[D01]')"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:message>WARNING: no pubdate found in info, the field date will be set to today.</xsl:message>
                <xsl:value-of select="format-date(current-date(), '[Y0001]-[M01]-[D01]')"/>
              </xsl:otherwise>
            </xsl:choose>
          </date>
          <miseajour>
            <xsl:choose>
              <xsl:when test="db:info/db:date">
                <xsl:value-of select="format-date(db:info/db:date, '[Y0001]-[M01]-[D01]')"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:message>WARNING: no date found in info, the field miseajour will be set to today.</xsl:message>
                <xsl:value-of select="format-date(current-date(), '[Y0001]-[M01]-[D01]')"/>
              </xsl:otherwise>
            </xsl:choose>
          </miseajour>
          
          <xsl:if test="$doc-qt">
            <includebas>include($_SERVER['DOCUMENT_ROOT'] . '/doc/pied.php'); include($_SERVER['DOCUMENT_ROOT'] . '/template/pied.php');</includebas>
          </xsl:if>
          
          <xsl:if test="string-length($google-analytics) > 0">
            <google-analytics><xsl:value-of select="$google-analytics"/></google-analytics>
          </xsl:if>
          
          <xsl:choose>
            <xsl:when test="string-length($license-author) > 0 and $license-number > 0 and $license-year > 0">
              <licauteur><xsl:value-of select="$license-author"/></licauteur>
              <lictype><xsl:value-of select="$license-number"/></lictype>
              <licannee><xsl:value-of select="$license-year"/></licannee>
            </xsl:when>
            <xsl:when test="string-length($license-author) > 0 or $license-number > 0 or $license-year > 0">
              <xsl:message>WARNING: Global license parameters not consistent: either the three parameters license-author, license-number, and license-year must be set, or only license-text.</xsl:message>
            </xsl:when>
          </xsl:choose>
          
          <xsl:choose>
            <xsl:when test="$doc-qt">
              <serveur>Qt</serveur>
              <xsl:variable name="url">
                <xsl:variable name="documentQdt" select="tokenize(base-uri(), '/')[last()]"/>
                <xsl:variable name="document" select="tokenize($documentQdt, '\.')[1]"/>
                <xsl:value-of select="concat(lower-case(db:info/db:productname), '/', db:info/db:productnumber, '/', $document)"/>
              </xsl:variable>
              <chemin>/doc/<xsl:value-of select="$url"/></chemin>
              <urlhttp>http://qt.developpez.com/doc/<xsl:value-of select="$url"/></urlhttp>
            </xsl:when>
            <xsl:when test="string-length($ftp-user) > 0 and string-length($ftp-folder) > 0">
              <serveur><xsl:value-of select="$ftp-user"/></serveur>
              <chemin><xsl:value-of select="$ftp-folder"/></chemin>
              <urlhttp>http://<xsl:value-of select="$ftp-user"/>.developpez.com/<xsl:value-of select="$ftp-folder"/></urlhttp>
            </xsl:when>
            <xsl:otherwise>
              <xsl:message>WARNING: FTP information missing.</xsl:message>
            </xsl:otherwise>
          </xsl:choose>
        </entete>
        
        <xsl:if test="string-length($license-author) = 0 and $license-number &lt; 0 and $license-year &lt; 0">
          <xsl:choose>
            <xsl:when test="string-length($license-text) > 0">
              <licence>
                <xsl:value-of select="$license-text"/>
              </licence>
            </xsl:when>
            <xsl:otherwise>
              <xsl:message>WARNING: Global license parameters not consistent: either the three parameters license-author, license-number, and license-year must be set, or only license-text.</xsl:message>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:if>
        
        <!-- If the synopsis has a specific form (last paragraph has only one children, a simple list), -->
        <!-- consider this list has links to linked documents. -->
        <xsl:if test="db:info/db:abstract/db:para[last()]/child::*[1][self::db:simplelist and @role='see-also']">
          <voiraussi>
            <!-- First, the linked documents (previous/next). -->
            <xsl:for-each select="db:info/db:abstract/db:para[last()]/db:simplelist/db:member">
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
            <xsl:when test="db:info/(db:authorgroup | db:author | db:editor | db:othercredit)">
              <xsl:for-each select="db:info//(db:author | db:editor | db:othercredit)">
                <xsl:apply-templates mode="header_author" select="."/>
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
        
        <xsl:if test="string-length($related) > 0">
          <reference><xsl:value-of select="$related"/></reference>
        </xsl:if>
        
        <synopsis>
          <xsl:variable name="abstractParagraphs" as="node()*">
            <xsl:choose>
              <xsl:when test="db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()[1]) > 0]">
                <!-- The abstract has paragraphs with something else than links to linked documents, great! -->
                <!-- Most normal case. -->
                
                <!-- Do some checks to ensure that no text is ignored (paragraphs outside sections). -->
                <xsl:if test="db:info/following-sibling::*[1][self::db:para]">
                  <xsl:message>WARNING: Paragraphs outside sections not being converted!</xsl:message>
                </xsl:if>
                
                <!-- <db:simplelist> is already eaten for <voiraussi>. -->
                <xsl:copy-of select="db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and text()]"/>
              </xsl:when>
              <xsl:when test="db:info/following-sibling::*[1][self::db:para]">
                <!-- Just links in the DocBook abstract, but something resembling an abstract -->
                <!-- (paragraphs before the first section). -->
                <xsl:variable name="tentative" select="db:info/following-sibling::*[not(preceding-sibling::db:section) and not(self::db:section)]"/>
                <xsl:copy-of select="if (count($tentative) &lt; count(db:info/following-sibling::*)) then $tentative else $tentative[1]"/>
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
          
          <!-- Deprecated/obsolete articles with replacement -->
          <xsl:if test="db:info/db:bibliorelation[@class='uri' and @type='isreplacedby']">
            <rich-imgtext type="error">
              <paragraph>
                Cet article est obsolète et n'est gardé que pour des raisons historiques, 
                <link href="{db:info/db:bibliorelation[@class='uri' and @type='isreplacedby']}">car une version 
                  plus à jour est disponible</link>. 
              </paragraph>
            </rich-imgtext>
          </xsl:if>
          
          <!-- Link to the forum. -->
          <xsl:choose>
            <xsl:when test="$forum-topic > 0">
              <paragraph>
                <lien-forum avecnote="1" id="{$forum-topic}">
                  <xsl:if test="$forum-post > 0">
                    <xsl:attribute name="idpost" select="$forum-post"/>
                  </xsl:if>
                </lien-forum>
              </paragraph>
            </xsl:when>
            <xsl:when test="$forum-post > 0">
              <xsl:message>WARNING: a forum post is present, but not a forum topic.</xsl:message>
            </xsl:when>
          </xsl:choose>
        </synopsis>
        
        <summary>
          <xsl:choose>
            <xsl:when test="not(child::*[2][self::db:section])">
              <!-- A document must have a section in DvpML, not necessarily in DocBook. -->
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
  
  <xsl:template mode="header_author" match="db:author | db:editor | db:othercredit">
    <xsl:variable name="role" as="xs:string">
      <xsl:choose>
        <xsl:when test="self::db:author">auteur</xsl:when>
        <xsl:when test="self::db:othercredit and @class='technicaleditor'">relecteur-technique</xsl:when>
        <xsl:when test="self::db:othercredit and @class='translator'">traducteur</xsl:when>
        <xsl:when test="self::db:othercredit and @class='conversion'">gabarisateur</xsl:when>
        <xsl:when test="self::db:othercredit and @class='proofreader'">correcteur</xsl:when>
        <xsl:otherwise>
          <xsl:message>WARNING: Unrecognised contribution with tag <xsl:value-of select="name(.)" /></xsl:message>
          <xsl:value-of select="'relecteur-technique'"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="key">
      <xsl:choose>
        <xsl:when test="db:personname/db:othername[@role='pseudonym']">
          <xsl:value-of select="db:personname/db:othername[@role='pseudonym']"/>
        </xsl:when>
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
          <xsl:value-of select="generate-id()"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    
    <authorDescription name="{$key}" role="{$role}">
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
      <xsl:if test="db:uri[not(@type='main-uri' or @type='blog-uri' or @type='google-plus' or @type='linkedin')]">
        <xsl:variable name="uri" select="db:uri[not(@type='main-uri' or @type='blog-uri' or @type='google-plus' or @type='linkedin')]"/>
        <homepage>
          <title><xsl:value-of select="$uri/@type"/></title>
          <url><xsl:value-of select="$uri/text()"/></url>
        </homepage>
      </xsl:if>
      <xsl:if test="db:uri[@type='main-uri']">
        <url><xsl:value-of select="db:uri[@type='main-uri']"/></url>
      </xsl:if>
      <xsl:if test="db:uri[@type='blog-uri']">
        <blog><xsl:value-of select="db:uri[@type='blog-uri']"/></blog>
      </xsl:if>
      <xsl:if test="db:uri[@type='google-plus']">
        <google-plus><xsl:value-of select="db:uri[@type='google-plus']"/></google-plus>
      </xsl:if>
      <xsl:if test="db:uri[@type='linkedin']">
        <linkedin><xsl:value-of select="db:uri[@type='linkedin']"/></linkedin>
      </xsl:if>
    </authorDescription>
  </xsl:template>
  
  <!-- Block elements. -->
  <xsl:template mode="content" match="db:info">
    <!-- Everything is done in <db:article>. -->
  </xsl:template>
  
  <xsl:template mode="content" match="db:section">
    <xsl:variable name="sectionId">
      <xsl:number level="multiple" format="1"/>
    </xsl:variable>
    
    <section id="{$sectionId}">
      <xsl:choose>
        <xsl:when test="@xml:id">
          <!-- First the title, then some raw HTML. -->
          <title><xsl:value-of select="node()[db:title][1]"/></title>
          <html-brut>
            <xsl:value-of select="concat('&lt;![CDATA[&lt;a name=&#34;', @xml:id, '&#34;]]>')"/>
          </html-brut>
          <xsl:apply-templates mode="content" select="node()[not(db:title)]"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates mode="content" select="node()"/>
        </xsl:otherwise>
      </xsl:choose>
      
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
  
  <xsl:template mode="content" match="db:table">
    <tableau width="80%" border="1" legende="{if (title) then title else info/title}">
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
        <xsl:when test="db:para | db:note | db:itemizedlist | db:orderedlist | db:mediaobject | db:programlisting | db:screen">
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
  
  <xsl:template mode="content" match="db:figure | db:informalfigure">
    <xsl:if test="@xml:id">
      <signet id="{@xml:id}"/>
    </xsl:if>
    
    <image>
      <xsl:attribute name="src">
        <xsl:value-of select="./db:mediaobject/db:imageobject/db:imagedata/@fileref"/>
      </xsl:attribute>
      
      <!-- A figure must have a title, unlike an informalfigure. -->
      <xsl:if test="./db:title">
        <xsl:attribute name="legende">
          <xsl:value-of select="./db:title"/>
        </xsl:attribute>
      </xsl:if>
    </image>
  </xsl:template>
  
  <xsl:template mode="content" match="db:constructorsynopsis | db:destructorsynopsis | db:enumsynopsis | db:typedefsynopsis | db:fieldsynopsis | db:methodsynopsis | db:classsynopsis | db:fieldsynopsis | db:namespacesynopsis"/>
  
  <xsl:template mode="content" match="db:para">
    <!-- The synopsis is done in <db:article>. -->
    <xsl:if test="..[self::db:section] or ..[self::db:listitem] or ..[self::db:blockquote] or ..[self::db:th] or ..[self::db:td] or ..[self::db:footnote] or ..[self::db:note] or ..[self::db:article] or string-length(name(preceding-sibling::*[1])) = 0">
      <xsl:choose>
        <xsl:when test="db:informaltable | db:note | db:programlisting | db:screen">
          <!-- Some content must be moved outside the paragraph (DocBook's model is really flexible). -->
          <xsl:variable name="children" select="child::node()" as="node()*"/>
          <xsl:variable name="firstTagOutsideParagraph" as="xs:integer">
            <xsl:variable name="isNotPara" select="for $i in 1 to count($children) return boolean($children[$i][self::db:informaltable | self::db:note | self::db:programlisting | self::db:screen]) or name($children[$i]) = 'db:informaltable' or name($children[$i]) = 'db:note' or name($children[$i]) = 'db:programlisting' or name($children[$i]) = 'db:screen'"/>
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
    <!-- Dirty hack, I know. -->
    <tableau width="95%" border="3">
      <ligne>
        <colonne useText="0">
          <xsl:apply-templates mode="content"/>
        </colonne>
      </ligne>
    </tableau>
  </xsl:template>
  
  <xsl:template mode="content" match="db:programlisting[@role='raw-html']">
    <html-brut>
      <xsl:apply-templates mode="content_para"/>
    </html-brut>
  </xsl:template>
  
  <xsl:template mode="content" match="db:programlisting[not(@role='raw-html')] | db:screen">
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
  
  <xsl:template mode="content" match="db:tip">
    <rich-imgtext type="idea">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:warning">
    <rich-imgtext type="warning">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:caution">
    <rich-imgtext type="error">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:important">
    <xsl:message>WARNING: Tag <xsl:value-of select="name(.)" /> has no matching construct in the target format. Content is output as a caution.</xsl:message>
    <rich-imgtext type="error">
      <xsl:apply-templates mode="content"/>
    </rich-imgtext>
  </xsl:template>
  
  <xsl:template mode="content" match="db:mediaobject | db:inlinemediaobject">
    <xsl:choose>
      <!-- First child is an image? You've got an image! -->
      <xsl:when test="child::node()[1][self::db:imageobject]">
        <image src="{db:imageobject[1]/db:imagedata/@fileref}">
          <xsl:if test="db:textobject">
            <xsl:attribute name="alt" select="normalize-space(db:textobject)"/>
          </xsl:if>
          
          <xsl:if test="db:caption">
            <xsl:attribute name="legende" select="normalize-space(db:caption)"/>
          </xsl:if>
        </image>
      </xsl:when>
      <!-- First child is a video? You've got a video! If there is an image, it is alternate content. -->
      <xsl:when test="child::node()[1][self::db:videoobject]">
        <animation>
          <xsl:if test="@role">
            <xsl:attribute name="type" select="@role"/>
          </xsl:if>
          
          <xsl:if test="@width">
            <width><xsl:value-of select="@width"/></width>
          </xsl:if>
          
          <xsl:if test="@height">
            <height><xsl:value-of select="@height"/></height>
          </xsl:if>
          
          <xsl:for-each select="multimediaparam">
            <xsl:element name="{@name}">
              <xsl:value-of select="@value"/>
            </xsl:element>
          </xsl:for-each>
        </animation>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template mode="content_para content" match="db:equation | db:inlineequation">
    <latex id="{@xml:id}"><xsl:value-of select="mathphrase[@role='latex']"/></latex>
  </xsl:template>
  
  <xsl:template match="*[preceding-sibling::*[1][self::db:mediaobject]]" mode="content" priority="-1">
  </xsl:template>
  
  <!-- Within a paragraph. -->
  <xsl:template mode="content_para" match="db:emphasis">
    <xsl:choose>
      <xsl:when test="not(parent::node()[self::db:code])">
        <!-- Nesting tags this way is not allowed (just text within <inline>).  -->
        <xsl:choose>
          <xsl:when test="@role='bold' or @role='strong'">
            <b><xsl:apply-templates mode="content_para"/></b>
          </xsl:when>
          <xsl:when test="@role='underline'">
            <u><xsl:apply-templates mode="content_para"/></u>
          </xsl:when>
          <xsl:when test="@role='strike'">
            <s><xsl:apply-templates mode="content_para"/></s>
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
  
  <xsl:template mode="content_para" match="db:phrase[starts-with(@role, 'color:')]">
    <font color="{substring(@role, 7)}">
      <xsl:apply-templates mode="content_para"/>
    </font>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:subscript">
    <sub>
      <xsl:apply-templates mode="content_para"/>
    </sub>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:link[not(starts-with(@role, 'lien-forum') or @linkend)]">
    <xsl:variable name="translatedLink" as="xs:string">
      <xsl:choose>
        <xsl:when test="ends-with(string(@xlink:href), '.webxml')">
          <xsl:variable name="filename" select="substring-before(string(@xlink:href), '.webxml')"/>
          <xsl:value-of select="concat('https://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"/>
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
  
  <xsl:template mode="content_para" match="db:link[starts-with(@role, 'lien-forum')]">
    <xsl:variable name="idpost" select="if (contains(@xlink:href, '#post')) then tokenize(@xlink:href, '#post')[2] else ''"/>
    <xsl:variable name="id" select="tokenize(replace(@xlink:href, '#post.*', ''), '?t=')[2]"/>
    
    <lien-forum id="{$id}">
      <xsl:if test="@idpost != ''">
        <xsl:attribute name="idpost" select="$idpost"/>
      </xsl:if>
      
      <xsl:if test="@role != 'lien-forum'">
        <!-- Should be 'lien-forum-avec-note' -->
        <xsl:attribute name="avecnote" select="'1'"></xsl:attribute>
      </xsl:if>
    </lien-forum>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:link[@linkend]">
    <renvoi id="{@linkend}">
      <xsl:apply-templates mode="content_para"/>
    </renvoi>
  </xsl:template>    
  
  <xsl:template mode="content_para" match="db:footnote">
    <noteBasPage>
      <xsl:for-each select="db:para">
        <xsl:apply-templates mode="content_para"/>
        
        <xsl:if test="position() &lt; last()">
          <br/>
        </xsl:if>
      </xsl:for-each>
    </noteBasPage>
  </xsl:template>
  
  <xsl:template mode="content_para" match="db:indexterm">
    <xsl:choose>
      <xsl:when test="db:primary and not(db:secondary)">
        <index><xsl:value-of select="@db:primary"/></index>
      </xsl:when>
      <xsl:otherwise>
        <index id1="{db:primary}" id2="{db:secondary}"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="#all">
    <xsl:choose>
      <xsl:when test="self::db:guilabel | self::db:accel | self::db:prompt | self::db:keysym">
        <xsl:message>WARNING: Tag <xsl:value-of select="name(.)" /> has no matching construct in the target format. Content is not lost, but is not marked either.</xsl:message>
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" /></xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>