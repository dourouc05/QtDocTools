<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xpath-file="http://expath.org/ns/file"
  xmlns:xpath-map="http://www.w3.org/2005/xpath-functions/map"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:html="http://www.w3.org/1999/xhtml"
  xmlns:db="http://docbook.org/ns/docbook"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:saxon="http://saxon.sf.net/"
  xmlns:tc="http://tcuvelier.be"
  exclude-result-prefixes="xsl xs html saxon tc db xlink"
  version="3.0">
  
  <xsl:output method="xml" indent="yes" suppress-indentation="inline link i b paragraph code"/>
  <xsl:import-schema schema-location="../../../schemas/dvpml/article.xsd" use-when="system-property('xsl:is-schema-aware')='yes'"/>
  
  <!-- Global sheet parameters without document-specific defaults (e.g., from a configuration file). -->
  <xsl:param name="configuration-file-name" as="xs:string" select="''"/>
  <xsl:param name="document-file-name" as="xs:string" select="''"/>
  <xsl:param name="doc-qt" as="xs:boolean" select="false()"/>
  
  <!-- Load the configuration file. -->
  <xsl:variable name="document" select="."/>
  <xsl:variable name="jsonDocument">
    <xsl:variable name="xmlUri" as="xs:string" select="base-uri()"/>
    <xsl:variable name="jsonUriBase" as="xs:string" select="replace($xmlUri, '.xml', '.json')"/>
    <xsl:variable name="jsonUriSuffix" as="xs:string" select="concat($xmlUri, '.json')"/>
    <xsl:choose>
      <xsl:when test="$configuration-file-name and xpath-file:exists($configuration-file-name)"><xsl:value-of select="json-doc($configuration-file-name)"/></xsl:when>
      <xsl:when test="xpath-file:exists($jsonUriBase)"><xsl:value-of select="json-doc($jsonUriBase)"/></xsl:when>
      <xsl:when test="xpath-file:exists($jsonUriSuffix)"><xsl:value-of select="json-doc($jsonUriSuffix)"/></xsl:when>
      <xsl:otherwise><xsl:value-of select="json-doc('{}')"/></xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  
  <!-- Global sheet parameters with default values from the JSON file. -->
  <!-- TODO: think about storing these values within the XML file as processing instructions. -->
  <xsl:param name="section" as="xs:integer" select="1"/><!-- select="if (xpath-map:contains($jsonDocument, 'section')) then xpath-map:get($jsonDocument, 'section') else 1" -->
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
  
  <!-- Import other modules. -->
  <xsl:include href="docbook_to_dvpml_block.xslt"/>
  <xsl:include href="docbook_to_dvpml_inline.xslt"/>
  <xsl:include href="docbook_to_dvpml_biblio.xslt"/>
    
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
        <xsl:call-template name="tc:document-entete"/>
        <xsl:call-template name="tc:document-license"/>
        <xsl:call-template name="tc:document-see-also"/>
        <xsl:call-template name="tc:document-authors"/>
        <xsl:call-template name="tc:document-related"/>
        
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
          <xsl:call-template name="tc:document-abstract-forum-link"/>
        </synopsis>
        
        <summary>
          <xsl:choose>
            <xsl:when test="not(child::*[2][self::db:section])">
              <!-- A document must have a section in DvpML, not necessarily in DocBook. -->
              <section id="I" noNumber="1">
                <title><xsl:value-of select="if (db:info/db:title) then db:info/db:title else db:title"/></title>
                
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
          <xsl:result-document validation="lax" href="{$document-file-name}_dvp_part_{position() + 1}_dvp.xml">
            <xsl:apply-templates mode="part-root" select="."/>
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
      <xsl:call-template name="tc:document-entete"/>
      <xsl:call-template name="tc:document-license"/>
      <xsl:call-template name="tc:document-authors"/>
      <xsl:call-template name="tc:document-related"/>
      
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
        
        <xsl:call-template name="tc:document-abstract-forum-link"/>
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
        <xsl:apply-templates mode="content" select="./*[self::db:chapter or self::db:section]"/>
        
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
      <xsl:call-template name="tc:document-license"/>
      <xsl:call-template name="tc:document-authors"/>
      <xsl:call-template name="tc:document-related"/>
      
      <xsl:if test="count(db:info/db:abstract/child::node()) &gt; 0 or tc:has-document-abstract-obsoleted-by(db:info) or tc:has-document-abstract-forum-link()">
        <synopsis>
          <!-- voiraussi is not implemented (book is not used for Qt's documentation). -->
          <!-- This simplifies a lot this code. -->
          <xsl:for-each select="db:info/db:abstract/child::node()">
            <xsl:apply-templates mode="content" select="."/>
          </xsl:for-each>
            
          <xsl:call-template name="tc:document-abstract-obsoleted-by">
            <xsl:with-param name="info" select="db:info"/>
          </xsl:call-template>
          <xsl:call-template name="tc:document-abstract-forum-link"/>
        </synopsis>
      </xsl:if>
      
      <summary>
        <!-- Generate the table of contents: first, solo chapters; then, parts (as subsections, one subsection per part). -->
        <section id="TOC" noNumber="1">
          <title>Table des matières</title>
          
          <xsl:if test="./db:chapter">
            <liste>
              <xsl:for-each select="db:chapter">
                <xsl:apply-templates mode="document-toc" select="."/>
              </xsl:for-each>
            </liste>
          </xsl:if>
          
          <xsl:for-each select="db:part">
            <xsl:variable name="partIndex" as="xs:integer" select="position()"/>
            
            <section id="TOC.{$partIndex}" noNumber="1">
              <!-- TODO: see TODO for document-toc -->
              <title><xsl:value-of select="(db:title | db:info/db:title)/text()"/></title>
              
              <!-- TODO: generate the URL based on the configuration instead of bullshit. -->
              <paragraph>
                <link href="http://bullshit#{position()}">
                  <xsl:value-of select="$partIndex"/>
                  <xsl:text>. </xsl:text>
                  <!-- TODO: see TODO for document-toc -->
                  <xsl:value-of select="(db:title | db:info/db:title)/text()"/>
                </link>
              </paragraph>
              
              <liste>
                <xsl:for-each select="db:chapter">
                  <xsl:apply-templates mode="document-toc" select="."/>
                </xsl:for-each>
              </liste>
            </section>
          </xsl:for-each>
          
          <xsl:if test="db:bibliography">
            <section id="TOC.BIB" noNumber="1">
              <title>Bibliographie</title>
              <!-- TODO: generate a true URL. -->
              <paragraph><link href="http://bullshit#bibliography"><xsl:text>Bibliographie</xsl:text></link></paragraph>
            </section>
          </xsl:if>
        </section>
        
        <!-- Generate the chapters outside parts on this first page. -->
        <xsl:for-each select="db:chapter">
          <xsl:variable name="sectionIndex" as="xs:string">
            <xsl:number format="I"/>
          </xsl:variable>
          <section id="{$sectionIndex}">
            <!-- Do manually the title and the first few paragraphs. They would otherwise be considered as synopsis (the title must be put before the paragraphs, hence the special treatment). -->
            
            <xsl:apply-templates mode="content" select="db:title | db:info"/>
            <xsl:for-each select="db:para">
              <paragraph>
                <xsl:apply-templates mode="content_para"/>
              </paragraph>
            </xsl:for-each>
            
            <xsl:apply-templates mode="content" select="./*[not(self::db:title) and not(self::db:info) and not(self::db:para)]"/>
          </section>
        </xsl:for-each>
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
    <!-- TODO: is this used? -->
    <xsl:result-document validation="lax">
      <document>
        <xsl:call-template name="tc:document-entete"/>
        <xsl:call-template name="tc:document-license"/>
        <xsl:call-template name="tc:document-authors"/>
        <xsl:call-template name="tc:document-related"/>
        
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
          <xsl:call-template name="tc:document-abstract-forum-link"/>
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
  
  <xsl:template match="db:part" mode="part-root">
    <xsl:result-document validation="lax">
      <document>
        <xsl:call-template name="tc:document-entete"/>
        <xsl:call-template name="tc:document-license"/>
        <xsl:call-template name="tc:document-authors"/>
        <xsl:call-template name="tc:document-related"/>
        
        <synopsis>
          <xsl:variable name="abstractParagraphs" as="node()*">
            <xsl:choose>
              <xsl:when test="db:partintro">
                <xsl:copy-of select="db:partintro"/>
              </xsl:when>
              <xsl:when test="db:info/following-sibling::*[1][self::db:para]">
                <!-- Something resembling an abstract (paragraphs before the first section). -->
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
          <xsl:call-template name="tc:document-abstract-forum-link"/>
        </synopsis>
        
        <summary>
          <xsl:apply-templates mode="content" select="./*"/>
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
  
  <xsl:template name="tc:document-entete">
    <xsl:param name="generate-summary" as="xs:boolean" select="true()"/>
    
    <entete>
      <rubrique><xsl:value-of select="$section"/></rubrique>
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
      <date><xsl:value-of select="tc:format-date(db:info/pubdate, 'pubdate')"/></date>
      <miseajour><xsl:value-of select="tc:format-date(db:info/date, 'date')"/></miseajour>
      
      
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
    </entete>
  </xsl:template>
  
  <xsl:template name="tc:document-authors">
    <authorDescriptions>
      <xsl:choose>
        <xsl:when test="$document/db:info/(db:authorgroup | db:author | db:editor | db:othercredit)">
          <xsl:for-each select="$document/db:info//(db:author | db:editor | db:othercredit)">
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
    <!-- If the synopsis has a specific form (last paragraph has only one children, a simple list), -->
    <!-- consider this list has links to linked documents. -->
    <xsl:if test="$doc-qt and $document/db:info/db:abstract/db:para[last()]/child::*[1][self::db:simplelist and @role='see-also']">
      <voiraussi>
        <!-- First, the linked documents (previous/next). -->
        <xsl:for-each select="$document/db:info/db:abstract/db:para[last()]/db:simplelist/db:member">
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
  
  <xsl:template name="tc:document-related">
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
  
  <xsl:template match="*" mode="tc:private-title"/>
  
  <xsl:template match="db:info" mode="tc:private-title">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="db:title" mode="tc:private-title">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template match="*" mode="tc:private-subtitle"/>
  
  <xsl:template match="db:info" mode="tc:private-subtitle">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="db:subtitle" mode="tc:private-subtitle">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:template match="*" mode="tc:private-titleabbrev"/>
  
  <xsl:template match="db:info" mode="tc:private-titleabbrev">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="db:titleabbrev" mode="tc:private-titleabbrev">
    <xsl:apply-templates mode="content_para"/>
  </xsl:template>
  
  <xsl:function name="tc:document-title" as="xs:string">
    <xsl:apply-templates mode="tc:private-title" select="$document"/>
  </xsl:function>

  <xsl:function name="tc:document-subtitle" as="xs:string">
    <xsl:apply-templates mode="tc:private-subtitle" select="$document"/>
  </xsl:function>

  <xsl:function name="tc:document-titleabbrev" as="xs:string">
    <xsl:apply-templates mode="tc:private-titleabbrev" select="$document"/>
  </xsl:function>
  
  <xsl:function name="tc:document-titleabbrev-or-title" as="xs:string">
    <xsl:variable name="titleAbbrev" select="tc:document-titleabbrev()"/>
    <xsl:value-of select="if ($titleAbbrev) then $titleAbbrev else tc:document-title()"/>
  </xsl:function>
  
  <xsl:function name="tc:document-description" as="xs:string">
    <xsl:choose>
      <!-- If available, use the version of the abstract that is tailored for the description. Otherwise, use the standard abstract. -->
      <xsl:when test="$document/db:info/db:abstract[@role='description']">
        <xsl:value-of select="$document/db:info/db:abstract[@role='description']/db:para[1]/text()"/>
      </xsl:when>
      <xsl:when test="$document/db:info/db:abstract[not(@role='description')]/db:para">
        <xsl:value-of select="$document/db:info/db:abstract[not(@role='description')]/db:para[1]/text()"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="tc:document-title()"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
  <xsl:function name="tc:document-keywords" as="xs:string">
    <xsl:choose>
      <xsl:when test="$document/db:info/db:keywordset">
        <xsl:for-each select="$document/db:info/db:keywordset/db:keyword">
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