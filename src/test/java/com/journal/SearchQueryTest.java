package com.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void buildsPrefixTermForEachWord() {
        assertEquals("kayak* lake*", SearchQuery.toMatch("kayak lake"));
    }

    @Test
    void stripsPunctuationThatWouldBreakFts() {
        assertEquals("a* b* c*", SearchQuery.toMatch("a-b.c"));
    }

    @Test
    void emptyOrWhitespaceYieldsEmpty() {
        assertEquals("", SearchQuery.toMatch(""));
        assertEquals("", SearchQuery.toMatch("   "));
        assertEquals("", SearchQuery.toMatch(null));
    }
}
