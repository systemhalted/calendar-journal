package com.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextStatsTest {

    @Test
    void emptyTextHasZeroWordsAndChars() {
        TextStats s = TextStats.of("");
        assertEquals(0, s.words());
        assertEquals(0, s.chars());
    }

    @Test
    void countsWordsAndCharacters() {
        TextStats s = TextStats.of("hello world");
        assertEquals(2, s.words());
        assertEquals(11, s.chars());
    }

    @Test
    void collapsesRunsOfWhitespaceWhenCountingWords() {
        TextStats s = TextStats.of("  a   b\n c ");
        assertEquals(3, s.words());
    }

    @Test
    void whitespaceOnlyHasZeroWordsButCountsChars() {
        TextStats s = TextStats.of("   ");
        assertEquals(0, s.words());
        assertEquals(3, s.chars());
    }
}
