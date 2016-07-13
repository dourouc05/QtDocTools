package tcuvelier.qtdoctools.launcher;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.trans.XPathException;

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
        args = new String[] { "F:\\QtDoc\\QtDoc\\QtDocTools\\import\\from_qdoc\\xslt\\qdoc2db_5.4.xsl", "F:\\QtDoc\\output\\html\\qtquick", "true"};

        // Parse the command line.
        if (args.length < 2 || args.length > 3) {
            throw new RuntimeException("Usage: xsl folder [true|false for error recovery]");
        }

        String xslt = args[0];
        String dir = args[1];
        Path folder = Paths.get(dir);
        String moduleName = folder.getFileName().toString();
        boolean errorRecovery = (args.length > 2) && Boolean.parseBoolean(args[2]);

        if (! xslt.endsWith(".xsl") || xslt.length() == 4) {
            throw new RuntimeException("Unrecognised style sheet name: " + xslt);
        }
        if (! Files.exists(Paths.get(xslt))) {
            throw new IOException("Style sheet does not exist: " + xslt);
        }
        if (! Files.exists(folder)) {
            throw new IOException("Input folder does not exist: " + dir);
        }

        // Deal with the standard error, to avoid Saxon outputting anything when this code deals with the error.
        PrintStream stderr = System.err;
        System.setErr(NullOutputStream.nullStream());

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
                     if (errorRecovery) {
                         // Terminated by <xsl:message>.
                         if (e instanceof TerminationException) {
                             TerminationException te = (TerminationException) e;
                             if (te.getErrorCodeLocalPart().equals("XTMM9000")) {
                                 /* Identifiers occurring multiple times: rewrite the next occurrences. */

                                 // Algorithm: remember the number of times each ID was ever seen in the document; if it is higher
                                 // than two, then rewrite the line containing the ID. Pay attention to the fact that those IDs are
                                 // sometimes duplicated, i.e. present at the same time within a <a name> tag and
                                 // within the title <h? id> tag.
                                 //         If there are multiple <html:a>, they have the same ID and lie on the same line
                                 // (qtquick-cppextensionpoints).
                                 // TODO: Check the message from the XSLT! "ERROR: Some ids are not unique!"
                                 try {
                                     Map<String, MutableInt> aSeen = new HashMap<>(); // Number of times this ID was seen for a <a name=""> tag.
                                     Map<String, MutableInt> hSeen = new HashMap<>(); // Number of times this ID was seen for a <h? id="">  tag.

                                     Pattern idRegex = Pattern.compile("id=\"(.*)\"|name=\"(.*)\"");
                                     Stream<String> outputLines = Files.lines(in.toPath()).map(line -> {
                                         // Detect an identifier.
                                         if (line.contains("<html:a name=\"")
                                                 || (line.contains("<html:h") && line.contains("id=\""))) {
                                             Matcher matcher = idRegex.matcher(line);
                                             if (! matcher.find()) { // Force to allow matches after the beginning of the line, hence mandatory call to find().
                                                 throw new AssertionError("Could not find an identifier in line '" + line + "'.");
                                             }
                                             String foundId = matcher.group().split("\"")[1];

                                             // Count this occurrence in what has been seen.
                                             if (line.contains("<html:a")) {
                                                 if (aSeen.get(foundId) == null) {
                                                     aSeen.put(foundId, new MutableInt());
                                                 } else {
                                                     aSeen.get(foundId).increment();
                                                 }
                                             } else {
                                                 if (hSeen.get(foundId) == null) {
                                                     hSeen.put(foundId, new MutableInt());
                                                 } else {
                                                     hSeen.get(foundId).increment();
                                                 }
                                             }

                                             // Rewrite the line if need be.
                                             int increment = Math.max(
                                                     aSeen.getOrDefault(foundId, MutableInt.zero).get(),
                                                     hSeen.getOrDefault(foundId, MutableInt.zero).get()
                                             );
                                             if (increment >= 2) {
                                                 return line.replace(foundId, foundId + "-" + increment);
                                             } else {
                                                 return line;
                                             }
                                         } else {
                                             return line;
                                         }
                                     });
                                     String newContents = outputLines.collect(Collectors.joining("\n"));
                                     try (PrintWriter pw = new PrintWriter(in)) {
                                         pw.write(newContents);
                                     }
                                 } catch (IOException e1) {
                                     // Cannot happen: file existence already checked by Saxon!
                                     e1.printStackTrace();
                                 }
                             }
                             // No else: if it is another error message from the XSLT, then it is not corrected, and
                             // caught by the next try.
                         }
                         // Terminated by the XML parser.
                         else if (e instanceof XPathException) {
                             XPathException xe = (XPathException) e;
                             if (xe.getErrorCodeLocalPart().equals("SXXP0003")) {
                                 // One-line comments about the function operator--; remove all one-line comments.
                                 if (xe.getMessage().contains("The string \"--\" is not permitted within comments.")) {
                                     try {
                                         Stream<String> outputLines = Files.lines(in.toPath()).map(line -> {
                                             String chomped = line.trim();
                                             if (chomped.startsWith("<!--") && chomped.endsWith("-->")) {
                                                 return "";
                                             } else {
                                                 return line;
                                             }
                                         });
                                         String newContents = outputLines.collect(Collectors.joining("\n"));
                                         try (PrintWriter pw = new PrintWriter(in)) {
                                             pw.write(newContents);
                                         }
                                     } catch (IOException e1) {
                                         e1.printStackTrace();
                                     }
                                 }
                                 // Invalid characters happening in the XML files (i.e. binary output within the doc!).
                                 else if (xe.getMessage().contains("An invalid XML character")) {
                                     try {
                                         Pattern idRegex = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\uD7FF\\uE000-\\uFFFD\\u10000-\\u10FFF]");
                                         Stream<String> outputLines = Files.lines(in.toPath()).map(line -> idRegex.matcher(line).replaceAll(""));
                                         String newContents = outputLines.collect(Collectors.joining("\n"));
                                         try (PrintWriter pw = new PrintWriter(in)) {
                                             pw.write(newContents);
                                         }
                                     } catch (IOException e1) {
                                         e1.printStackTrace();
                                     }
                                 }
                                 // Otherwise: unknown error!
                                 else {
                                     throw new RuntimeException(e);
                                 }
                             }
                         }
                         // Otherwise: unknown error!
                         else {
                             throw new RuntimeException(e);
                         }

                        // Restore the outputs and retry.
                         try {
                             System.setErr(stderr);
                             transformer.transform(new StreamSource(in), new StreamResult(out));
                         } catch (TransformerException e1) {
                             System.err.println("Problem(s) with file '" + path.getFileName().toFile() + "' at stage XSLT: \n");
                             e1.printStackTrace();
                         } finally {
                             System.setErr(NullOutputStream.nullStream());
                         }
                     } else {
                         System.err.println("Problem(s) with file '" + path.getFileName().toFile() + "' at stage XSLT: \n");
                         e.printStackTrace();
                     }
                 }
             });
    }

    private static class MutableInt {
        private int value = 1; // Starts at 1 when created.
        private void increment() { value += 1; }
        private int get() { return value; }

        private static MutableInt zero = new MutableInt() {
            private int get() {
                return 0;
            }
        };
    }

    private static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {}

        private static PrintStream nullStream() {
            return new PrintStream(new NullOutputStream());
        }
    }
}
