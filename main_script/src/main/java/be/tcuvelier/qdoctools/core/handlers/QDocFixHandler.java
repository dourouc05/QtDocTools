package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Handler for fixing QDoc's DocBook output.
//
// Backup extensions:
// - fixQDocBugs: .bak
// - addDates: .bak2
// - fixLinks: .bak3
// - addAuthors: .bak4
public class QDocFixHandler {
    private final Path outputFolder; // Where all the generated files are put.
    private final GlobalConfiguration config;
    private final boolean keepBackups;

    public QDocFixHandler(String output, GlobalConfiguration config, boolean keepBackups) {
        outputFolder = Paths.get(output);
        this.config = config;
        this.keepBackups = keepBackups;
    }

    public void fixQDocBugs() throws IOException {
        // Only the files in the root folder are considered.
        int nFiles = 0;
        int nFilesRewritten = 0;
        int nFilesIgnored = 0;

        for (Path filePath : findDocBook()) {
            boolean hasMatched = false;
            String fileContents = Files.readString(filePath);

            nFiles += 1;

            if (fileContents.isEmpty()) {
                nFilesIgnored += 1;
                continue;
            }

            // <db:para><db:para>QXmlStreamReader is part of <db:simplelist><db:member>xml-tools</db:member>
            // <db:member>qtserialization</db:member></db:simplelist></db:para>
            // </db:para>
            // ->
            // <db:para>QXmlStreamReader is part of <db:simplelist><db:member>xml-tools</db:member>
            // <db:member>qtserialization</db:member></db:simplelist></db:para>
            // More generic! Before https://codereview.qt-project.org/c/qt/qttools/+/527899.
            {
                Pattern regex = Pattern.compile(
                        "<db:para><db:para>(.*) is part of <db:simplelist>(.*)</db:simplelist></db:para>\n" +
                        "</db:para>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;

                    fileContents = matches.replaceAll("<db:para>$1 is part of <db:simplelist>$2</db:simplelist></db:para>");
                }
            }

            //  xml:id=""
            // Nothing.
            {
                Pattern regex = Pattern.compile(" xml:id=\"\"");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("");
                }
            }

            // <db:img src="images/happy.gif"/>
            // \inlineimage happy.gif
            {
                Pattern regex = Pattern.compile("<db:img src=\"images/happy\\.gif\"/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("\\\\inlineimage happy.gif");
                }
            }

            // <db:emphasis&#246;/>
            // <db:emphasis>&#246;</db:emphasis>
            {
                Pattern regex = Pattern.compile("<db:emphasis&#246;/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:emphasis>&#246;</db:emphasis>");
                }
            }

