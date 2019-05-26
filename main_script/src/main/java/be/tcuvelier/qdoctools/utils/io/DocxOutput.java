package be.tcuvelier.qdoctools.utils.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

public class DocxOutput {
    /*
    * How to make a new template? Basically, use every style available in Word. This ensures that no style that is
    * used by this script remains "latent", in OpenXML terminology.
    * You must also define some new styles:
    *   - for segmented lists (which will always be shown as textual lists!): "Definition List Title" and
    *     "Definition List Item"
    *   - for variable lists (which will always be shown as textual lists): "Variable List Title" and
    *     "Variable List Item". Graphically, those styles should resemble segmented lists (they are distinct so that
    *     round-tripping is possible)
    */

    public static void main(String[] args) throws Exception {
//        String test = "basic";
//        String test = "sections";
//        String test = "images";
        String test = "lists";

        new DocxOutput(MainCommand.toDocxTests + "synthetic/" + test + ".xml")
                .toDocx(MainCommand.toDocxTests + "synthetic/" + test + ".docx");
    }

    private String input;

    public DocxOutput(String input) {
        this.input = input;
    }

    public void toDocx(String output) throws IOException, ParserConfigurationException, SAXException {
        try (FileOutputStream out = new FileOutputStream(output)) {
            toDocx().write(out);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public XWPFDocument toDocx() throws IOException, ParserConfigurationException, SAXException {
        SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
        DocxOutputImpl handler = new DocxOutputImpl(Paths.get(input).getParent());
        saxParser.parse(new File(input), handler);
        return handler.doc;
    }
}
