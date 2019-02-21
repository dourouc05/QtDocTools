package be.tcuvelier.qdoctools;

import be.tcuvelier.qdoctools.cli.MainCommand;
import be.tcuvelier.qdoctools.utils.QtVersion;
import picocli.CommandLine;

/**
 * Goals of this package?
 *  - Transform documents, either one by one OR by batch (for Qt's documentation only)
 *      - DvpML <> DocBook (configuration: a JSON along the document)
 *    File types are guessed from extensions (.xml for DvpML, .db, .dbk, or .qdt for DocBook, .webxml for WebXML).
 *    Single-shot transformations from WebXML are not available outside qdoc mode, due to the requirements of the
 *    transformation (the utilities sheet must be run before).
 *  - Run qdoc and the associated transformations (for Qt's documentation only)
 *      - Only qdoc to WebXML
 *      - From qdoc to DocBook
 *  - More documentation-oriented things (like seeing what has changed between two versions and applying the same
 *    changes to a translated copy).
 *
 *  All options to find qdoc and related tools are contained in a configuration file.
 */

public class Main {

    public static void main(String[] args) {
//        String[] argv = {"qdoc", "-i", "D:\\Qt\\5.12.0\\Src", "-o", "D:\\Qt\\Doc511v2", "--qt-version", "5.12", "--no-rewrite-qdocconf", "--no-convert-webxml"};

        String[] argv = {"merge",
                "-l", "D:\\Thibaut\\Dvp\\QtDoc\\QtDocTools\\proofread\\merge_after_proofread\\CPLEX_before.xml",
                "-r", "D:\\Thibaut\\Dvp\\QtDoc\\QtDocTools\\proofread\\merge_after_proofread\\CPLEX_after.xml",
                "-m", "D:\\Thibaut\\Dvp\\QtDoc\\QtDocTools\\proofread\\merge_after_proofread\\CPLEX_merged.xml"};
//        String[] argv = {"proofread", "-i", "D:\\Thibaut\\Dvp\\QtDoc\\QtDocTools\\proofread\\proofread_fromdocx\\tests\\CPLEX.docx"};
//        String[] argv = {"proofread", "-i", "D:\\Thibaut\Dvp\\QtDoc\\QtDocTools\\proofread\\proofread_todocx\\tests\\CPLEX.db"};

        CommandLine cl = new CommandLine(new MainCommand());
        cl.registerConverter(QtVersion.class, QtVersion::new);
        cl.parseWithHandler(new CommandLine.RunAll(), argv);
    }
}
