package be.tcuvelier.qdoctools.io.helpers;

import java.util.List;

public class DocBookBlock {
    public static final List<Pair<String, String>> preformatted = List.of(
            new Pair<>("programlisting", "ProgramListing"),
            new Pair<>("screen", "Screen"),
            new Pair<>("synopsis", "Synopsis"),
            new Pair<>("literallayout", "LiteralLayout")
    );
}
