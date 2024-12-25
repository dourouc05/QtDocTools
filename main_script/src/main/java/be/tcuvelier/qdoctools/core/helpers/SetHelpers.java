package be.tcuvelier.qdoctools.core.helpers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    public static <T> List<T> union(List<T> a, List<T>... lb) {
        Stream<T> stream = a.stream();
        for (List<T> b : lb) {
            stream = Stream.concat(stream, b.stream());
        }
        // Don't use Stream::toList, as this list is immutable.
        return stream.distinct().collect(Collectors.toList());
    }

    @SafeVarargs
    public static <T> List<T> sortedUnion(List<T> a, List<T>... lb) {
        List<T> array = union(a, lb);
        array.sort(null);
        return array;
    }
}
