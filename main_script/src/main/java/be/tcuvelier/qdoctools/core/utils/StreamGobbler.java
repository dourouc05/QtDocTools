package be.tcuvelier.qdoctools.core.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

// From https://stackoverflow.com/a/33386692/1066843
public class StreamGobbler implements Runnable {
    private InputStream inputStream;
    private Consumer<String> consumeInputLine;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumeInputLine) {
        this.inputStream = inputStream;
        this.consumeInputLine = consumeInputLine;
    }

    public StreamGobbler(InputStream inputStream, List<Consumer<String>> consumeInputLine) {
        this.inputStream = inputStream;
        this.consumeInputLine = (String r) -> consumeInputLine.forEach(c -> c.accept(r));
    }

    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumeInputLine);
    }
}
