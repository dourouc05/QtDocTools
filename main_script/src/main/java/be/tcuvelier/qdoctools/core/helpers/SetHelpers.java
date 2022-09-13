package be.tcuvelier.qdoctools.core.helpers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class SetHelpers {
    public static <T> Set<T> setDifference(Set<T> a, Set<T> b) {
        Set<T> difference = new HashSet<>(a);
        difference.removeAll(b);
        return difference;
    }

    public static <T> Set<T> difference(Set<T> a, Set<T> b) {
        Set<T> forward = setDifference(a, b);
        forward.addAll(setDifference(b, a));
        return forward;
    }

    @SafeVarargs
    public static <T> Set<T> union(Set<T> a, Set<T>... lb) {
        Set<T> result = new HashSet<>(a);
        for (Set<T> b : lb) {
            result.addAll(b);
        }
        return result;
    }

    @SafeVarargs
    public static <T> T[] union(T[] a, T[]... lb) {
        Stream<T> stream = Arrays.stream(a);
        for (T[] b : lb) {
            stream = Stream.concat(stream, Arrays.stream(b));
        }
        //noinspection unchecked
        return (T[]) stream.distinct().toArray();
    }

    @SafeVarargs
    public static <T> T[] sortedUnion(T[] a, T[]... lb) {
        T[] array = union(a, lb);
        Arrays.sort(array);
        return array;
    }

    public static <T> boolean compareSets(Set<T> a, Set<T> b) {
        return a.size() == b.size() && difference(a, b).size() == 0;
    }
}
