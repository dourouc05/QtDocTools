package be.tcuvelier.qdoctools.io.docx.fromdocx;

import be.tcuvelier.qdoctools.io.docx.helpers.DocBookBlock;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class PreformattedMetadata {
    private final DocBookBlock type;
    private Optional<String> language;
    private Optional<Boolean> continuation;
    private Optional<Integer> linenumbering;
    private Optional<Integer> startinglinenumber;

    public PreformattedMetadata(@NotNull String p) throws XMLStreamException {
        String[] options = Arrays.stream(p.split("\\.")).map(String::strip).filter(Predicate.not(String::isEmpty)).toArray(String[]::new);

        // Parse the type.
        if (options[0].equals("Program listing")) {
            type = DocBookBlock.PROGRAM_LISTING;
        } else {
            throw new XMLStreamException("Unrecognised preformatted metadata block: " + options[0]);
        }

        // Prefill all fields.
        language = Optional.empty();
        continuation = Optional.empty();
        linenumbering = Optional.empty();
        startinglinenumber = Optional.empty();

        // Parse the rests, if there is anything left.
        for (int i = 1; i < options.length; ++i) {
            String[] option = Arrays.stream(options[i].split(":")).map(String::strip).filter(Predicate.not(String::isEmpty)).toArray(String[]::new);

            if (option[0].equalsIgnoreCase("Language")) {
                language = Optional.of(option[1]);
            } else if (option[0].equalsIgnoreCase("Continuation")) {
                continuation = Optional.of(Boolean.valueOf(option[1]));
            } else if (option[0].equalsIgnoreCase("Line numbering")) {
                linenumbering = Optional.of(Integer.parseInt(option[1]));
            } else if (option[0].equalsIgnoreCase("Starting line number")) {
                startinglinenumber = Optional.of(Integer.parseInt(option[1]));
            } else {
                throw new XMLStreamException("Unrecognised preformatted option: " + option[0]);
            }
        }
    }

    public PreformattedMetadata(@NotNull XWPFParagraph p) throws XMLStreamException {
        this(p.getText());
    }

    public DocBookBlock getType() {
        return type;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();

        language.ifPresent(s -> map.put("language", s));
        continuation.ifPresent(b -> map.put("continuation", b.toString()));
        linenumbering.ifPresent(i -> map.put("linenumbering", i.toString()));
        startinglinenumber.ifPresent(i -> map.put("startinglinenumber", i.toString()));

        return Map.copyOf(map);
    }
}
