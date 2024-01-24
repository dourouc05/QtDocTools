<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xpath-map="http://www.w3.org/2005/xpath-functions/map"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xpath-map xs html db xlink saxon tc" version="3.0">

  <xsl:output method="xml" indent="yes" suppress-indentation="inline link i b u paragraph code"/>
  <xsl:import-schema schema-location="../../../schemas/dvpml/article.xsd"
    use-when="system-property('xsl:is-schema-aware') = 'yes'"/>
    
  <!-- Global sheet parameters without document-specific defaults (e.g., from a configuration file). -->
  <xsl:param name="configuration-file-name" as="xs:string" select="''"/>
  <xsl:param name="document-file-name" as="xs:string" select="''"/>
  <xsl:param name="doc-qt" as="xs:boolean" select="false()"/>
  <xsl:param name="qt-version" select="''"/>
  
  <!-- Load the configuration file if there is one. -->
  <xsl:variable name="document" select="."/>
  <xsl:variable name="json-document-uri">
    <xsl:variable name="xml-uri" as="xs:string" select="base-uri()"/>
    <xsl:variable name="json-uri-base" as="xs:string" select="replace($xml-uri, '.xml', '.json')"/>
    <xsl:variable name="json-uri-suffix" as="xs:string" select="concat($xml-uri, '.json')"/>
    <xsl:choose>
      <xsl:when test="$configuration-file-name and tc:file-exists($configuration-file-name)">
        <xsl:value-of select="$configuration-file-name"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($json-uri-base)">
        <xsl:value-of select="$json-uri-base"/>
      </xsl:when>
      <xsl:when test="tc:file-exists($json-uri-suffix)">
        <xsl:value-of select="$json-uri-suffix"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'{}'"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="json-document" as="map(*)">
    <xsl:choose>
      <xsl:when test="doc-available($json-document-uri)"><xsl:copy-of select="parse-json($json-document-uri)"/></xsl:when>
      <xsl:when test="$doc-qt">
        <xsl:map>
          <xsl:map-entry key="'section'" select="65"/>
          <xsl:map-entry key="'license-number'" select="-1"/>
          <xsl:map-entry key="'license-year'" select="-1"/>
          <xsl:map-entry key="'license-author'" select="''"/>
          <xsl:map-entry key="'license-text'">
            Ce document est disponible sous la licence <link href="https://www.gnu.org/licenses/fdl-1.3.html">GNU Free Documentation License version 1.3</link> telle que publiée par la Free Software Foundation.
          </xsl:map-entry>
          <xsl:map-entry key="'forum-topic'" select="-1"/>
          <xsl:map-entry key="'forum-post'" select="-1"/>
          <xsl:map-entry key="'ftp-user'" select="'qt'"/>
          <xsl:map-entry key="'ftp-folder'" select="concat('doc/', $qt-version, '/', $document-file-name, '/')"/>
          <xsl:map-entry key="'google-analytics'" select="''"/>
          <xsl:map-entry key="'related'" select="''"/>
        </xsl:map>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">ERROR: No JSON file found! Expected path: <xsl:value-of select="$json-document-uri"/></xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>

  <!-- Global sheet parameters with default values from the JSON file. -->
  <!-- TODO: how about moving the JSON reading to Java? XSLT is not the best language to process it. -->
  <xsl:param name="section" as="xs:integer" select="if ($json-document?section) then xs:integer($json-document?section) else 1"/>
  <xsl:param name="license-number" as="xs:integer" select="if ($json-document?license-number) then xs:integer($json-document?license-number) else -1"/>
  <xsl:param name="license-year" as="xs:integer" select="if ($json-document?license-year) then xs:integer($json-document?license-year) else -1"/>
  <xsl:param name="license-author" as="xs:string" select="if ($json-document?license-author) then $json-document?license-author else ''"/>
  <xsl:param name="license-text" select="if ($json-document?license-text) then $json-document?license-text else ''"/>
  <xsl:param name="forum-topic" as="xs:integer" select="if ($json-document?forum-topic) then xs:integer($json-document?forum-topic) else -1"/>
  <xsl:param name="forum-post" as="xs:integer" select="if ($json-document?forum-post) then xs:integer($json-document?forum-post) else -1"/>
  <xsl:param name="ftp-user" as="xs:string" select="if ($json-document?ftp-user) then $json-document?ftp-user else ''"/>
  <xsl:param name="ftp-folder" as="xs:string" select="if ($json-document?ftp-folder) then $json-document?ftp-folder else ''"/>
  <xsl:param name="http-url" as="xs:string" select="concat('https://', $ftp-user, '.developpez.com/', $ftp-folder)"/>
  <xsl:param name="google-analytics" as="xs:string" select="if ($json-document?google-analytics) then $json-document?google-analytics else ''"/>
  <xsl:param name="related" as="xs:string" select="if ($json-document?related) then $json-document?related else ''"/>

  <!-- Import other modules. -->
  <xsl:include href="docbook_to_dvpml_media.xslt"/>
  <xsl:include href="docbook_to_dvpml_block.xslt"/>
  <xsl:include href="docbook_to_dvpml_inline.xslt"/>
  <xsl:include href="docbook_to_dvpml_inlinenoformatting.xslt"/>
  <xsl:include href="docbook_to_dvpml_biblio.xslt"/>
  
  <!-- Type of document, mostly to generate links. -->
  <xsl:variable name="is-article" as="xs:boolean" select="boolean($document[self::db:article])"/>
  <xsl:variable name="is-book" as="xs:boolean" select="boolean($document[self::db:book])"/>
  <xsl:variable name="is-monopart-book" as="xs:boolean" select="$is-book and not($document/*[self::db:part])"/>
  <xsl:variable name="is-multipart-book" as="xs:boolean" select="$is-book and boolean($document/*[self::db:part])"/>

  <xsl:template name="tc:check-valid-document-file-name">
    <xsl:if test="string-length($document-file-name) = 0">
      <xsl:message>ERROR: Missing parameter document-file-name.</xsl:message>
    </xsl:if>
    <xsl:if test="ends-with($document-file-name, '.xml')">
      <xsl:message>WARNING: Parameter document-file-name should not have an extension, as the name
        for other files is determined based on this value.</xsl:message>
    </xsl:if>
  </xsl:template>

  <xsl:template match="db:set">
    <xsl:message terminate="yes">ERROR: book sets are not supported.</xsl:message>
  </xsl:template>

  <xsl:template match="db:article">
    <xsl:if test="$is-article">
      <xsl:message>ERROR: Document must be an article.</xsl:message>
    </xsl:if>
    
    <xsl:call-template name="tc:check-valid-document-file-name"/>
    <xsl:result-document validation="lax" href="{$document-file-name}_dvp.xml">
      <document>
        <xsl:call-template name="tc:document-entete"/>
        <xsl:call-template name="tc:document-see-also"/>
        <xsl:call-template name="tc:document-authors"/>
        <xsl:call-template name="tc:document-license"/>
        <xsl:call-template name="tc:document-related"/>

        <synopsis>
          <xsl:for-each select="tc:document-abstract()">
            <xsl:apply-templates mode="content" select="."/>
          </xsl:for-each>
          
          <xsl:call-template name="tc:document-abstract-obsoleted-by">
            <xsl:with-param name="info" select="db:info"/>
          </xsl:call-template>
          <xsl:call-template name="tc:document-abstract-forum-link"/>
        </synopsis>

        <summary>
          <xsl:variable name="hasTextBeforeSection" as="xs:boolean">
            <xsl:choose>
              <!-- If the element just after <info> is a section -->
              <xsl:when test="child::*[2][self::db:section]">
                <xsl:value-of select="false()"/>
              </xsl:when>
              <!-- First a class synopsis, then a section -->
              <xsl:when test="child::*[2][self::db:classsynopsis] and child::*[3][self::db:section]">
                <xsl:value-of select="false()"/>
              </xsl:when>
              <!-- Other cases? -->
              <xsl:otherwise><xsl:value-of select="true()"/></xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          
          <xsl:choose>
            <xsl:when test="$hasTextBeforeSection">
              <!-- A document must have a section in DvpML, not necessarily in DocBook. -->
              <!-- TODO: the previous comment is wrong. -->
              <section id="{generate-id()}" noNumber="1">
                <title>
                  <xsl:value-of select="
                      if (db:info/db:title) then
                        db:info/db:title
                      else
                        db:title"/>
                </title>

                <xsl:apply-templates mode="content" select="./*[not(self::db:info) and not(self::db:title)]"/>
              </section>
            </xsl:when>
            <xsl:otherwise>
              <xsl:apply-templates mode="content" select="./*[not(self::db:info) and not(self::db:title)]"/>
            </xsl:otherwise>
          </xsl:choose>

          <xsl:if test="db:bibliography">
            <xsl:apply-templates select="db:bibliography"/>
          </xsl:if>
        </summary>
      </document>
    </xsl:result-document>
  </xsl:template>

  <xsl:template match="db:book">
    <xsl:if test="$is-book">
      <xsl:message>ERROR: Document must be a book.</xsl:message>
    </xsl:if>
    
    <xsl:call-template name="tc:check-valid-document-file-name"/>
    <xsl:if test="$doc-qt">
      <xsl:message terminate="yes">Qt documentation is not supposed to contain books.</xsl:message>
    </xsl:if>

    <xsl:choose>
      <!-- When there are parts, generate one article per part (the first chapters, not within any part, will be output in the first file, with the table of contents). -->
      <xsl:when test="db:part">
        <xsl:if test="$is-multipart-book">
          <xsl:message>ERROR: Document must be a book with at least one part.</xsl:message>
        </xsl:if>
        
        <!-- Main document: table of contents and first few chapters (outside parts). -->
        <xsl:result-document validation="lax" href="{$document-file-name}_dvp.xml">
          <xsl:apply-templates mode="book-with-parts" select="."/>
        </xsl:result-document>

        <!-- Iterate over parts, each in its own file. -->
        <xsl:for-each select="db:part">
          <xsl:result-document validation="lax"
            href="{$document-file-name}_dvp_part_{position() + 1}_dvp.xml">
            <xsl:apply-templates mode="part-root" select=".">
              <xsl:with-param name="part-number" select="position()"/>
              <!-- Add the bibliography to the last part. -->
              <xsl:with-param name="bibliography" select="following-sibling::*[self::db:bibliography]"/>
            </xsl:apply-templates>
          </xsl:result-document>
        </xsl:for-each>
      </xsl:when>
      <!-- When there are only chapters, use the default mechanisms. -->
      <xsl:otherwise>
        <xsl:if test="$is-monopart-book">
          <xsl:message>ERROR: Document must be a book without parts.</xsl:message>
        </xsl:if>
        
        <xsl:result-document validation="lax" href="{$document-file-name}_dvp.xml">
          <xsl:apply-templates mode="book-without-parts" select="."/>
        </xsl:result-document>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="db:book" mode="book-without-parts">
    <xsl:call-template name="tc:check-valid-document-file-name"/>
    <document>
      <xsl:call-template name="tc:document-entete"/>
      <xsl:call-template name="tc:document-license"/>
      <xsl:call-template name="tc:document-authors"/>
      <xsl:call-template name="tc:document-related"/>

      <synopsis>        
        <xsl:for-each select="tc:document-abstract()">
          <xsl:apply-templates mode="content" select="."/>
        </xsl:for-each>
        
        <xsl:call-template name="tc:document-abstract-forum-link"/>
      </synopsis>

      <multi-page>
        <xsl:for-each select="db:preface | db:chapter">
          <page id="page_{position()}">
            <title>
              <xsl:apply-templates mode="content_para_no_formatting" select="db:title | db:info/db:title"></xsl:apply-templates>
            </title>
            <link>
              <xsl:attribute name="href">
                <xsl:number value="position()" format="I"/>
              </xsl:attribute>
            </link>
          </page>
        </xsl:for-each>
      </multi-page>

      <summary>
        <xsl:apply-templates mode="content"
          select="./*[self::db:preface or self::db:chapter]"/>

        <xsl:if test="db:bibliography">
          <xsl:apply-templates select="db:bibliography"/>
        </xsl:if>
      </summary>
    </document>
  </xsl:template>

  <xsl:template match="db:book" mode="book-with-parts">
    <document>
      <xsl:call-template name="tc:document-entete">
        <!-- Avoid generating a table of contents, as this page only contains a table of contents for the whole book. -->
        <xsl:with-param name="generate-summary" select="false()"/>
      </xsl:call-template>
      <xsl:call-template name="tc:document-authors"/>
      <xsl:call-template name="tc:document-license"/>
      <xsl:call-template name="tc:document-related"/>

      <synopsis>
        <xsl:for-each select="tc:document-abstract()">
          <xsl:apply-templates mode="content" select="."/>
        </xsl:for-each>
        
        <xsl:call-template name="tc:document-abstract-forum-link"/>
      </synopsis>
      
      <multi-page>
        <page id="page_toc">
          <title>Table des matières</title>
          <link href="TOC"/>
        </page>
        
        <xsl:for-each select="db:preface">
          <page id="page_P{position()}">
            <title>
              <xsl:apply-templates mode="content_para_no_formatting" select="db:title | db:info/db:title"></xsl:apply-templates>
            </title>
            <link href="{generate-id()}"/>
          </page>
        </xsl:for-each>
        
        <xsl:for-each select="db:chapter">
          <page id="page_C{position()}">
            <title>
              <xsl:apply-templates mode="content_para_no_formatting" select="db:title | db:info/db:title"></xsl:apply-templates>
            </title>
            <link>
              <xsl:attribute name="href">
                <xsl:number value="position()" format="I"/>
              </xsl:attribute>
            </link>
          </page>
        </xsl:for-each>
      </multi-page>

      <summary>
        <!-- Generate the table of contents: first, solo chapters; then, parts (as subsections, one subsection per part). -->
        <section id="TOC" noNumber="1">
          <title>Table des matières</title>

          <xsl:if test="db:preface | db:chapter">
            <liste>
              <xsl:for-each select="db:preface">
                <xsl:apply-templates mode="document-toc" select=".">
                  <xsl:with-param name="chapter-index" select="generate-id()"/>
                </xsl:apply-templates>
              </xsl:for-each>
              <xsl:for-each select="db:chapter">
                <xsl:apply-templates mode="document-toc" select=".">
                  <xsl:with-param name="chapter-index" select="position()"/>
                </xsl:apply-templates>
              </xsl:for-each>
            </liste>
          </xsl:if>

          <xsl:for-each select="db:part">
            <xsl:variable name="part-index" as="xs:integer" select="position()"/>

            <section id="TOC.{$part-index}" noNumber="1">
              <title>
                <xsl:apply-templates select="db:title | db:info" mode="content_para_no_formatting"/>
              </title>

              <!-- TODO: generate the URL based on the configuration instead of bullshit. -->
              <paragraph>
                <link href="{tc:generate-url-for-part($part-index)}">
                  <xsl:text>Partie </xsl:text>
                  <xsl:value-of select="$part-index"/>
                  <xsl:text>. </xsl:text>
                  <xsl:apply-templates select="db:title | db:info" mode="content_para_no_formatting"/>
                </link>
              </paragraph>

              <liste>
                <xsl:for-each select="db:chapter">
                  <xsl:variable name="chapter-index" as="xs:integer" select="position()"/>
                  <xsl:apply-templates mode="document-toc" select=".">
                    <xsl:with-param name="part-index" select="$part-index"/>
                    <xsl:with-param name="chapter-index" select="$chapter-index"/>
                  </xsl:apply-templates>
                </xsl:for-each>
              </liste>
            </section>
          </xsl:for-each>

          <xsl:if test="db:bibliography">
            <section id="TOC.BIB" noNumber="1">
              <title>Bibliographie</title>
              <!-- TODO: generate a true URL. -->
              <paragraph>
                <link href="{tc:generate-url-for-part(count($document//db:part))}#bibliography">
                  <xsl:text>Bibliographie</xsl:text>
                </link>
              </paragraph>
            </section>
          </xsl:if>
        </section>

        <!-- Generate the chapters outside parts on this first page. -->
        <xsl:for-each select="db:preface">
          <section id="{generate-id()}" noNumber="1">
            <!-- Do manually the title and the first few paragraphs. They -->
            <!-- would otherwise be considered as synopsis (the title must be -->
            <!-- put before the paragraphs, hence the special treatment). -->
            <xsl:apply-templates mode="content" select="db:title | db:info"/>
            <xsl:apply-templates mode="content"
              select="./*[not(self::db:title) and not(self::db:info)]"/>
          </section>
        </xsl:for-each>
        
        <xsl:for-each select="db:chapter">
          <xsl:variable name="sectionIndex" as="xs:string">
            <xsl:number format="I"/>
          </xsl:variable>
          <section id="{$sectionIndex}">
            <!-- Do manually the title and the first few paragraphs. They -->
            <!-- would otherwise be considered as synopsis (the title must be -->
            <!-- put before the paragraphs, hence the special treatment). -->
            <xsl:apply-templates mode="content" select="db:title | db:info"/>
            <xsl:apply-templates mode="content"
              select="./*[not(self::db:title) and not(self::db:info)]"/>
          </section>
        </xsl:for-each>
        
        <!-- If bibliography, put it on its own page, after the various parts. -->
      </summary>
    </document>
  </xsl:template>

  <xsl:template match="db:part" mode="part-root">
    <xsl:param name="bibliography" as="element(db:bibliography)?"/>
    <xsl:param name="part-number" as="xs:integer"/>
    
    <document>
      <xsl:call-template name="tc:document-entete">
        <xsl:with-param name="part-number" select="$part-number"/>
      </xsl:call-template>
      <xsl:call-template name="tc:document-authors"/>
      <xsl:call-template name="tc:document-license"/>
      <xsl:call-template name="tc:document-related"/>

      <synopsis>
        <!-- Synopses for parts are more specific, because they do not use the standard -->
        <!-- abstract tag in info. -->
        <xsl:variable name="abstractParagraphs" as="node()*">
          <xsl:choose>
            <xsl:when test="db:partintro">
              <xsl:copy-of select="db:partintro"/>
            </xsl:when>
            <xsl:when test="db:info/following-sibling::*[1][self::db:para]">
              <!-- Something resembling an abstract (paragraphs before the first section). -->
              <xsl:variable name="tentative" select="db:info/following-sibling::*[not(preceding-sibling::db:section) and not(self::db:section)]"/>
              <xsl:copy-of select="
                  if (count($tentative) &lt; count(db:info/following-sibling::*)) then
                    $tentative
                  else
                    $tentative[1]"/>
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
        
        <xsl:call-template name="tc:document-abstract-forum-link"/>
      </synopsis>
      
      <soustitre>
        <xsl:value-of select="db:title"/>
      </soustitre>
      
      <multi-page>
        <xsl:for-each select="db:preface | db:chapter">
          <page id="page_{position()}">
            <title>
              <xsl:apply-templates mode="content_para_no_formatting" select="db:title | db:info/db:title"></xsl:apply-templates>
            </title>
            <link>
              <xsl:attribute name="href">
                <xsl:number value="position()" format="I"/>
              </xsl:attribute>
            </link>
          </page>
        </xsl:for-each>
        
        <xsl:if test="$bibliography">
          <page id="page_toc">
            <title>Bibliographie</title>
            <link href="BIBLIOGRAPHY"/>
          </page>
        </xsl:if>
      </multi-page>

      <summary>
        <xsl:apply-templates mode="content" select="./*[not(self::db:title)]"/>
        
        <xsl:if test="$bibliography">
          <xsl:apply-templates select="$bibliography"/>
        </xsl:if>
      </summary>
    </document>
  </xsl:template>
  
  <xsl:template match="db:preface" mode="document-toc">
    <!-- No numbering for preface. -->
    <element useText="0">
      <paragraph>
        <link href="{$http-url}#L{generate-id()}">
          <xsl:apply-templates mode="content_para_no_formatting" select="db:title | db:info/db:title"/>
        </link>
      </paragraph>
      
      <xsl:if test="db:section">
        <liste>
          <xsl:for-each select="db:section">
            <xsl:apply-templates select="." mode="document-toc">
              <xsl:with-param name="section-index" select="generate-id()"/>
            </xsl:apply-templates>
          </xsl:for-each>
        </liste>
      </xsl:if>
    </element>
  </xsl:template>

  <xsl:template
    match="db:chapter | db:section | db:sect1 | db:sect2 | db:sect3 | db:sect4 | db:sect5 | db:sect6"
    mode="document-toc">
    <!-- Parent indices are valid XML indices, not necessarily integers. -->
    <!-- The major case is integers, but sections with no number displayed -->
    <!-- may have funky indices (like "d9e21"). -->
    <xsl:param name="part-index"/>
    <xsl:param name="section-index"/>
    
    <xsl:variable name="sectionId">
      <xsl:number level="multiple" format="I.1.1"/>
    </xsl:variable>
    
    <xsl:variable name="base-url">
      <xsl:choose>
        <xsl:when test="$part-index">
          <xsl:value-of select="tc:generate-url-for-part($part-index)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$http-url"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <element useText="0">
      <paragraph>
        <link href="{$base-url}#L{$sectionId}">
          <xsl:value-of select="$sectionId"/>
          <xsl:text>. </xsl:text>
          <xsl:apply-templates mode="content_para_no_formatting" select="db:title | db:info/db:title"/>
        </link>
      </paragraph>

      <xsl:if test="db:section">
        <liste>
          <xsl:for-each select="db:section">
            <xsl:apply-templates select="." mode="document-toc"/>
          </xsl:for-each>
        </liste>
      </xsl:if>
    </element>
  </xsl:template>

  <xsl:function name="tc:format-date">
    <xsl:param name="date" as="node()?"/>
    <xsl:param name="tag-name" as="xs:string"/>

    <xsl:choose>
      <xsl:when test="$date">
        <xsl:value-of select="format-date($date, '[Y0001]-[M01]-[D01]')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: no <xsl:value-of select="$tag-name"/> found in info, the field will be
          set to today.</xsl:message>
        <xsl:value-of select="format-date(current-date(), '[Y0001]-[M01]-[D01]')"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:template name="tc:document-entete">
    <xsl:param name="generate-summary" as="xs:boolean" select="true()"/>
    <xsl:param name="part-number" as="xs:integer?"/>
    
    <xsl:if
      test="string-length($license-author) > 0 or $license-number > 0 or $license-year > 0">
      <xsl:message>WARNING: Global license parameters not consistent: either the three
        parameters license-author, license-number, and license-year must be set, or only
        license-text.</xsl:message>
    </xsl:if>

    <entete>
      <rubrique>
        <xsl:value-of select="$section"/>
      </rubrique>
      <meta>
        <description>
          <xsl:value-of select="tc:document-description()"/>
        </description>
        <keywords>
          <xsl:value-of select="tc:document-keywords()"/>
        </keywords>
      </meta>

      <titre>
        <page>
          <xsl:value-of select="tc:document-titleabbrev-or-title()"/>
        </page>
        <article>
          <xsl:value-of select="tc:document-title()"/>
        </article>
      </titre>
      <date>
        <xsl:value-of select="tc:format-date(db:info/db:pubdate, 'pubdate')"/>
      </date>
      <miseajour>
        <xsl:value-of select="tc:format-date(db:info/db:date, 'date')"/>
      </miseajour>

      <xsl:if test="$doc-qt">
        <includebas>include($_SERVER['DOCUMENT_ROOT'] . '/doc/pied.php');
          include($_SERVER['DOCUMENT_ROOT'] . '/template/pied.php');</includebas>
      </xsl:if>

      <xsl:if test="string-length($google-analytics) > 0">
        <google-analytics>
          <xsl:value-of select="$google-analytics"/>
        </google-analytics>
      </xsl:if>

      <xsl:if test="string-length($license-author) > 0 and $license-number > 0 and $license-year > 0">
        <licauteur>
          <xsl:value-of select="$license-author"/>
        </licauteur>
        <lictype>
          <xsl:value-of select="$license-number"/>
        </lictype>
        <licannee>
          <xsl:value-of select="$license-year"/>
        </licannee>
        <!-- When $license-text is set, the license is output just after the <entete> tag. -->
      </xsl:if>

      <xsl:choose>
        <xsl:when test="string-length($ftp-user) > 0 and string-length($ftp-folder) > 0">
          <xsl:variable name="url-suffix" as="xs:string">
            <xsl:choose>
              <xsl:when test="not($doc-qt) and $part-number">
                <xsl:value-of select="tc:generate-url-suffix-for-part($part-number)"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="''"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          
          <serveur>
            <xsl:value-of select="$ftp-user"/>
          </serveur>
          <chemin>
            <xsl:value-of select="$ftp-folder"/>
            <xsl:if test="$url-suffix">
              <xsl:text>/</xsl:text>
              <xsl:value-of select="$url-suffix"/>
            </xsl:if>
          </chemin>
          <urlhttp>
            <xsl:value-of select="$http-url"/>
            <xsl:if test="$url-suffix">
              <xsl:text>/</xsl:text>
              <xsl:value-of select="$url-suffix"/>
            </xsl:if>
          </urlhttp>
        </xsl:when>
        <xsl:otherwise>
          <xsl:message>WARNING: FTP information missing.</xsl:message>
        </xsl:otherwise>
      </xsl:choose>

      <xsl:if test="$generate-summary">
        <nosummary/>
        <nosummarypage/>
      </xsl:if>

      <nopdf/>
      <nozip/>
      <nodownload/>
      <noebook/>
    </entete>
  </xsl:template>

  <xsl:template name="tc:document-authors">
    <authorDescriptions>
      <xsl:choose>
        <xsl:when test="$document//db:info/(db:authorgroup | db:author | db:editor | db:othercredit)">
          <xsl:for-each select="$document//db:info//(db:author | db:editor | db:othercredit)">
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
  </xsl:template>

  <xsl:template name="tc:document-license">
    <xsl:if
      test="string-length($license-author) = 0 and $license-number &lt; 0 and $license-year &lt; 0">
      <xsl:choose>
        <!-- When license-author and the others are set, the license information is within the <entete> tag. -->
        <!-- It may either be a string or a sequence (for instance, text with a link). -->
        <xsl:when test="not(not($license-text))">
          <licence>
            <xsl:copy-of select="$license-text"/>
          </licence>
        </xsl:when>
        <xsl:otherwise>
          <xsl:message>WARNING: Global license parameters not consistent: either the three
            parameters license-author, license-number, and license-year must be set, or only
            license-text.</xsl:message>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <xsl:template name="tc:document-see-also">
    <!-- If the synopsis has a specific form (last paragraph has only one children, a simple list), -->
    <!-- consider this list has links to linked documents. -->
    <xsl:if
      test="$doc-qt and $document//db:info/db:abstract/db:para[last()]/child::*[1][self::db:simplelist]">
      <voiraussi>
        <!-- First, the linked documents (previous/next). -->
        <xsl:for-each select="$document//db:info/db:abstract/db:para[last()]/db:simplelist/db:member">
          <lien>
            <texte>
              <xsl:choose>
                <!-- Rewrite the names when there are chevrons indicating the previous (<) or next page (>), or the upper level (^). -->
                <xsl:when test="ends-with(db:link/text(), ' &gt;')"><xsl:value-of select="substring(db:link/text(), 0, string-length(db:link/text()) - 1)"/></xsl:when>
                <xsl:when test="starts-with(db:link/text(), '&lt; ')"><xsl:value-of select="substring(db:link/text(), 3, string-length(db:link/text()))"/></xsl:when>
                <xsl:when test="starts-with(db:link/text(), '^ ') and ends-with(db:link/text(), ' ^')"><xsl:value-of select="substring(db:link/text(), 3, string-length(db:link/text()) - 4)"/></xsl:when>
                <!-- Base case: don't do anything. -->
                <xsl:otherwise>
                  <xsl:value-of select="db:link/text()"/>
                </xsl:otherwise>
              </xsl:choose>
            </texte>
            <url>
              <xsl:variable name="filename"
                select="substring-before(string(db:link/@xlink:href), '.html')"/>
              <xsl:value-of
                select="concat('http://qt.developpez.com/doc/', lower-case(//db:info/db:productname), '/', //db:info/db:productnumber, '/', $filename)"
              />
            </url>
          </lien>
        </xsl:for-each>
        <!-- Then, anything else? -->
      </voiraussi>
    </xsl:if>
  </xsl:template>

  <xsl:template name="tc:document-related">
    <xsl:if test="string-length($related) > 0">
      <reference>
        <xsl:value-of select="$related"/>
      </reference>
    </xsl:if>
  </xsl:template>

  <xsl:function name="tc:has-document-abstract-obsoleted-by" as="xs:boolean">
    <xsl:param name="info" as="element(db:info)"/>
    <xsl:sequence
      select="count($info/db:bibliorelation[@class = 'uri' and @type = 'isreplacedby']) &gt; 0"/>
  </xsl:function>

  <xsl:template name="tc:document-abstract-obsoleted-by">
    <xsl:param name="info" as="element(db:info)"/>

    <!-- Deprecated/obsolete articles with replacement -->
    <xsl:if test="$info/db:bibliorelation[@class = 'uri' and @type = 'isreplacedby']">
      <rich-imgtext type="error">
        <paragraph> Cet article est obsolète et n'est gardé que pour des raisons historiques, <link
            href="{$info/db:bibliorelation[@class='uri' and @type='isreplacedby']}">car une version
            plus à jour est disponible</link>. </paragraph>
      </rich-imgtext>
    </xsl:if>
  </xsl:template>

  <xsl:function name="tc:has-document-abstract-forum-link" as="xs:boolean">
    <xsl:sequence select="$forum-topic > 0"/>
  </xsl:function>

  <xsl:template name="tc:document-abstract-forum-link">
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
        <xsl:message>WARNING: a forum post ID is present, but not a forum topic ID.</xsl:message>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="db:author | db:editor | db:othercredit" mode="header_author">
    <xsl:variable name="role" as="xs:string">
      <xsl:choose>
        <xsl:when test="self::db:author">auteur</xsl:when>
        <xsl:when test="self::db:othercredit and @class = 'technicaleditor'"
          >relecteur-technique</xsl:when>
        <xsl:when test="self::db:othercredit and @class = 'translator'">traducteur</xsl:when>
        <xsl:when test="self::db:othercredit and @class = 'conversion'">gabarisateur</xsl:when>
        <xsl:when test="self::db:othercredit and @class = 'proofreader'">correcteur</xsl:when>
        <xsl:otherwise>
          <xsl:message>WARNING: Unrecognised contribution with tag <xsl:value-of select="name(.)"
            /></xsl:message>
          <xsl:value-of select="'relecteur-technique'"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <xsl:variable name="key">
      <xsl:choose>
        <xsl:when test="db:personname/db:othername[@role = 'pseudonym']">
          <xsl:value-of select="db:personname/db:othername[@role = 'pseudonym']"/>
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
            <xsl:value-of select="concat(db:personname/db:firstname, ' ', db:personname/db:surname)"
            />
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="db:personname/db:othername[@role = 'pseudonym']"/>
          </xsl:otherwise>
        </xsl:choose>
      </fullname>
      <xsl:if
        test="db:uri[not(@type = 'main-uri' or @type = 'blog-uri' or @type = 'google-plus' or @type = 'linkedin')]">
        <xsl:variable name="uri"
          select="db:uri[not(@type = 'main-uri' or @type = 'blog-uri' or @type = 'google-plus' or @type = 'linkedin')]"/>
        <homepage>
          <title>
            <xsl:value-of select="$uri/@type"/>
          </title>
          <url>
            <xsl:value-of select="$uri/text()"/>
          </url>
        </homepage>
      </xsl:if>
      <xsl:if test="db:uri[@type = 'main-uri']">
        <url>
          <xsl:value-of select="db:uri[@type = 'main-uri']"/>
        </url>
      </xsl:if>
      <xsl:if test="db:uri[@type = 'blog-uri']">
        <blog>
          <xsl:value-of select="db:uri[@type = 'blog-uri']"/>
        </blog>
      </xsl:if>
      <xsl:if test="db:uri[@type = 'google-plus']">
        <google-plus>
          <xsl:value-of select="db:uri[@type = 'google-plus']"/>
        </google-plus>
      </xsl:if>
      <xsl:if test="db:uri[@type = 'linkedin']">
        <linkedin>
          <xsl:value-of select="db:uri[@type = 'linkedin']"/>
        </linkedin>
      </xsl:if>
    </authorDescription>
  </xsl:template>

  <xsl:function name="tc:document-title" as="xs:string">
    <xsl:variable name="title" as="xs:string?">
      <xsl:variable name="raw" as="xs:string*">
        <xsl:apply-templates mode="content_para_no_formatting" select="$document//db:title[1]"/>
      </xsl:variable>
      <xsl:value-of select="$raw[string-length(.) &gt; 0][1]"/>
    </xsl:variable>
    <xsl:value-of select="normalize-space($title)"/>
  </xsl:function>

  <xsl:function name="tc:document-subtitle" as="xs:string">
    <xsl:variable name="title" as="xs:string?">
      <xsl:variable name="raw" as="xs:string*">
        <xsl:apply-templates mode="content_para_no_formatting" select="$document//db:title[1]/ancestor::node()[1]/db:subtitle"/>
      </xsl:variable>
      <xsl:value-of select="$raw[string-length(.) &gt; 0][1]"/>
    </xsl:variable>
    <xsl:value-of select="normalize-space($title)"/>
  </xsl:function>

  <xsl:function name="tc:document-titleabbrev" as="xs:string">
    <xsl:variable name="title" as="xs:string?">
      <xsl:variable name="raw" as="xs:string*">
        <xsl:apply-templates mode="content_para_no_formatting" select="$document//db:title[1]/ancestor::node()[1]/db:titleabbrev"/>
      </xsl:variable>
      <xsl:value-of select="$raw[string-length(.) &gt; 0][1]"/>
    </xsl:variable>
    <xsl:value-of select="normalize-space($title)"/>
  </xsl:function>

  <xsl:function name="tc:document-titleabbrev-or-title" as="xs:string">
    <xsl:variable name="titleAbbrev" select="tc:document-titleabbrev()"/>
    <xsl:value-of select="
        if ($titleAbbrev) then
          $titleAbbrev
        else
          tc:document-title()"/>
  </xsl:function>

  <xsl:function name="tc:document-description" as="xs:string">
    <xsl:choose>
      <!-- If available, use the version of the abstract that is tailored for the description. Otherwise, use the standard abstract. -->
      <xsl:when test="$document//db:info/db:abstract[@role = 'description']">
        <xsl:apply-templates mode="content_para_no_formatting" select="$document//db:info/db:abstract[not(@role = 'description')]"></xsl:apply-templates>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="tc:document-title()"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:function name="tc:document-keywords" as="xs:string">
    <xsl:choose>
      <xsl:when test="$document//db:info/db:keywordset">
        <xsl:for-each select="$document//db:info/db:keywordset/db:keyword">
          <xsl:value-of select="."/>
          <xsl:if test="position() &lt; last()">
            <xsl:value-of select="','"/>
          </xsl:if>
        </xsl:for-each>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="translate(translate(tc:document-title(), ',', ''), ' ', ',')"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:function name="tc:document-abstract" as="node()*">
    <xsl:choose>
      <xsl:when test="not($doc-qt) and $document//db:info/db:abstract">
        <!-- Most normal case for generic DocBook documents. -->
        <xsl:copy-of select="$document//db:info/db:abstract/*"/>
      </xsl:when>
      <xsl:when
        test="$doc-qt and ($document//db:info/db:abstract/node()[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()[1]) > 0])">
        <!-- Most normal case for Qt documentation. -->
        <!-- Don't count when there is no abstract content (i.e. empty -->
        <!-- paragraphs are ignored). -->
        
        <!-- For Qt documentation, there is an (unfortunately typical) case -->
        <!-- where the abstract only contains a see-also list of links -->
        <!-- (<simplelist>). It should be handled in a specific way, because -->
        <!-- there are more specific tags for this (<voiraussi>). -->
        <!-- This condition checks whether the abstract has paragraphs with -->
        <!-- something else than links to linked documents! -->
        <!-- (Linked documents are only available for Qt documentation.) -->
        <xsl:if test="not($document//self::db:article)">
          <xsl:message>WARNING: entering a code path that is supposed to be specific for Qt
            documentation, but the document type is not article.</xsl:message>
        </xsl:if>
        <xsl:copy-of
          select="$document//db:info/db:abstract/node()[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and text()]"
        />
      </xsl:when>
      <xsl:when test="$document//db:info/following-sibling::*[1][self::db:para]">
        <!-- Something resembling an abstract at the beginning of the text -->
        <!-- (paragraphs before the first section). -->
        <xsl:variable name="tentative"
          select="$document//db:info/following-sibling::*[not(preceding-sibling::db:section) and not(self::db:section) and not(preceding-sibling::db:sect1) and not(self::db:sect1)]"/>
        <xsl:copy-of select="
            if (count($tentative) &lt; count($document//db:info/following-sibling::*)) then
              $tentative
            else
              $tentative[1]"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- Nothing to do, sorry about that... -->
        <db:para/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:function name="tc:file-exists">
    <xsl:param name="filename" as="xs:string"/>
    <!-- doc-available is limited to XML, unlike unparsed-text-available. -->
    <xsl:value-of select="unparsed-text-available($filename) != false()"/>
    <!-- Only in Saxon EE: -->
    <!-- xmlns:xpath-file="http://expath.org/ns/file" -->
    <!-- xpath-file:exists(filename) -->
  </xsl:function>

  <xsl:function name="tc:generate-url-suffix-for-part" as="xs:string">
    <xsl:param name="part-number" as="xs:integer"></xsl:param>
    
    <xsl:variable name="part" as="element(db:part)" select="$document//db:part[$part-number]"/>
    <xsl:variable name="part-title" as="xs:string">
      <xsl:apply-templates mode="content_para_no_formatting" select="$part/db:title | $part/db:info/db:title"/>
    </xsl:variable>
    
    <xsl:value-of select="concat($part-number, '-', translate(lower-case($part-title), ' ', '-'))"/>
  </xsl:function>
  
  <xsl:function name="tc:generate-url-for-part" as="xs:string">
    <xsl:param name="part-number" as="xs:integer"/>
    <xsl:value-of select="concat($http-url, '/', tc:generate-url-suffix-for-part($part-number))"/>
  </xsl:function>

  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="book-with-parts" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
    /> in mode "book-with-parts".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="book-without-parts" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
    /> in mode "book-without-parts".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="content" priority="-1">
    <xsl:choose>
      <xsl:when test="db:sect1 | db:sect2 | db:sect3 | db:sect4 | db:sect5 | db:sect6">
        <xsl:message>WARNING: Only section tags are supported, not numbered ones like <xsl:value-of
          select="name(.)"/>. You can replace automatically instances of <xsl:value-of
            select="name(.)"/> by section.</xsl:message>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
          /> in mode "content".</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="*" mode="content_bibliography" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> in mode "content_bibliography".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="content_bibliography_author" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> in mode "content_bibliography_author".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="content_bibliography_title" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> in mode "content_bibliography_title".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="content_para" priority="-1">
    <xsl:choose>
      <xsl:when test="db:guilabel | db:accel | db:prompt | db:keysym">
        <xsl:message>WARNING: Tag <xsl:value-of select="name(.)"/> has no matching construct in the
          target format. Content is not lost, but is not marked either.</xsl:message>
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
          /> in mode "content_para".</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="*" mode="content_para_no_formatting" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> in mode "content_para_no_formatting".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="document-toc" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
    /> in mode "document-toc".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="header_author" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> in mode "header_author".</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="part-root" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
     /> in mode "part-root".</xsl:message>
  </xsl:template>
  <xsl:template match="*" priority="-1">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> without mode.</xsl:message>
  </xsl:template>
  <xsl:template match="*" mode="#all" priority="-10">
    <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)"
      /> in unknown mode.</xsl:message>
  </xsl:template>
</xsl:stylesheet>
