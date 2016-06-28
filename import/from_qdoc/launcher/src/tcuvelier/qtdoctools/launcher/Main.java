package tcuvelier.qtdoctools.launcher;


import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class Main {
    // Saxon HE: run with -Djava.xml.transform.TransformerFactory=net.sf.saxon.TransformerFactoryImpl.
    // Saxon PE: run with -Djava.xml.transform.TransformerFactory=com.saxonica.config.ProfessionalTransformerFactory.
    // Saxon EE: run with -Djava.xml.transform.TransformerFactory=com.saxonica.config.EnterpriseTransformerFactory.

    private static final String extension = ".xml"; // Extension for files that are recognised here.
    private static final String[] forbiddenSuffixes = { "-members", "-compat", "-obsolete" };
    private static final String[] ignoredSuffixes = { "-manifest" };

    // Arguments: XSLT file, folder.
    public static void main(String[] args) throws TransformerConfigurationException, IOException {
        args = new String[] { ".xsl", "F:\\QtDoc\\output\\html\\qtgui"};

        // Parse the command line.
        if (args.length != 2) {
            System.err.println("Usage: xsl folder");
        }
        String xslt = args[0];
        String dir = args[1];
        if (! xslt.endsWith(".xsl")) {
            System.err.println("Unrecognised style sheet extension: " + xslt);
        }

        // Compile the style sheet.
//        Templates templates = TransformerFactory.newInstance().newTemplates(new StreamSource(xslt));
//        Transformer transformer = templates.newTransformer();

        // Iterate through the given folder.
//        File folder = new File(dir);
//        File[] listing = folder.listFiles();
//        if (listing == null) {
//            throw new FileNotFoundException("The given folder has no file: " + dir);
//        }

        String[] folderSuffixes = Stream.concat(Arrays.stream(forbiddenSuffixes), Arrays.stream(ignoredSuffixes))
                                        .map(s -> s + extension)
                                        .toArray(String[]::new);
        Files.walk(Paths.get(dir))
             .filter(Files::isRegularFile)
             .filter(path -> path.getFileName().toString().endsWith(extension))
             .filter(path -> Arrays.stream(folderSuffixes)
                                   .map(suffix -> !path.getFileName().toString().endsWith(suffix))
                                   .reduce(true, (a, b) -> a && b))
             .forEach(path -> {
                 System.out.println(path);;
             });

//        for (File child : listing) {
//            String name = child.getName();
//            if (! name.endsWith(extension)) {
//                continue;
//            }
//        }

        /*
        count_db = 0

        for root, sub_dirs, files in os.walk(self.folders['output'] + module_name + '/'):
            if root.endswith('/style') or root.endswith('/scripts') or root.endswith('/images'):
                continue

            count = 0
            n_files = len(files)
            for file in files:
                count += 1

                # Handle a bit of output (even though the DocBook counter is not yet updated for this iteration).
                if count % 10 == 0:
                    logging.info('XML to DocBook: module %s, %i files done out of %i (%i DocBook files generated)'
                                 % (module_name, count, n_files, count_db))

                # Avoid lists of examples (-manifest.xml) and files automatically included within the output
                # with the XSLT stylesheet (-members.xml, -obsolete.xml, -compat.xml).
                if not file.endswith(ext):
                    continue
                if any([file.endswith(fs + ext) for fs in ignored_suffixes]):
                    continue
                if any([file.endswith(fs + ext) for fs in forbidden_suffixes]):
                    continue
                if module_name in self.ignores['files'] and file in self.ignores['files'][module_name]:
                    continue
                count_db += 1

                # Actual processing.
                base_file_name = os.path.join(root, file[:-4])
                in_file_name = base_file_name + '.xml'
                out_file_name = base_file_name + '.db'
                self._call_xslt(in_file_name, out_file_name)

                # For C++ classes, also handle the function prototypes with the C++ application.
                if file.startswith('q') and not file.startswith('qml-'):
                    self._call_cpp_parser(out_file_name, out_file_name)

        return count_db
         */

//        System.out.println("\n\n----- transform of " + sourceID1 + " -----");
//        transformer.transform(new StreamSource(sourceID1),
//                new StreamResult(System.out));
//        System.out.println("\n\n----- transform of " + sourceID2 + " -----");
//        transformer.transform(new StreamSource(sourceID2),
//                new StreamResult(System.out));
    }
}
