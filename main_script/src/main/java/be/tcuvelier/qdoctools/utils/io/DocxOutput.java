package be.tcuvelier.qdoctools.utils.io;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DocxOutput {
    public static void main(String[] args) throws IOException {
        XWPFDocument doc = new XWPFDocument(new FileInputStream("template.docx"));

        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading1");
        p.createRun().setText("Heading1");

        XWPFTable t = doc.createTable(2, 3);
        t.getRow(0).getCell(0).setText("00");
        t.getRow(0).getCell(1).setText("01");
        t.getRow(0).getCell(2).setText("02");
        t.getRow(1).getCell(0).setText("10");
        t.getRow(1).getCell(1).setText("11");
        t.getRow(1).getCell(2).setText("12");

        FileOutputStream out = new FileOutputStream("test.docx");
        doc.write(out);
        out.close();
    }
}
