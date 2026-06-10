package com.journal;

/**
 * Word and character counts for a block of text. Words are runs of non-whitespace.
 */
public record TextStats(int words, int chars) {

    public static TextStats of(String text) {
        if (text == null || text.isEmpty()) {
            return new TextStats(0, 0);
        }
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        return new TextStats(words, text.length());
    }
}
