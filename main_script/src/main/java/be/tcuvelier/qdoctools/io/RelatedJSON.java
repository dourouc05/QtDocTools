package be.tcuvelier.qdoctools.io;

import be.tcuvelier.qdoctools.core.config.ArticleConfiguration;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.xml.stream.XMLStreamException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class RelatedJSON {
    private final String file;
    private final GlobalConfiguration config;
    private final ArticleConfiguration articleConfiguration;

    public RelatedJSON(String file, GlobalConfiguration config, ArticleConfiguration articleConfiguration) {
        this.file = file + "/related.json";
        this.config = config;
        this.articleConfiguration = articleConfiguration;
    }

    public void toDvpML(String output) throws IOException {
        // Parse the input related file.
        JsonObject root = JsonParser.parseReader(new FileReader(file)).getAsJsonObject();
        JsonObject refs = root.getAsJsonObject("related-documents");

        // Generate the corresponding XML.
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<references>\n");
        xml.append("    <entete>\n");
        xml.append("        <serveur>")
                .append(articleConfiguration.getFtpServer())
                .append("</serveur>\n");
        xml.append("        <chemin>")
                .append(articleConfiguration.getFtpFolder())
                .append("</chemin>\n");
        xml.append("        <urlhttp>")
                .append("http://")
                .append(articleConfiguration.getFtpServer())
                .append(".developpez.com/")
                .append(articleConfiguration.getFtpFolder())
                .append("</urlhttp>\n");
        xml.append("    </entete>\n");

        for (Map.Entry<String, JsonElement> section: refs.entrySet()) {
            xml.append("    <reference>\n")
               .append("        <title>")
                    .append(section.getKey())
                    .append("</title>\n");

            for (Map.Entry<String, JsonElement> ref: section.getValue().getAsJsonObject().entrySet()) {
                xml.append("        <element>")
                        .append("<link href=\"")
                        .append(ref.getKey())
                        .append("\">")
                        .append(ref.getValue().getAsString())
                        .append("</link>")
                        .append("</element>\n");
            }

            xml.append("    </reference>\n");
        }

        xml.append("</references>");

        // Write down this XML.
        Path outputPath = Paths.get(output);
        Files.write(outputPath, xml.toString().getBytes(StandardCharsets.UTF_8));
    }
}
