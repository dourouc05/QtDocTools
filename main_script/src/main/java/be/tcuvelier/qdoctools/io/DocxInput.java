package be.tcuvelier.qdoctools.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import be.tcuvelier.qdoctools.io.helpers.DocBookFormatting;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class DocxInput {
    public static void main(String[] args) throws IOException, XMLStreamException {
//        String test = "basic";
//        String test = "sections";
//        String test = "sections_bogus";
//        String test = "images";
//        String test = "lists";
        String test = "preformatted";

//        String docBook = new DocxInput(MainCommand.fromDocxTests + "synthetic/" + test + ".docx").toDocBook();
//        System.out.println(docBook);
////        Files.write(Paths.get(MainCommand.fromDocxTests + "synthetic/" + test + ".xml"), docBook.getBytes());

        new DocxInput(MainCommand.fromDocxTests + "synthetic/" + test + ".docx").toDocBook(MainCommand.fromDocxTests + "synthetic/" + test + ".xml");
    }

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
        for (Map.Entry<String, byte[]> entry: impl.getImages().entrySet()) {
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
