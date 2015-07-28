<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:ns uri="http://www.w3.org/1999/xhtml" prefix="html"/>

    <sch:let name="main" value="//html:div[@class = 'content mainContent']"/>
    <sch:let name="types" value="$main/html:div[@class = 'types']"/>

    <sch:let name="title" value="//html:h1[@class = 'title']/text()"/>
    <sch:let name="isClass"
        value="
            starts-with($title, 'Q')
            and ends-with($title, ' Class')
            and count(contains($title, ' ')) = 1"/>
    <sch:let name="className"
        value="
            if ($isClass) then
                substring-before($title, ' Class')
            else
                ''"/>

    <sch:pattern>
        <sch:title>General things</sch:title>

        <sch:rule context="//html:a[@name]">
            <sch:assert test="not(./text() = '')">An HTML &lt;a&gt; anchor has some text
                inside.</sch:assert>
        </sch:rule>
    </sch:pattern>

    <sch:pattern>
        <sch:title>Existence of included documents (their contents are not checked)</sch:title>

        <sch:rule
            context="//html:div[@class = 'content mainContent']/html:ul[preceding::html:div[@class = 'table']]/html:li/html:a/@href">
            <!-- 
                -members files are not interesting, their content can be regenerated by inheritance.
                resolve-uri() allows to force URI resolution with respect to the XML document base, 
                not something else. 
                
                Recognised files: obsolete, style. 
            -->
            <sch:let name="fileNameHtml" value="."/>
            <sch:let name="fileName" value="replace($fileNameHtml, '.html', '')"/>
            <sch:let name="fileNameXml" value="concat($fileName, '.xml')"/>
            <sch:let name="pathXml" value="resolve-uri($fileNameXml, base-uri())"/>
            <sch:assert
                test="
                    if (ends-with($fileName, '-members')) then
                        true()
                    else
                        ends-with($fileName, '-obsolete') or ends-with($fileName, '-styling')"
                >Unrecognised other document type: <sch:value-of select="$fileName"/>.</sch:assert>
            <sch:assert
                test="
                    if (ends-with($fileName, '-members')) then
                        true()
                    else
                        doc-available($pathXml)"
                >Linked related document unavailable: <sch:value-of select="$fileNameXml"
                />.</sch:assert>
        </sch:rule>
    </sch:pattern>

    <sch:pattern>
        <sch:title>About enumerations (enum)</sch:title>

        <sch:rule context="//html:div[@class = 'types']/html:h3">
            <sch:assert test="ends-with(@id, '-enum')">An enumeration has an xml:id that does not
                end with '-enum'.</sch:assert>
            <sch:assert test="./html:a[1]/@name = @id">Mismatch between the &lt;h3&gt; title's id
                and the HTML &lt;a&gt; anchor.</sch:assert>
            <sch:assert test="starts-with(./text()[1], 'enum ')">The text for an enumeration does
                not start with the keyword 'enum'.</sch:assert>
        </sch:rule>
        <sch:rule context="//html:div[@class = 'types']/html:h3[@class = 'fn']">
            <sch:assert test="count(./child::html:br) = 0">A pure enumeration has a line
                break.</sch:assert>
        </sch:rule>
        <sch:rule context="//html:div[@class = 'types']/html:h3[@class = 'flags']">
            <sch:assert test="starts-with(./following-sibling::html:br/text()[1], 'flags ')">An
                enumeration-plus-flags has no line break.</sch:assert>
        </sch:rule>
    </sch:pattern>

    <sch:pattern>
        <sch:title>About functions (func)</sch:title>

        <sch:rule
            context="//html:h3[@class = 'fn' and not(contains(@id, '-enum')) and not(contains(@id, '-typedef'))]">
            <sch:let name="rawString" value="string-join(./text(), '')"/>
            <sch:let name="functionAnchor" value="./@id"/>
            <sch:let name="functionName" value="./html:span[@class = 'name']"/>
            <sch:let name="isCtor" value="starts-with($functionAnchor, $className)"/>
            <sch:let name="isDtor" value="starts-with($functionAnchor, 'dtor.')"/>
            <sch:let name="isFct" value="not($isCtor or $isDtor)"/>

            <!-- Generalities. -->
            <sch:assert test="@id = ./html:a[1]/@name">Mismatch between the &lt;h3&gt; title's id
                and the HTML &lt;a&gt; anchor: the first one is <sch:value-of
                    select="./html:a[1]/@name"/>, the latter <sch:value-of select="@id"
                />.</sch:assert>
            <sch:assert test="count(./html:code) &lt;= 1">Too many HTML &lt;code&gt;: found
                    <sch:value-of select="count(./html:code)"/>.</sch:assert>
            <sch:let name="modifier"
                value="
                    if (count(./html:code) = 1) then
                        normalize-space(./html:code/text())
                    else
                        '[static]'"/>
            <sch:assert test="$modifier = '[static]' or $modifier = '(obsolete)'">A function has a
                modifier that is not recognised: <sch:value-of
                    select="normalize-space(./html:code/text())"/>. </sch:assert>

            <!-- Test the first nodes to check their types. -->
            <sch:let name="firstNode" value="./child::node()[1]"/>
            <sch:let name="secondNode" value="./child::node()[2]"/>
            <sch:assert test="$firstNode[self::html:a][@name]">The first node of the function's
                &lt;h3&gt; title is not an HTML &lt;a&gt; anchor: <sch:value-of
                    select="name($firstNode)"/>.</sch:assert>
            <sch:assert
                test="
                    $secondNode[self::text()]
                    or $secondNode[self::html:code]
                    or $secondNode[self::html:span][@class = 'type']"
                >The first content node of the function's &lt;h3&gt; title is not recognised:
                    <sch:value-of select="name($secondNode)"/>.</sch:assert>

            <!-- Check the class name. -->
            <sch:report
                test="normalize-space($functionName/preceding-sibling::text()[last()]) = concat($className, '::')"
                >Wrong class name in fully qualified function prototype: got '<sch:value-of
                    select="normalize-space($functionName/preceding-sibling::text()[last()])"/>',
                expected '<sch:value-of select="concat($className, '::')"/>'.</sch:report>

            <!-- Check the extremities of the argument list, and the presence of a const. -->
            <sch:assert
                test="starts-with($functionName/following-sibling::node()[1][self::text()], '(')"
                >The function has no argument list; looking for a (, got <sch:value-of
                    select="$functionName/following-sibling::node()[1][self::text()]"
                />.</sch:assert>
            <sch:let name="lastQualifier"
                value="normalize-space(substring-after($functionName/following-sibling::node()[last()][self::text()], ')'))"/>
            <sch:assert test="$lastQualifier = 'const' or $lastQualifier = ''">The function has an
                unrecognised qualifier: <sch:value-of select="$lastQualifier"/>.</sch:assert>
            <sch:assert
                test="ends-with(replace($functionName/following-sibling::node()[last()][self::text()], ' const', ''), ')')"
                >The function has no argument list; looking for a ), got <sch:value-of
                    select="$functionName/following-sibling::node()[last()][self::text()]"
                />.</sch:assert>

            <!-- Testing the templates (no nesting). -->
            <sch:let name="nTemplates" value="count(tokenize($rawString, '&lt;'))"/>
            <sch:assert
                test="count(tokenize($rawString, '&lt;')) = count(tokenize($rawString, '>'))"
                >Unbalanced chevrons for templates. </sch:assert>
        </sch:rule>
    </sch:pattern>

    <sch:pattern>
        <sch:title>About related non-members (nonmem)</sch:title>

        <sch:rule context="//html:div[@class = 'relnonmem']/html:h3">
            <sch:assert test="./html:a[1]/@name = @id">Mismatch between the &lt;h3&gt; title's id
                and the HTML &lt;a&gt; anchor.</sch:assert>
            <sch:assert
                test="
                    if (contains(@id, '-typedef')) then
                        starts-with(./html:a/following-sibling::text(), 'typedef')
                    else
                        true()"
            />
        </sch:rule>
    </sch:pattern>
</sch:schema>
