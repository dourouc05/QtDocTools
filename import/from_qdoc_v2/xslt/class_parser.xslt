<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tc="http://tcuvelier.be"
    exclude-result-prefixes="xs xsl tc"
    version="3.0">
    
    <xsl:output indent="yes"></xsl:output>
    
    <!--<xsl:param name="local-folder" as="xs:string" select="'file:///D:/Qt/Doc512v2/html/'"/>-->
    <xsl:param name="local-folder" as="xs:string" required="true"/>
    
    <xsl:function name="tc:normalise-name">
        <xsl:param name="name" as="xs:string"/>
        <xsl:value-of select="translate($name, ' ', '')"/>
    </xsl:function>
    
    <xsl:template name="main">
        <classes>
            <xsl:for-each select="collection(concat($local-folder, '?select=*.webxml'))[./WebXML/document/class]">
                <class>
                    <name><xsl:value-of select="if (./WebXML/document/class/@fullname) then ./WebXML/document/class/@fullname else ./WebXML/document/class/@name"/></name>
                    <status><xsl:value-of select="./WebXML/document/class/@status"/></status>
                    <bases><xsl:value-of select="./WebXML/document/class/@bases"/></bases>
                    
                    <functions>
                        <xsl:for-each select="./WebXML/document/class/function">
                            <function>
                                <name><xsl:value-of select="@name"/></name>
                                <parameters><xsl:value-of select="tc:normalise-name(string-join(parameter/@type, ','))"/></parameters>
                            </function>
                        </xsl:for-each>
                    </functions>
                    
                    <variables>
                        <xsl:for-each select="./WebXML/document/class/variable">
                            <variable>
                                <name><xsl:value-of select="tc:normalise-name(@name)"/></name>
                                <type><xsl:value-of select="@type"/></type>
                            </variable>
                        </xsl:for-each>
                    </variables>
                    
                    <classes>
                        <xsl:for-each select="./WebXML/document/class/class">
                            <class>
                                <name><xsl:value-of select="tc:normalise-name(@name)"/></name>
                            </class>
                        </xsl:for-each>
                    </classes>
                    
                    <properties>
                        <xsl:for-each select="./WebXML/document/class/property">
                            <property>
                                <name><xsl:value-of select="tc:normalise-name(@name)"/></name>
                                <type><xsl:value-of select="@type"/></type>
                            </property>
                        </xsl:for-each>
                    </properties>
                    
                    <namespaces>
                        <xsl:for-each select="./WebXML/document/class/namespace">
                            <namespace>
                                <name><xsl:value-of select="tc:normalise-name(@name)"/></name>
                            </namespace>
                        </xsl:for-each>
                    </namespaces>
                    
                    <enums>
                        <xsl:for-each select="./WebXML/document/class/enum">
                            <enum>
                                <name><xsl:value-of select="tc:normalise-name(@name)"/></name>
                            </enum>
                        </xsl:for-each>
                    </enums>
                    
                    <typedefs>
                        <xsl:for-each select="./WebXML/document/class/typedef">
                            <typedef>
                                <name><xsl:value-of select="tc:normalise-name(@name)"/></name>
                            </typedef>
                        </xsl:for-each>
                    </typedefs>
                </class>
            </xsl:for-each>
        </classes>
    </xsl:template>
</xsl:stylesheet>
