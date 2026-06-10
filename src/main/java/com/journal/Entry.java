package com.journal;

/**
 * A journal entry's editable fields. {@code title} may be {@code null}.
 */
public record Entry(String title, String content) {
}
