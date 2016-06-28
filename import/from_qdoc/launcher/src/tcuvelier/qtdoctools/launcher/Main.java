package tcuvelier.qtdoctools.launcher;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    // Saxon HE: run with -Djava.xml.transform.TransformerFactory=net.sf.saxon.TransformerFactoryImpl.
    // Saxon PE: run with -Djava.xml.transform.TransformerFactory=com.saxonica.config.ProfessionalTransformerFactory.
    // Saxon EE: run with -Djava.xml.transform.TransformerFactory=com.saxonica.config.EnterpriseTransformerFactory.

    private static final String extension = ".xml"; // Extension for files that are recognised here.
    private static final String[] forbiddenSuffixes = { "-members", "-compat", "-obsolete" };
    private static final String[] ignoredSuffixes = { "-manifest" };
    private static final Map<String, String[]> ignoredFiles = new HashMap<>();

    static {
        ignoredFiles.put("qtdoc", new String[] {
                "classes.xml", "obsoleteclasses.xml", "hierarchy.xml", "qmlbasictypes.xml", "qmltypes.xml"
        });
    }

    // Arguments: XSLT file, folder.
    public static void main(String[] args) throws TransformerConfigurationException, IOException {
        args = new String[] { "F:\\QtDoc\\QtDoc\\QtDocTools\\import\\from_qdoc\\xslt\\qdoc2db_5.4.xsl", "F:\\QtDoc\\output\\html\\qtdoc2"};

        // Parse the command line.
        if (args.length != 2) {
            throw new RuntimeException("Usage: xsl folder");
        }
        String xslt = args[0];
        String dir = args[1];
        Path folder = Paths.get(dir);
        String moduleName = folder.getFileName().toString();
        if (! xslt.endsWith(".xsl") || xslt.length() == 4) {
            throw new RuntimeException("Unrecognised style sheet name: " + xslt);
        }
        if (! Files.exists(Paths.get(xslt))) {
            throw new IOException("Style sheet does not exist: " + xslt);
        }
        if (! Files.exists(folder)) {
            throw new IOException("Input folder does not exist: " + dir);
        }

        // Compile the style sheet.
        Templates templates = TransformerFactory.newInstance().newTemplates(new StreamSource(xslt));
        Transformer transformer = templates.newTransformer();

        // Iterate through the given folder.
        String[] folderSuffixes = Stream.concat(Arrays.stream(forbiddenSuffixes), Arrays.stream(ignoredSuffixes))
                                        .map(s -> s + extension)
                                        .toArray(String[]::new);
        Files.walk(folder)
             .filter(Files::isRegularFile)
             .filter(path -> path.getFileName().toString().endsWith(extension))
             .filter(path -> Arrays.stream(folderSuffixes)
                                   .map(suffix -> !path.getFileName().toString().endsWith(suffix))
                                   .reduce(true, (a, b) -> a && b))
             .filter(path -> {
                 if (ignoredFiles.containsKey(moduleName)) {
                     return Arrays.stream(ignoredFiles.get(moduleName))
                                  .map(suffix -> !path.getFileName().toString().equals(suffix))
                                  .reduce(true, (a, b) -> a && b);
                 } else {
                     return true;
                 }
             })
             .forEach(path -> {
                 String filename = path.getFileName().toString();
                 File in = path.toFile();
                 File out = path.resolveSibling(filename.replace(".xml", ".db")).toFile();

                 // XSLT transformation.
                 try {
                     transformer.transform(new StreamSource(in), new StreamResult(out));
                 } catch (TransformerException e) {
                     System.err.println("Problem(s) with file '" + path.getFileName().toFile() + "' at stage XSLT: \\n");
                     e.printStackTrace();
                 }
             });
    }
}
