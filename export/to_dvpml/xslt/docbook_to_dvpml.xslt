<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/" xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  
  <xsl:include href="docbook_to_dvpml_block.xslt"/>
  <xsl:include href="docbook_to_dvpml_inline.xslt"/>
  
  <xsl:output method="xml" indent="yes" suppress-indentation="inline link i b paragraph code"/>
  <xsl:import-schema schema-location="../../../schemas/dvpml/article.xsd" use-when="system-property('xsl:is-schema-aware')='yes'"/>
  <!-- Global sheet parameters, mostly used to fill the header. -->
  <xsl:param name="document-file-name" as="xs:string" select="''"/>
  <xsl:param name="configuration-file-name" as="xs:string" select="''"/>
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
  
  <xsl:variable name="biblioRefs" as="map(xs:string, xs:decimal)">
    <!-- Create a global map between bibliographic IDs and the numbers they are assigned to. These numbers are increasing in order of appearance in the text. This is not configurable. -->
    <xsl:variable name="uniqueRefs" select="distinct-values(//db:biblioref/@endterm)"/>
    
    <xsl:map>
      <xsl:for-each select="$uniqueRefs">
        <xsl:map-entry key="xs:string(.)" select="xs:decimal(position())"/>
      </xsl:for-each>
    </xsl:map>
  </xsl:variable>
  
  <xsl:variable name="document" select="."/>
  
  <xsl:template name="tc:check-valid-document-file-name">
    <xsl:if test="string-length($document-file-name) = 0">
      <xsl:message>ERROR: Missing parameter document-file-name.</xsl:message>
    </xsl:if>
    <xsl:if test="ends-with($document-file-name, '.xml')">
      <xsl:message>WARNING: Parameter document-file-name should not have an extension, as the name for other files is determined based on this value.</xsl:message>
    </xsl:if>
  </xsl:template>
  
  <xsl:template match="db:set">
    <xsl:message terminate="yes">ERROR: book sets are not supported.</xsl:message>
  </xsl:template>
  
  <xsl:template match="db:article">
    <xsl:call-template name="tc:check-valid-document-file-name"/>
    <xsl:result-document validation="lax" href="{$document-file-name}_dvp.xml">
      <document>
        <entete>
          <rubrique><xsl:value-of select="$section"/></rubrique>
          <meta>
            <description>
              <xsl:choose>
                <xsl:when test="db:info/db:abstract/db:para">
                  <xsl:value-of select="db:info/db:abstract/db:para[1]/text()"/>
                </xsl:when>
                <xsl:when test="db:info/db:title">
                  <xsl:value-of select="db:info/db:title"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="db:title"/>
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
                <xsl:when test="db:info/db:title">
                  <xsl:value-of select="translate(translate(db:info/db:title, ',', ''), ' ', ',')"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="translate(translate(db:title, ',', ''), ' ', ',')"/>
                </xsl:otherwise>
              </xsl:choose>
            </keywords>
          </meta>
          
          <titre>
            <page><xsl:value-of select="db:info/db:title"/></page>
            <article><xsl:value-of select="db:info/db:title"/></article>
          </titre>
          <date><xsl:value-of select="tc:format-date(db:info/pubdate, 'pubdate')"/></date>
          <miseajour><xsl:value-of select="tc:format-date(db:info/date, 'date')"/></miseajour>
          
          <xsl:call-template name="tc:document-entete-from-parameters"/>
        </entete>
        
        <xsl:call-template name="tc:document-license-from-parameters"/>
        <xsl:call-template name="tc:document-see-also">
          <xsl:with-param name="info" select="db:info"/>
        </xsl:call-template>
        <xsl:call-template name="tc:document-authors">
          <xsl:with-param name="info" select="db:info"/>
        </xsl:call-template>
        <xsl:call-template name="tc:document-related-from-parameters"/>
        
        <synopsis>
          <xsl:variable name="abstractParagraphs" as="node()*">
            <xsl:choose>
              <xsl:when test="not($doc-qt) or (db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()[1]) > 0])">
                <!-- The abstract has paragraphs with something else than links to linked documents, great! -->
                <!-- (Linked documents are only available for Qt documentation.) -->
                <!-- Most normal case. -->
                
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
          
          <xsl:call-template name="tc:document-abstract-obsoleted-by">
            <xsl:with-param name="info" select="db:info"/>
          </xsl:call-template>
          <xsl:call-template name="tc:document-abstract-forum-link-from-parameters"/>
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
          
          <xsl:if test="db:bibliography">
            <xsl:apply-templates select="db:bibliography"/>
          </xsl:if>
        </summary>
      </document>
    </xsl:result-document>
  </xsl:template>
  
  <xsl:template match="db:book">
    <xsl:call-template name="tc:check-valid-document-file-name"/>
    <xsl:if test="$doc-qt">
      <xsl:message terminate="yes">Qt documentation is not supposed to contain books.</xsl:message>
    </xsl:if>
    
    <xsl:choose>
      <!-- When there are parts, generate one article per part (the first chapters, not within any part, will be output in the first file, with the table of contents). -->
      <xsl:when test="db:part">
        <!-- Main document: table of contents and first few chapters (outside parts). -->
        <xsl:result-document validation="lax" href="{$document-file-name}_dvp.xml">
          <xsl:apply-templates mode="book-with-parts" select="."/>
        </xsl:result-document>
        
        <!-- Iterate over parts, each in its own file. -->
        <xsl:for-each select="db:part">
          <xsl:result-document validation="lax" href="{$document-file-name}_part_{position() + 1}_dvp.xml">
            <xsl:apply-templates mode="part_root" select="."/>
          </xsl:result-document>
        </xsl:for-each>
      </xsl:when>
      <!-- When there are only chapters, use the default mechanisms. -->
      <xsl:otherwise>
        <xsl:result-document validation="lax" href="{$document-file-name}_dvp.xml">
          <xsl:apply-templates mode="book-without-parts" select="."/>
        </xsl:result-document>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template match="db:book" mode="book-without-parts">
    <xsl:call-template name="tc:check-valid-document-file-name"/>
    <document>
      <entete>
        <rubrique><xsl:value-of select="$section"/></rubrique>
        <meta>
          <description>
            <xsl:choose>
              <xsl:when test="db:info/db:abstract/db:para">
                <xsl:value-of select="db:info/db:abstract/db:para[1]/text()"/>
              </xsl:when>
              <xsl:when test="db:info/db:title">
                <xsl:value-of select="db:info/db:title"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="db:title"/>
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
              <xsl:when test="db:info/db:title">
                <xsl:value-of select="translate(translate(db:info/db:title, ',', ''), ' ', ',')"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="translate(translate(db:title, ',', ''), ' ', ',')"/>
              </xsl:otherwise>
            </xsl:choose>
          </keywords>
        </meta>
        
        <titre>
          <page><xsl:value-of select="db:info/db:title"/></page>
          <article><xsl:value-of select="db:info/db:title"/></article>
        </titre>
        <date><xsl:value-of select="tc:format-date(db:info/pubdate, 'pubdate')"/></date>
        <miseajour><xsl:value-of select="tc:format-date(db:info/date, 'date')"/></miseajour>
        
        <xsl:call-template name="tc:document-entete-from-parameters"/>
      </entete>
      
      <xsl:call-template name="tc:document-license-from-parameters"/>
      <xsl:call-template name="tc:document-see-also">
        <xsl:with-param name="info" select="db:info"/>
      </xsl:call-template>
      <xsl:call-template name="tc:document-authors">
        <xsl:with-param name="info" select="db:info"/>
      </xsl:call-template>
      <xsl:call-template name="tc:document-related-from-parameters"/>
      
      <synopsis>
        <xsl:variable name="abstractParagraphs" as="node()+">
          <xsl:choose>
            <xsl:when test="db:info/db:abstract and db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()[1]) > 0]">
              <!-- The abstract has paragraphs with something else than links to linked documents, great! -->
              <!-- (Linked documents are only available for Qt documentation.) -->
              <!-- Most normal case. -->
              
              <!-- <db:simplelist> is already eaten for <voiraussi>. -->
              <xsl:copy-of select="db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and text()]"/>
            </xsl:when>
            <xsl:when test="db:info/following-sibling::*[1][self::db:para]">
              <!-- Just links in the DocBook abstract, but something resembling an abstract -->
              <!-- (paragraphs before the first section). -->
              <xsl:variable name="tentative" select="db:info/following-sibling::*[not(preceding-sibling::db:section) and not(self::db:section)]"/>
              <xsl:copy-of select="if (count($tentative) &lt; count(db:info/following-sibling::*)) then $tentative else $tentative[1]"/>
            </xsl:when>
            <xsl:when test="db:preface">
              <xsl:copy-of select="db:preface/node()[not(self::db:title)]"/>
            </xsl:when>
            <xsl:otherwise>
              <!-- Nothing to do, sorry about that... -->
              <db:para> </db:para>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        
        <xsl:for-each select="$abstractParagraphs">
          <xsl:apply-templates mode="content" select="."/>
        </xsl:for-each>
        
        <xsl:call-template name="tc:document-abstract-forum-link-from-parameters"/>
      </synopsis>
      
      <multi-page>
        <xsl:for-each select="db:chapter">
          <page id="page_{position()}">
            <title>
              <xsl:choose>
                <xsl:when test="db:info/db:title">
                  <xsl:value-of select="db:info/db:title"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="db:title"/>
                </xsl:otherwise>
              </xsl:choose>
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
        <xsl:for-each select="db:chapter">
          <section>
            <xsl:attribute name="id">
              <xsl:number value="position()" format="I"/>
            </xsl:attribute>
            
            <title><xsl:value-of select="if (db:info/db:title) then db:info/db:title else db:title"/></title>
            <xsl:apply-templates mode="content" select="."/><!-- child::node()[position() &gt; ] -->
          </section>
        </xsl:for-each>
        
        <xsl:if test="db:bibliography">
          <xsl:apply-templates select="db:bibliography"/>
        </xsl:if>
      </summary>
    </document>
  </xsl:template>
  
  <xsl:template match="db:book" mode="book-with-parts">
    <document>
      <entete>
        <rubrique><xsl:value-of select="$section"/></rubrique>
        <meta>
          <description>
            <xsl:choose>
              <xsl:when test="db:info/db:abstract/db:para">
                <xsl:value-of select="db:info/db:abstract/db:para[1]/text()"/>
              </xsl:when>
              <xsl:when test="db:info/db:title">
                <xsl:value-of select="db:info/db:title"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="db:title"/>
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
              <xsl:when test="db:info/db:title">
                <xsl:value-of select="translate(translate(db:info/db:title, ',', ''), ' ', ',')"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="translate(translate(db:title, ',', ''), ' ', ',')"/>
              </xsl:otherwise>
            </xsl:choose>
          </keywords>
        </meta>
        
        <titre>
          <page><xsl:value-of select="db:info/db:title"/></page>
          <article><xsl:value-of select="db:info/db:title"/></article>
        </titre>
        <date><xsl:value-of select="tc:format-date(db:info/pubdate, 'pubdate')"/></date>
        <miseajour><xsl:value-of select="tc:format-date(db:info/date, 'date')"/></miseajour>
        
        <xsl:call-template name="tc:document-entete-from-parameters">
          <!-- Avoid generating a table of contents, as this page only contains a table of contents for the whole book. -->
          <xsl:with-param name="generate-summary" select="false()"/>
        </xsl:call-template>
      </entete>
      
      <xsl:call-template name="tc:document-license-from-parameters"/>
      <xsl:call-template name="tc:document-authors">
        <xsl:with-param name="info" select="db:info"/>
      </xsl:call-template>
      <xsl:call-template name="tc:document-related-from-parameters"/>
      
      <xsl:if test="count(db:info/db:abstract/child::node()) &gt; 0 or tc:has-document-abstract-obsoleted-by(db:info) or tc:has-document-abstract-forum-link-from-parameters()">
        <synopsis>
          <!-- voiraussi is not implemented (book is not used for Qt's documentation). -->
          <!-- This simplifies a lot this code. -->
          <xsl:for-each select="db:info/db:abstract/child::node()">
            <xsl:apply-templates mode="content" select="."/>
          </xsl:for-each>
            
          <xsl:call-template name="tc:document-abstract-obsoleted-by">
            <xsl:with-param name="info" select="db:info"/>
          </xsl:call-template>
          <xsl:call-template name="tc:document-abstract-forum-link-from-parameters"/>
        </synopsis>
      </xsl:if>
      
      <summary>
        <!-- Generate the table of contents: first, solo chapters; then, parts (as subsections, one subsection per part). -->
        <section id="I" noNumber="1">
          <title>Table des matières</title>
          
          <xsl:if test="./db:chapter">
            <liste>
              <xsl:for-each select="db:chapter">
                <xsl:apply-templates mode="document-toc" select="."/>
              </xsl:for-each>
            </liste>
          </xsl:if>
          
          <xsl:for-each select="db:part">
            <section id="{position()}">
              <!-- TODO: see TODO for document-toc -->
              <title><xsl:value-of select="(db:title | db:info/db:title)/text()"/></title>
              
              <!-- TODO: generate the URL based on the configuration instead of bullshit. -->
              <paragraph>
                <link href="http://bullshit#{position()}">
                  <xsl:value-of select="position()"/>
                  <xsl:text>. </xsl:text>
                  <!-- TODO: see TODO for document-toc -->
                  <xsl:value-of select="(db:title | db:info/db:title)/text()"/>
                </link>
              </paragraph>
              
              <xsl:for-each select="db:chapter">
                <xsl:apply-templates mode="document-toc" select="."/>
              </xsl:for-each>
            </section>
          </xsl:for-each>
          
          <xsl:if test="db:bibliography">
            <!-- TODO: generate a true URL. -->
            <link href="http://bullshit#bibliography">
              <xsl:text>Bibliography</xsl:text>
            </link>
          </xsl:if>
        </section>
      </summary>
    </document>
  </xsl:template>
  
  <xsl:template match="db:chapter | db:section" mode="document-toc">
    <!-- TODO: does not work with sect1/sect6. -->
    <xsl:variable name="sectionId">
      <xsl:number level="multiple" format="1"/>
    </xsl:variable>
    
    <!-- TODO: generate the URL based on the configuration. -->
    <element useText="0">
      <paragraph>
        <link href="http://bullshit#{$sectionId}">
          <xsl:value-of select="$sectionId"/>
          <xsl:text>. </xsl:text>
          <!-- TODO: text() allows to only select the true title when there are indexterm, but not for formatting. Formatting should be kept intact, while some tags like indexterm should be eliminated -->
          <xsl:value-of select="(db:title | db:info/db:title)/text()"/>
        </link>
      </paragraph>
    
      <xsl:if test="./db:section">
        <liste>
          <xsl:for-each select="./db:section">
            <xsl:apply-templates select="." mode="document-toc"/>
          </xsl:for-each>
        </liste>
      </xsl:if>
    </element>
  </xsl:template>
  
  <xsl:template match="db:chapter" mode="chapter-root">
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
                <xsl:when test="db:info/db:title">
                  <xsl:value-of select="db:info/db:title"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="db:title"/>
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
                <xsl:when test="db:info/db:title">
                  <xsl:value-of select="translate(translate(db:info/db:title, ',', ''), ' ', ',')"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="translate(translate(db:title, ',', ''), ' ', ',')"/>
                </xsl:otherwise>
              </xsl:choose>
            </keywords>
          </meta>
          
          <titre>
            <page><xsl:value-of select="db:info/db:title"/></page>
            <article><xsl:value-of select="db:info/db:title"/></article>
          </titre>
          <date><xsl:value-of select="tc:format-date(db:info/pubdate, 'pubdate')"/></date>
          <miseajour><xsl:value-of select="tc:format-date(db:info/date, 'date')"/></miseajour>
          
          <xsl:call-template name="tc:document-entete-from-parameters"/>
        </entete>
        
        <xsl:call-template name="tc:document-license-from-parameters"/>
        <xsl:call-template name="tc:document-see-also">
          <xsl:with-param name="info" select="db:info"/>
        </xsl:call-template>
        <xsl:call-template name="tc:document-authors">
          <xsl:with-param name="info" select="db:info"/>
        </xsl:call-template>
        <xsl:call-template name="tc:document-related-from-parameters"/>
        
        <synopsis>
          <xsl:variable name="abstractParagraphs" as="node()*">
            <xsl:choose>
              <xsl:when test="(db:info/db:abstract/db:para[not(child::*[1][self::db:simplelist] and count(child::*) = 1) and string-length(text()[1]) > 0])">
                <!-- The abstract has paragraphs with something else than links to linked documents, great! -->
                <!-- Most normal case. -->
                
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
          
          <xsl:call-template name="tc:document-abstract-obsoleted-by">
            <xsl:with-param name="info" select="db:info"/>
          </xsl:call-template>
          <xsl:call-template name="tc:document-abstract-forum-link-from-parameters"/>
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
    
  <xsl:function name="tc:format-date">
    <xsl:param name="date" as="node()?"/>
    <xsl:param name="tag-name" as="xs:string"/>
    
    <xsl:choose>
      <xsl:when test="$date">
        <xsl:value-of select="format-date($date, '[Y0001]-[M01]-[D01]')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: no <xsl:value-of select="$tag-name"/> found in info, the field will be set to today.</xsl:message>
        <xsl:value-of select="format-date(current-date(), '[Y0001]-[M01]-[D01]')"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:template name="tc:document-entete-from-parameters">
    <xsl:param name="generate-summary" as="xs:boolean" select="true()"/>
    
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
        <!-- TODO: generate all this in the configuration file? -->
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
    
    <xsl:if test="$generate-summary">
      <nosummary/>
      <nosummarypage/>
    </xsl:if>
    
    <nopdf/>
    <nozip/>
    <nodownload/>
    <noebook/>
  </xsl:template>
  
  <xsl:template name="tc:document-authors">
    <xsl:param name="info" as="element(db:info)"/>
    
    <authorDescriptions>
      <xsl:choose>
        <xsl:when test="$info/(db:authorgroup | db:author | db:editor | db:othercredit)">
          <xsl:for-each select="$info//(db:author | db:editor | db:othercredit)">
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
  
  <xsl:template name="tc:document-license-from-parameters">
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
  </xsl:template>
  
  <xsl:template name="tc:document-see-also">
    <xsl:param name="info" as="element(db:info)"/>
    
    <!-- If the synopsis has a specific form (last paragraph has only one children, a simple list), -->
    <!-- consider this list has links to linked documents. -->
    <xsl:if test="$doc-qt and $info/db:abstract/db:para[last()]/child::*[1][self::db:simplelist and @role='see-also']">
      <voiraussi>
        <!-- First, the linked documents (previous/next). -->
        <xsl:for-each select="$info/db:abstract/db:para[last()]/db:simplelist/db:member">
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
  </xsl:template>
  
  <xsl:template name="tc:document-related-from-parameters">
    <xsl:if test="string-length($related) > 0">
      <reference><xsl:value-of select="$related"/></reference>
    </xsl:if>
  </xsl:template>
  
  <xsl:function name="tc:has-document-abstract-obsoleted-by" as="xs:boolean">
    <xsl:param name="info" as="element(db:info)"/>
    <xsl:sequence select="count($info/db:bibliorelation[@class='uri' and @type='isreplacedby']) &gt; 0"/>
  </xsl:function>
  
  <xsl:template name="tc:document-abstract-obsoleted-by">
    <xsl:param name="info" as="element(db:info)"/>
    
    <!-- Deprecated/obsolete articles with replacement -->
    <xsl:if test="$info/db:bibliorelation[@class='uri' and @type='isreplacedby']">
      <rich-imgtext type="error">
        <paragraph>
          Cet article est obsolète et n'est gardé que pour des raisons historiques, 
          <link href="{$info/db:bibliorelation[@class='uri' and @type='isreplacedby']}">car une version 
            plus à jour est disponible</link>. 
        </paragraph>
      </rich-imgtext>
    </xsl:if>
  </xsl:template>
  
  <xsl:function name="tc:has-document-abstract-forum-link-from-parameters" as="xs:boolean">
    <xsl:sequence select="$forum-topic > 0"/>
  </xsl:function>
  
  <xsl:template name="tc:document-abstract-forum-link-from-parameters">
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
  
  <xsl:template match="db:bibliography">
    <section noNumber="1" id="B">
      <title>
        <xsl:choose>
          <xsl:when test="db:info/db:title"><xsl:value-of select="db:info/db:title"/></xsl:when>
          <xsl:when test="db:title"><xsl:value-of select="db:title"/></xsl:when>
          <xsl:otherwise>Bibliographie</xsl:otherwise>
        </xsl:choose>
      </title>
      <xsl:apply-templates mode="content_bibliography"/>
    </section>
  </xsl:template>
  
  <xsl:template match="db:bibliodiv" mode="content_bibliography">
    <section>
      <title>
        <xsl:choose>
          <xsl:when test="db:info/db:title"><xsl:value-of select="db:info/db:title"/></xsl:when>
          <xsl:when test="db:title"><xsl:value-of select="db:title"/></xsl:when>
        </xsl:choose>
      </title>
      <xsl:apply-templates mode="content_bibliography"/>
    </section>
  </xsl:template>
  
  <xsl:template match="db:bibliomixed | db:biblioentry" mode="content_bibliography">
    <xsl:apply-templates mode="content_bibliography"/>
  </xsl:template>
  
  <xsl:template match="db:bibliomixed" mode="content_bibliography">
    <signet id="{@xml:id}">[<xsl:value-of select="$biblioRefs(xs:string(@xml:id))"/>]</signet>
    <paragraph>
      <xsl:apply-templates mode="content_para"/>
    </paragraph>
  </xsl:template>
  
  <xsl:template match="db:biblioentry" mode="content_bibliography">
    <signet id="{@xml:id}">[<xsl:value-of select="$biblioRefs(xs:string(@xml:id))"/>]</signet>
    <!-- Basic formatting of the entry. -->
    <!-- If need be, implement something more intelligent, like https://github.com/docbook/xslTNG/blob/main/src/main/xslt/modules/bibliography.xsl. -->
    <paragraph>
      <xsl:apply-templates mode="content_bibliography_author"/>, 
      <xsl:apply-templates mode="content_bibliography_title"/>. 
    </paragraph>
  </xsl:template>
  
  <xsl:template match="db:authorgroup" mode="content_bibliography_author">
    <xsl:for-each select="db:author">
      <xsl:apply-templates mode="content_bibliography_author"/>
      <xsl:if test="position() != last()">
        <xsl:text>, </xsl:text>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  
  <xsl:template match="db:author" mode="content_bibliography_author">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template match="*" mode="content_bibliography_author">
    <!-- Ignore this. -->
  </xsl:template>
  
  <xsl:template match="db:title" mode="content_bibliography_title">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template match="*" mode="content_bibliography_title">
    <!-- Ignore this. -->
  </xsl:template>
  
  <!-- Catch-all block for the remaining content that has not been handled with. -->
  <xsl:template match="*" mode="#all">
    <xsl:choose>
      <xsl:when test="db:guilabel | db:accel | db:prompt | db:keysym">
        <xsl:message>WARNING: Tag <xsl:value-of select="name(.)" /> has no matching construct in the target format. Content is not lost, but is not marked either.</xsl:message>
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:when test="db:sect1 | db:sect2 | db:sect3 | db:sect4 | db:sect5 | db:sect6">
        <xsl:message>WARNING: Only section tags are supported, not numbered ones like <xsl:value-of select="name(.)" />. You can replace automatically instances of <xsl:value-of select="name(.)" /> by section.</xsl:message>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message>WARNING: Unhandled content with tag <xsl:value-of select="name(.)" />.</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>