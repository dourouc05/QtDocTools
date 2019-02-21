package be.tcuvelier.qdoctools.utils.helpers;

import java.util.HashSet;
import java.util.Set;

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
        for (Set<T> b: lb) {
            result.addAll(b);
        }
        return result;
    }

    public static <T> boolean compareSets(Set<T> a, Set<T> b) {
        return a.size() == b.size() && difference(a, b).size() == 0;
    }
}
