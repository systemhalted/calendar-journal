package com.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryAutosaveTest {

    @TempDir
    Path tmp;

    private final LocalDate date = LocalDate.of(2026, 6, 9);

    private JournalDao freshDao() {
        JournalDao dao = new JournalDao(tmp.resolve("journal.db").toString());
        dao.init();
        return dao;
    }

    @Test
    void nonBlankContentIsSavedWithTitle() {
        JournalDao dao = freshDao();
        EntryAutosave.persist(dao, date, "A Title", "a thought");
        Entry e = dao.loadEntry(date);
        assertEquals("A Title", e.title());
        assertEquals("a thought", e.content());
    }

    @Test
    void titleAloneKeepsTheEntry() {
        JournalDao dao = freshDao();
        EntryAutosave.persist(dao, date, "Just a title", "");
        assertNotNull(dao.loadEntry(date));
    }

    @Test
    void bothBlankRemovesExistingEntry() {
        JournalDao dao = freshDao();
        dao.saveEntry(date, "t", "to be cleared");
        EntryAutosave.persist(dao, date, "", "");
        assertNull(dao.loadEntry(date));
    }

    @Test
    void whitespaceOnlyBothCountsAsBlank() {
        JournalDao dao = freshDao();
        dao.saveEntry(date, "t", "c");
        EntryAutosave.persist(dao, date, "  ", "  \n ");
        assertNull(dao.loadEntry(date));
    }

    @Test
    void blankOnAbsentEntryDoesNotCreateOne() {
        JournalDao dao = freshDao();
        EntryAutosave.persist(dao, date, "", "");
        assertNull(dao.loadEntry(date));
    }
}
