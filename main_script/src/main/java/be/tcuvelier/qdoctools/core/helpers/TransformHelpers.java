package be.tcuvelier.qdoctools.core.helpers;

import be.tcuvelier.qdoctools.core.config.ArticleConfiguration;
import be.tcuvelier.qdoctools.core.config.Configuration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import be.tcuvelier.qdoctools.core.exceptions.InconsistentConfiguration;
import be.tcuvelier.qdoctools.core.handlers.XsltHandler;
import be.tcuvelier.qdoctools.io.DocxInput;
import be.tcuvelier.qdoctools.io.DocxOutput;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TransformHelpers {
    public static void fromDvpMLToDocBook(String input, String output, Configuration config) throws SaxonApiException, IOException {
        Path confPath = ArticleConfiguration.getConfigurationFileName(output);
        if (! confPath.toFile().exists()) { // No configuration file: try to do something about it!
            if (Paths.get(output).toFile().exists()) { // There is already a DvpML file: read what you can from it.
                Files.write(confPath, ArticleConfiguration.parseConfigurationFileFromXml(output).getBytes());
            } else { // Nothing to get inspiration from: create the most basic file.
                Files.write(confPath, ArticleConfiguration.proposeConfigurationFile().getBytes());
            }
        }

        new XsltHandler(new QdtPaths(config).getXsltFromDvpMLPath()).transform(input, output);
    }

    private static Map<String, Object> getTemplateParameters(ArticleConfiguration conf)
            throws InconsistentConfiguration, ConfigurationMissingField {
        HashMap<String, Object> params = new HashMap();

        params.put("section", conf.getSection());

        if (conf.getDocQt()) {
            params.put("doc-qt", true);
        }

        if (conf.getGoogleAnalytics().isPresent()) {
            params.put("google-analytics", conf.getGoogleAnalytics().get());
        }

        if (conf.getLicenseNumber().isPresent()) {
            if (conf.getLicenseAuthor().isEmpty()) {
                throw new InconsistentConfiguration("Field license-author absent when license-number is present");
            }
            if (conf.getLicenseYear().isEmpty()) {
                throw new InconsistentConfiguration("Field license-year absent when license-number is present");
            }

            params.put("license-number", conf.getLicenseNumber().get());
            params.put("license-author", conf.getLicenseAuthor().get());
            params.put("license-year", conf.getLicenseYear().get());
        } else {
            if (conf.getLicenseText().isEmpty()) {
                throw new InconsistentConfiguration("Field license-text absent when license-number is absent also: the document must have a license");
            }

            params.put("license-text", conf.getLicenseText().get());
        }

        if (conf.getFtpUser().isPresent()) {
            params.put("ftp-user", conf.getFtpUser().get());
        }
        params.put("ftp-folder", conf.getFtpFolder());

        return params;
    }

    public static void fromDocBookToDvpML(String input, String output, Configuration config)
            throws SaxonApiException, ConfigurationMissingField, FileNotFoundException, InconsistentConfiguration {
        try {
            ArticleConfiguration conf = new ArticleConfiguration(input);
            new XsltHandler(new QdtPaths(config).getXsltToDvpMLPath()).transform(input, output, getTemplateParameters(conf));
        } catch (FileNotFoundException e) {
            System.err.println("There is no configuration file for the article " + input);
            System.err.println("Here is an example of such a file: ");
            System.err.println(ArticleConfiguration.proposeConfigurationFile());
            throw e;
        }
    }

    public static void fromDOCXToDocBook(String input, String output) throws IOException, XMLStreamException {
        new DocxInput(input).toDocBook(output);
    }

    public static void fromDocBookToDOCX(String input, String output, Configuration config)
            throws IOException, ParserConfigurationException, SAXException, InvalidFormatException {
        new DocxOutput(input, config).toDocx(output);
    }

    public static void fromODTToDocBook(String input, String output) {
        throw new RuntimeException("NOT YET IMPLEMENTED");
    }

    public static void fromDocBookToODT(String input, String output) {
        throw new RuntimeException("NOT YET IMPLEMENTED");
    }
}
