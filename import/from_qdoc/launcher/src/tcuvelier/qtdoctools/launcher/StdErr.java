package tcuvelier.qtdoctools.launcher;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

class StdErr {
    private static PrintStream stdErr = System.err;

    static void disableStdErr() {
        System.setErr(nullStream());
    }
    static void enableStdErr() {
        System.setErr(stdErr);
    }

    private static PrintStream nullStream() {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {}
        });
    }
}
