package be.tcuvelier.qdoctools.core.helpers;

import be.tcuvelier.qdoctools.cli.MainCommand;
import be.tcuvelier.qdoctools.core.config.Configuration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import be.tcuvelier.qdoctools.core.exceptions.BadConfigurationFile;
import be.tcuvelier.qdoctools.core.handlers.XsltHandler;
import be.tcuvelier.qdoctools.io.DocxInput;
import be.tcuvelier.qdoctools.io.DocxOutput;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class TransformHelpers {
    public static void fromDvpMLToDocBook(String input, String output, Configuration config) throws SaxonApiException, BadConfigurationFile {
        // TODO: What about the configuration file for this document? Generate one in all cases, I guess?
        new XsltHandler(new QdtPaths(config).getXsltFromDvpMLPath()).transform(input, output);
    }

    public static void fromDocBookToDvpML(String input, String output, Configuration config) throws SaxonApiException, BadConfigurationFile {
        // TODO: What about the configuration file for this document?
        new XsltHandler(new QdtPaths(config).getXsltToDvpMLPath()).transform(input, output);
    }

    public static void fromDOCXToDocBook(String input, String output) throws IOException, XMLStreamException {
        new DocxInput(input).toDocBook(output);
    }

    public static void fromDocBookToDOCX(String input, String output, Configuration config) throws IOException, ParserConfigurationException,
            SAXException, InvalidFormatException {
        new DocxOutput(input, config).toDocx(output);
    }

    public static void fromODTToDocBook(String input, String output) {
        throw new RuntimeException("NOT YET IMPLEMENTED");
    }

    public static void fromDocBookToODT(String input, String output) {
        throw new RuntimeException("NOT YET IMPLEMENTED");
    }
}