            // xlink:to="Qt for QNX"
            // xlink:to="qnx.xml"
            // And family.
            {
                Pattern regex = Pattern.compile("xlink:to=\"Qt for QNX\"");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("xlink:to=\"qnx.xml\"");
                }
            }
            {
                Pattern regex = Pattern.compile("xlink:to=\"Desktop Integration\"");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("xlink:to=\"desktop-integration.xml\"");
                }
            }

            // <db:anchor xml:id="section-commands"/>
            // <db:section xml:id="section-commands">
            // ->
            // <db:section xml:id="section-commands">
            // Only if both IDs coincide! Otherwise, it's an alias that's perfectly allowed.
            // Happily, Java regexes allow back-references to groups!
            {
                Pattern regex = Pattern.compile("<db:anchor xml:id=\"(.*)\"/>\n<db:section xml:id=\"\\1\">");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:section xml:id=\"$1\">");
                }
            }

            // <db:section><db:title>Universal.accent : color</db:title><db:fieldsynopsis><db:type>color</db:type>
            // <db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // <db:anchor xml:id="universal-accent-attached-prop"/>
            // ->
            // <db:section xml:id="universal-accent-attached-prop"><db:title>Universal.accent : color</db:title>
            // <db:fieldsynopsis><db:type>color</db:type><db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // More generic! Once https://codereview.qt-project.org/c/qt/qtdeclarative/+/528728 is in.
            {
                Pattern regex = Pattern.compile(
                        "<db:section><db:title>(.*) : (.*)</db:title><db:fieldsynopsis><db:type>(.*)</db:type><db:varname>(.*)</db:varname></db:fieldsynopsis><db:anchor xml:id=\"(.*)\"/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    // TODO: assert that $1 == $4 and $1 == $2.
                    fileContents = matches.replaceAll(
                            "<db:section xml:id=\"$5\"><db:title>$1 : $2</db:title><db:fieldsynopsis><db:type>$3</db:type><db:varname>$4</db:varname></db:fieldsynopsis>");
                }
            }

            // <db:section xml:id="universal-accent-attached-prop"><db:title>Universal.accent : color</db:title><db:fieldsynopsis><db:type>color</db:type>
            // <db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // <db:anchor xml:id="universal-accent-attached-prop"/>
            // ->
            // <db:section xml:id="universal-accent-attached-prop"><db:title>Universal.accent : color</db:title>
            // <db:fieldsynopsis><db:type>color</db:type><db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // More generic! Before https://codereview.qt-project.org/c/qt/qtdeclarative/+/528728.
            {
                Pattern regex = Pattern.compile(
                        "<db:section xml:id=\"(.*)\"><db:title>(.*) : (.*)</db:title><db:fieldsynopsis><db:type>(.*)</db:type><db:varname>(.*)</db:varname></db:fieldsynopsis><db:anchor xml:id=\"(.*)\"/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    // TODO: assert that $1 == $6 and $2 == $5 and $2 == $3.
                    fileContents = matches.replaceAll(
                            "<db:section xml:id=\"$1\"><db:title>$2 : $3</db:title><db:fieldsynopsis><db:type>$4</db:type><db:varname>$5</db:varname></db:fieldsynopsis>");
                }
            }

            // </db:abstract>
            // </db:info>
            // </db:article>
            // ->
            // </db:abstract>
            // </db:info>
            // <db:para/>
            // </db:article>
            {
                Pattern regex = Pattern.compile(
                        "</db:abstract>\n</db:info>\n</db:article>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            "</db:abstract>\n</db:info>\n<db:para/>\n</db:article>");
                }
            }

            // QAbstract3DGraph only, two parts.
            // ----------------------------
            // </db:itemizedlist>
            // </db:td>
            // <db:tr>
            // <db:td>
            // <db:para>
            // Add a </db:tr>.
            // ----------------------------
            // </db:tr>
            // </db:tr>
            // <db:para>The <db:code>SelectionFlags</db:code> type is a typedef for
            // <db:code><db:link xlink:href="qflags.xml">QFlags</db:link>&lt;SelectionFlag&gt;. </db:code>
            // It stores an OR combination of <db:code>SelectionFlag</db:code> values.</db:para>
            // </db:informaltable>
            // Replace a double </db:tr>, move </db:informaltable> before the paragraph.
            // ----------------------------
            // https://bugreports.qt.io/browse/QTBUG-120457
            {
                Pattern regex = Pattern.compile(
                        "</db:itemizedlist>\n</db:td>\n<db:tr>\n<db:td>\n<db:para>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            "</db:itemizedlist>\n</db:td>\n</db:tr>\n<db:tr>\n<db:td>\n<db:para>");
                }
            }
            {
                Pattern regex = Pattern.compile(
                        "</db:tr>\n</db:tr>\n<db:para>(.*)</db:para>\n</db:informaltable>\n<db:section");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            "</db:tr>\n</db:informaltable>\n<db:para>$1</db:para>\n<db:section");
                }
            }

            // overviews.xml and licenses-used-in-qt.xml and cmake-command-reference.xml and activeqt-tools.xml.
            // <db:variablelist role="explanations-positioning">
            // <db:listitem>
            // Replace by a <db:itemizedlist>, but also the closing tag
            // Hence, a simple regex doesn't capture enough.
            // Hopefully, in overviews.xml and activeqt-tools.xml, there are no true <db:variablelist>s.
            // However, this pattern also appears in licenses-used-in-qt.xml and cmake-command-reference.xml, where true <db:variablelist>s also appear.
            // The only occurrence is in the middle of licenses-used-in-qt.xml (or at the end for cmake-command-reference.xml), with true <db:variablelist>s both before and after.
            // https://codereview.qt-project.org/c/qt/qttools/+/527900
            if (filePath.toString().contains("overviews.xml") || filePath.toString().contains("activeqt-tools.xml")) {
                Pattern regex = Pattern.compile(
                        "<db:variablelist role=\"(.*)\">\n<db:listitem>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = fileContents.replaceAll("db:variablelist", "db:itemizedlist");
                }
            }
            if (filePath.toString().contains("licenses-used-in-qt.xml")) { // Qt 6.5.
                Pattern regex = Pattern.compile(
                        """
                                <db:variablelist role="annotatedattributions">
                                <db:listitem>
                                <db:para><db:link xlink:href="qtsvg-svggenerator-example.xml" xlink:role="page">SVG Generator Example</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtsvg-svgviewer-example.xml" xlink:role="page">SVG Viewer Example</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtsvg-richtext-textobject-example.xml" xlink:role="page">Text Object Example</db:link></db:para>
                                </db:listitem>
                                </db:variablelist>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            """
                            <db:itemizedlist role="annotatedattributions">
                            <db:listitem>
                            <db:para><db:link xlink:href="qtsvg-svggenerator-example.xml" xlink:role="page">SVG Generator Example</db:link></db:para>
                            </db:listitem>
                            <db:listitem>
                            <db:para><db:link xlink:href="qtsvg-svgviewer-example.xml" xlink:role="page">SVG Viewer Example</db:link></db:para>
                            </db:listitem>
                            <db:listitem>
                            <db:para><db:link xlink:href="qtsvg-richtext-textobject-example.xml" xlink:role="page">Text Object Example</db:link></db:para>
                            </db:listitem>
                            </db:itemizedlist>""");
                }
            }
            if (filePath.toString().contains("licenses-used-in-qt.xml")) { // Qt 6.4.
                Pattern regex = Pattern.compile(
                        """
                                <db:variablelist role="annotatedattributions">
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-modelviewclient-example\\.xml" xlink:role="page">Model-View Client</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-modelviewserver-example\\.xml" xlink:role="page">Model-View Server</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-qmlmodelviewclient-example\\.xml" xlink:role="page">QML Model-View Client</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-ssl-example\\.xml" xlink:role="page">QtRemoteObjects SSL Example</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-websockets-example\\.xml" xlink:role="page">QtRemoteObjects WebSockets Example</db:link></db:para>
                                </db:listitem>
                                </db:variablelist>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            """
                                    <db:itemizedlist role="annotatedattributions">
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-modelviewclient-example.xml" xlink:role="page">Model-View Client</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-modelviewserver-example.xml" xlink:role="page">Model-View Server</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-qmlmodelviewclient-example.xml" xlink:role="page">QML Model-View Client</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-ssl-example.xml" xlink:role="page">QtRemoteObjects SSL Example</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-websockets-example.xml" xlink:role="page">QtRemoteObjects WebSockets Example</db:link></db:para>
                                    </db:listitem>
                                    </db:itemizedlist>""");
                }
            }
            if (filePath.toString().contains("cmake-command-reference.xml")) {
                Pattern regex = Pattern.compile("""
                        <db:variablelist role="cmake-macros-qtscxml">
                        <db:listitem>
                        <db:para><db:link xlink:href="qtscxml-cmake-qt-add-statecharts.xml" xlink:role="page">qt_add_statecharts</db:link></db:para>
                        </db:listitem>
                        </db:variablelist>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("""
                            <db:itemizedlist role="cmake-macros-qtscxml">
                            <db:listitem>
                            <db:para><db:link xlink:href="qtscxml-cmake-qt-add-statecharts.xml" xlink:role="page">qt_add_statecharts</db:link></db:para>
                            </db:listitem>
                            </db:itemizedlist>""");
                }
            }

            // qml-qt5compat-graphicaleffects-gaussianblur.xml
            // https://codereview.qt-project.org/c/qt/qt5compat/+/527903
            if (filePath.toString().contains("qml-qt5compat-graphicaleffects-gaussianblur.xml")) {
                Pattern regex = Pattern.compile(
                        """
                    <db:para><db:inlinemediaobject>
                    <db:imageobject>
                    <db:imagedata fileref="images/GaussianBlur_deviation_graph\\.png"/>
                    </db:imageobject>
                    </db:inlinemediaobject></db:para>
                    <db:title>The image above shows the Gaussian function with two different deviation values, yellow \\(1\\) and cyan \\(2\\.7\\)\\. The y-axis shows the weights, the x-axis shows the pixel distance.</db:title>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("""
                            <db:figure>
                            <db:title>The image above shows the Gaussian function with two different deviation values, yellow (1) and cyan (2.7). The y-axis shows the weights, the x-axis shows the pixel distance.</db:title>
                            <db:mediaobject>
                            <db:imageobject>
                            <db:imagedata fileref="images/GaussianBlur_deviation_graph.png"/>
                            </db:imageobject>
                            </db:mediaobject>
                            </db:figure>""");
                }
            }

            // qml-color.xml, qcolorconstants.xml
            // <div style="padding:10px;color:#fff;background:#000000;"></div>
            // <db:phrase role="color:#000000">&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</db:phrase>
            // https://codereview.qt-project.org/c/qt/qtdeclarative/+/421106
            if (filePath.toString().contains("qml-color.xml") || filePath.toString().contains("qcolorconstants.xml")) {
                Pattern regex = Pattern.compile("<div style=\"padding:10px;color:#fff;background:#(.*);\"></div>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:phrase role=\"color:#$1\">&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</db:phrase>");
                }
            }

            // licenses-used-in-qt.xml
            // <db:bridgehead renderas="sect2" xml:id="additional-information">Additional Information</db:bridgehead><db:para>
            // Transform to a real section, close it at the end of the document. Fixed at Qt 6.5.
            if (filePath.toString().contains("licenses-used-in-qt.xml")) {
                Pattern regex = Pattern.compile("<db:bridgehead renderas=\"sect2\" xml:id=\"additional-information\">Additional Information</db:bridgehead><db:para>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:section xml:id=\"additional-information\"><db:title>Additional Information</db:title><db:para>");
                    fileContents = fileContents.replaceAll("</db:variablelist>\n</db:article>", "</db:variablelist>\n</db:section>\n</db:article>");
                }
            }

            // 12-0-qdoc-commands-miscellaneous.xml
            // The original text has indentation (eight spaces):
            //        <blockquote>
            //        <h1 class="title">Foo Namespace</h1>
            //        <p>A namespace. <a>More...</a></p>
            //        <div class="table"><table class="alignedsummary">
            //        <tr><td class="memItemLeft rightAlign topAlign"> Header:</td><td class="memItemRight bottomAlign"> <span class="preprocessor">#include &lt;Bar&gt;</span></td></tr>
            //        <tr><td class="memItemLeft rightAlign topAlign"> CMake:</td><td class="memItemRight bottomAlign"> find_package(Qt6 REQUIRED COMPONENTS Baz)</td></tr>
            //        </table></div>
            //        </blockquote>
            // -> CDATA:
            // <db:blockquote><db:programlisting role="raw-html"><![CDATA[<h1 class="title">Foo Namespace</h1>
            //            <p>A namespace. <a>More...</a></p>
            //            <div class="table"><table class="alignedsummary">
            //            <tr><td class="memItemLeft rightAlign topAlign"> Header:</td><td class="memItemRight bottomAlign"> <span class="preprocessor">#include &lt;Bar&gt;</span></td></tr>
            //            <tr><td class="memItemLeft rightAlign topAlign"> CMake:</td><td class="memItemRight bottomAlign"> find_package(Qt6 REQUIRED COMPONENTS Baz)</td></tr>
            //            </table></div>]]></db:programlisting>
            // </db:blockquote>
            if (filePath.toString().contains("12-0-qdoc-commands-miscellaneous.xml")) {
                Pattern regex = Pattern.compile("        <blockquote>\n" +
                        "        <h1 class=\"title\">Foo Namespace</h1>\n" +
                        "        <p>A namespace. <a>More...</a></p>\n" +
                        "        <div class=\"table\"><table class=\"alignedsummary\">\n" +
                        "        <tr><td class=\"memItemLeft rightAlign topAlign\"> Header:</td><td class=\"memItemRight bottomAlign\"> <span class=\"preprocessor\">#include &lt;Bar&gt;</span></td></tr>\n" +
                        "        <tr><td class=\"memItemLeft rightAlign topAlign\"> CMake:</td><td class=\"memItemRight bottomAlign\"> find_package\\(Qt6 REQUIRED COMPONENTS Baz\\)</td></tr>\n" +
                        "        </table></div>\n" +
                        "        </blockquote>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:blockquote><db:programlisting role=\"raw-html\"><![CDATA[<h1 class=\"title\">Foo Namespace</h1>\n            <p>A namespace. <a>More...</a></p>\n            <div class=\"table\"><table class=\"alignedsummary\">\n            <tr><td class=\"memItemLeft rightAlign topAlign\"> Header:</td><td class=\"memItemRight bottomAlign\"> <span class=\"preprocessor\">#include &lt;Bar&gt;</span></td></tr>\n            <tr><td class=\"memItemLeft rightAlign topAlign\"> CMake:</td><td class=\"memItemRight bottomAlign\"> find_package(Qt6 REQUIRED COMPONENTS Baz)</td></tr>\n            </table></div>]]></db:programlisting>\n</db:blockquote>");
                }
            }

            // YouTube video:
            // <db:videodata fileref="...">
            // ->
            // <?db video="iframe"?>
            // <db:videodata format="youtube" fileref="...">
            // A YouTube ID is mostly composed of letters and digits, plus hyphens and underscores. As of Qt 6, all
            // videos are hosted on YouTube.
            // https://codereview.qt-project.org/c/qt/qtbase/+/540084
            {
                Pattern regex = Pattern.compile("<db:videodata fileref=\"(.*)\">");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<?db video=\"iframe\"?>\n<db:videodata format=\"youtube\" fileref=\"$1\">");
                }
            }

            // <db:emphasis&#246;/>
            // ->
            // <db:emphasis>&#246;</db:emphasis>
            {
                Pattern regex = Pattern.compile("<db:emphasis&#246;/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:emphasis>&#246;</db:emphasis>");
                }
            }

            // classes.xml and similar lists, with just one letter
            // <db:member><db:link xlink:href="a">A</db:link></db:member>
            // ->
            // <db:member><db:link xlink:href="#a">A</db:link></db:member>
            {
                Pattern regex = Pattern.compile("""
                        <db:member><db:link xlink:href="([a-z])">([A-Z])</db:link></db:member>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("""
                            <db:member><db:link xlink:href="#$1">$2</db:link></db:member>""");
                }
            }

            if (!hasMatched) {
                // This file has not changed: no need to have a back-up file or to spend time
                // writing on disk.
                continue;
            }
            nFilesRewritten += 1;

            // TODO: extract this feature to a method.
            if (keepBackups) {
                Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak");
                if (!fileBackUp.toFile().exists()) {
                    Files.move(filePath, fileBackUp);
                }
                Files.write(filePath, fileContents.getBytes());
            }
        }

        System.out.println("++> " + nFiles + " postprocessed, " +
                nFilesRewritten + " rewritten, " + nFilesIgnored + " ignored.");
    }

    public void addDates() throws IOException {
        // The following patterns appears only once per file:
        // </db:abstract>
        // </db:info>
        // Insert the dates just there.
        Pattern regex = Pattern.compile("</db:abstract>\n</db:info>");
        String replacement = "</db:abstract>\n<db:pubdate>" + java.time.LocalDate.now() + "</db:pubdate>\n<db:date>" + java.time.LocalDate.now() + "</db:date>\n</db:info>";

        for (Path filePath : findDocBook()) {
            String fileContents = Files.readString(filePath);

            fileContents = regex.matcher(fileContents).replaceAll(replacement);

            // TODO: extract this feature to a method.
            if (keepBackups) {
                Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak2");
                if (!fileBackUp.toFile().exists()) {
                    Files.move(filePath, fileBackUp);
                }
                Files.write(filePath, fileContents.getBytes());
            }
        }
    }

    public void addAuthors() throws IOException {
        // Insert the following authorship information just before </db:info> -- which means that this function MUST be
        // called after `addDates()`. Use an `authorgroup` with just one entry to make room for translators. The check
        // for `</db:date>` in the regex ensures that this function can be called several times on the same XML files.
        //     <db:authorgroup>
        //        <db:author>
        //            <db:orgname class="corporation">The Qt Company Ltd.</db:orgname>
        //        </db:author>
        //    </db:authorgroup>
        Pattern regex = Pattern.compile("</db:date>\n</db:info>");
        String replacement = "</db:date>\n<db:authorgroup>\n<db:author>\n<db:orgname class=\"corporation\">The Qt Company Ltd.</db:orgname>\n</db:author>\n</db:authorgroup>\n</db:info>";

        for (Path filePath : findDocBook()) {
            String fileContents = Files.readString(filePath);

            fileContents = regex.matcher(fileContents).replaceAll(replacement);

            // TODO: extract this feature to a method.
            if (keepBackups) {
                Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak4");
                if (!fileBackUp.toFile().exists()) {
                    Files.move(filePath, fileBackUp);
                }
                Files.write(filePath, fileContents.getBytes());
            }
        }
    }

    public void fixLinks() throws IOException {
        // Update the links (but not the anchors):
        //    xlink:href="../qtcore/qobject.xml"
        //    xlink:href="../qdoc/22-qdoc-configuration-generalvariables.xml#headers-variable"
        Pattern regex = Pattern.compile("xlink:href=\"\\.\\./[a-z]*/(.*)\\.xml");

        for (Path filePath : findDocBook()) {
            String fileContents = Files.readString(filePath);

            fileContents = regex.matcher(fileContents).replaceAll("xlink:href=\"$1.xml");

            // TODO: extract this feature to a method.
            if (keepBackups) {
                Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak3");
                if (!fileBackUp.toFile().exists()) {
                    Files.move(filePath, fileBackUp);
                }
                Files.write(filePath, fileContents.getBytes());
            }
        }
    }

    public void validateDocBook() throws IOException, SAXException {
        int nFiles = 0;
        int nEmptyFiles = 0;
        int nValidFiles = 0;
        for (Path filePath : findDocBook()) {
            nFiles += 1;
            if (Files.size(filePath) == 0) {
                // Validation can only fail for empty files.
                nEmptyFiles += 1;
                continue;
            }

            if (ValidationHelper.validateDocBook(filePath, config)) {
                nValidFiles += 1;
            } else {
                System.out.println("!!> Invalid file: " + filePath);
            }
        }
        System.out.println("++> " + nFiles + " validated, " +
                nValidFiles + " valid, " + (nFiles - nValidFiles) + " invalid, " + nEmptyFiles +
                " empty.");
    }

    // TODO: this does not belong to a handler.
    private List<Path> findWithExtension(@SuppressWarnings("SameParameterValue") String extension) {
        String[] fileNames =
                outputFolder.toFile().list((current, name) -> name.endsWith(extension));
        if (fileNames == null || fileNames.length == 0) {
            return Collections.emptyList();
        } else {
            return Arrays.stream(fileNames).map(outputFolder::resolve).collect(Collectors.toList());
        }
    }

    // TODO: this does not belong to a handler.
    public List<Path> findDocBook() {
        return findWithExtension(".xml");
    }
}
