package com.journal;

import java.time.LocalDate;

/**
 * A single full-text search result: the entry's date, its title (may be
 * {@code null}), and a snippet of matching content.
 */
public record SearchHit(LocalDate date, String title, String snippet) {
}
