package be.tcuvelier.qdoctools.io.docx.helpers;

public class Tuple<T, U> {
    public final T first;
    public final U second;

    public Tuple(T t, U u) {
        first = t;
        second = u;
    }
}
