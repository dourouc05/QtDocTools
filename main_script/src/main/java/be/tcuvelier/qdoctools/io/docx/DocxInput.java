package be.tcuvelier.qdoctools.io.docx;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class DocxInput {
//    public static void main(String[] args) throws IOException, XMLStreamException {
//        String test = "synthetic/basic";
//        String test = "synthetic/sections";
//        String test = "synthetic/sections_bogus";
//        String test = "synthetic/images_captioned";
//        String test = "synthetic/images";
//        String test = "synthetic/lists";
//        String test = "synthetic/lists_medium";
//        String test = "synthetic/lists_nested";
//        String test = "synthetic/lists_hard";
//        String test = "synthetic/lists_horror";
//        String test = "synthetic/preformatted";
//        String test = "synthetic/formatting_stack_minimum";
//        String test = "synthetic/formatting_stack";
//        String test = "synthetic/formatting_filename_replaceable";
//        String test = "synthetic/formatting_stack_styleid";
//        String test = "synthetic/formatting_link_underline";
//        String test = "synthetic/programlisting_one";
//        String test = "synthetic/programlisting_many";
//        String test = "synthetic/admonitions";
//        String test = "synthetic/admonitions_many";
//        String test = "synthetic/book";
//        String test = "synthetic/book_abstract";
//        String test = "synthetic/footnote";
//
//        String test = "CPLEX";
//
//        String docBook = new DocxInput(MainCommand.fromDocxTests + "synthetic/" + test + "
//        .docx").toDocBook();
//        System.out.println(docBook);
////        Files.write(Paths.get(MainCommand.fromDocxTests + "synthetic/" + test + ".xml"),
// docBook.getBytes());
//
//        new DocxInput(MainCommand.fromDocxTests + test + ".docx").toDocBook(MainCommand
//        .fromDocxTests + test + ".xml");
//    }

    private final DocxInputImpl impl;

    public DocxInput(String filename) throws IOException, XMLStreamException {
        impl = new DocxInputImpl(filename);
    }

    public void toDocBook(String output) throws IOException, XMLStreamException {
        Path outputPath = Paths.get(output);

        // Deal with XML.
        Files.write(outputPath, toDocBook().getBytes());

        // Deal with images.
        Path folder = outputPath.getParent();
        for (Map.Entry<String, byte[]> entry : impl.getImages().entrySet()) {
            Files.write(folder.resolve(entry.getKey()), entry.getValue());
        }
    }

    @SuppressWarnings("WeakerAccess")
    public String toDocBook() throws IOException, XMLStreamException {
        return impl.toDocBook();
    }

    @SuppressWarnings("unused")
    public Map<String, byte[]> getImages() {
        return impl.getImages();
    }
}
