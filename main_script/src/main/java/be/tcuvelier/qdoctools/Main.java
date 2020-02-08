package be.tcuvelier.qdoctools;

import be.tcuvelier.qdoctools.cli.MainCommand;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import picocli.CommandLine;

/**
 * Goals of this package? Briefly: provide tooling around the DocBook format, in the context of a documentation website.
 * It also contains tools to deal more specifically with Qt's documentation.
 *
 * More concretely:
 *  - Transform documents, either one by one OR in batch
 *    File types are guessed from extensions (.xml for DvpML, .db, .dbk for DocBook), but can be overwritten.
 *    Output format should be given (by default: any to DocBook, DocBook to DOCX).
 *    - Import transformations:
 *      - DvpML <> DocBook (configuration: a JSON along the document)
 *    - Import and proofreading:
 *      - DOCX / ODT <> DocBook
 *  - Merging documents with differences
 *    - After proofreading: metadata gets lost
 *    - After a Qt update: get changes from the update, update an older translation (and indicate what changed)
 *  - Run qdoc (5.15+) to get DocBook
 *  - Upload documents to a given FTP, either one by one OR in batch
 *    Dvp's toolchains are used to get the files to upload.
 *
 *  All options to find qdoc and other tools are contained in a configuration file (config.json).
 *  Passwords are stored in a keyring (i.e. within the operating system).
 */

public class Main {
    public static void main(String[] args) {
//        String[] argv = {"qdoc", "-i", "C:\\Qt\\5.13.0\\Src", "-s", "C:\\Qt\\5.13.0\\mingw73_64", "-o", "C:\\Qt\\Doc513", "--qt-version", "5.13"};

//        String doc = "CPLEX";
//        String doc = "07-0-qdoc-commands-includingexternalcode";
//        String doc = "16-qdoc-commands-status";
        String doc = "C:\\Users\\Thibaut\\Documents\\GitHub\\Articles\\julia\\introduction-rapide-1.1\\julia.xml";
//        String doc = "C:\\Users\\Thibaut\\Documents\\GitHub\\Articles\\julia\\introduction-rapide-1.1\\julia.docx";
//        String doc = "C:\\Users\\Thibaut\\Documents\\GitHub\\Articles\\julia\\introduction-jump-0.20\\julia-jump.docx";
//        String doc = "C:\\Users\\Thibaut\\Desktop\\julia.docx";

//        String[] argv = {"merge",
//                "-l", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\merge_after_proofread\\tests\\" + doc + "_before.xml",
//                "-r", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\merge_after_proofread\\tests\\" + doc + "_after.xml",
//                "-m", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\merge_after_proofread\\tests\\" + doc + "_merged.xml"};
//        String[] argv = {"transform", "-i", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\proofread_todocx\\tests\\" + doc + ".xml"};
//        String[] argv = {"transform", "-i", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\proofread_todocx\\tests\\" + doc + ".db"};
//        String[] argv = {"transform", "-i", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\proofread_todocx\\tests\\" + doc + ".db", "--disable-sanity-checks"};
//        String[] argv = {"transform", "-i", "C:\\Users\\Thibaut\\Documents\\GitHub\\QtDocTools\\proofread\\proofread_fromdocx\\tests\\" + doc + ".docx"};

        String[] argv = {"transform", "-i", doc, "--no-validation"};

        submain(argv);
//        submain(args);
    }

    private static void submain(String[] argv) {
        CommandLine cl = new CommandLine(new MainCommand());
        cl.registerConverter(QtVersion.class, QtVersion::new);
        System.exit(cl.execute(argv));
    }
}
