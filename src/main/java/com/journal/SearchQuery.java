package com.journal;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds a safe FTS5 MATCH expression from free-form user input: each word becomes
 * a prefix term, and punctuation that could break FTS syntax is dropped. This
 * gives forgiving, incremental "search as you type" behavior.
 */
public final class SearchQuery {

    private SearchQuery() {
    }

    /** @return an FTS MATCH string like {@code "kayak* lake*"}, or empty if no words. */
    public static String toMatch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return Arrays.stream(input.trim().split("\\W+"))
                .filter(w -> !w.isEmpty())
                .map(w -> w + "*")
                .collect(Collectors.joining(" "));
    }
}
