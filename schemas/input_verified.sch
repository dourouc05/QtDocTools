<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:ns uri="http://www.w3.org/1999/xhtml" prefix="html"/>

    <sch:let name="main" value="//html:div[@class = 'content mainContent']"/>
    <sch:let name="types" value="$main/html:div[@class='types']"/>

    <!-- About enumerations (enum). -->
    <sch:pattern>
        <sch:rule context="//html:div[@class='types']/html:h3">
            <sch:assert test="ends-with(@id, '-enum')"/>
            <sch:assert test="./html:a[1]/@name = @id"/>
            <sch:assert test="starts-with(./text()[1], 'enum ')"/>
        </sch:rule>
        <sch:rule context="//html:div[@class='types']/html:h3[@class = 'fn']">
            <sch:assert test="count(./child::html:br) = 0"/>
        </sch:rule>
        <sch:rule context="//html:div[@class='types']/html:h3[@class = 'flags']">
            <sch:assert test="starts-with(./following-sibling::html:br/text()[1], 'flags ')"/>
        </sch:rule>
    </sch:pattern>
    
    <!-- About functions (func). -->
    <sch:pattern>
        <sch:rule context="//html:div[@class='func']/html:h3">
            <sch:assert test="./html:a[1]/@name = @id"/>
        </sch:rule>
    </sch:pattern>
</sch:schema>