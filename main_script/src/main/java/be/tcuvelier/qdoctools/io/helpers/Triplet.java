package be.tcuvelier.qdoctools.io.helpers;

public class Triplet<T, U, V> {
    public final T first;
    public final U second;
    public final V third;

    Triplet (T t, U u, V v) {
        first = t;
        second = u;
        third = v;
    }
}
