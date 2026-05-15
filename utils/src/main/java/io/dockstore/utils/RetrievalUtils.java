package io.dockstore.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class RetrievalUtils {

    private static final int PAGE_LIMIT = 100;

    private RetrievalUtils() {
    }

    /**
     * Retrieves up to {@code count} elements by repeatedly calling {@code retriever}
     * with successive page offsets and limits until {@code count} elements have been
     * collected or the lambda returns an empty page.
     *
     * @param retriever a function that accepts (offset, limit) and returns a page of elements
     * @param count the maximum number of elements to retrieve
     * @return collected elements, at most {@code count}
     */
    public static <T> List<T> pagedRetrieval(BiFunction<Integer, Integer, List<T>> retriever, int count) {
        List<T> results = new ArrayList<>();
        int offset = 0;
        while (results.size() < count) {
            int limit = Math.min(PAGE_LIMIT, count - results.size());
            List<T> page = retriever.apply(offset, limit);
            if (page.isEmpty()) {
                break;
            }
            results.addAll(page);
            offset += page.size();
        }
        return results;
    }
}
