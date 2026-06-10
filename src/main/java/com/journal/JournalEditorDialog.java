package com.journal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The "notepad": a modal window for one day's journal entry, with an optional
 * title and a live word/character count. Changes autosave a short pause after you
 * stop typing, and again when the window closes, so text is never lost. A blank
 * title and content removes the entry (see {@link EntryAutosave}).
 */
public class JournalEditorDialog extends Stage {

    private static final DateTimeFormatter TITLE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final Duration AUTOSAVE_DELAY = Duration.millis(600);

    private final JournalDao dao;
    private final LocalDate date;
    private final TextField titleField = new TextField();
    private final TextArea textArea = new TextArea();
    private final Label status = new Label();
    private final Label count = new Label();
    private boolean deleted = false;

    public JournalEditorDialog(Window owner, JournalDao dao, Settings settings, LocalDate date) {
        this.dao = dao;
        this.date = date;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Journal — " + date.format(TITLE_FORMAT));

        Entry existing = dao.loadEntry(date);
        titleField.setPromptText("Title (optional)");
        titleField.setText(existing == null || existing.title() == null ? "" : existing.title());
        textArea.setText(existing == null ? "" : existing.content());
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: " + settings.editorFontSize() + "px;");

        status.setText(existing == null ? "" : "Saved");
        updateCount();

        // Debounced autosave: restart the timer on each keystroke; save when it settles.
        PauseTransition debounce = new PauseTransition(AUTOSAVE_DELAY);
        debounce.setOnFinished(e -> autosave());
        titleField.textProperty().addListener((obs, old, val) -> onEdit(debounce));
        textArea.textProperty().addListener((obs, old, val) -> {
            updateCount();
            onEdit(debounce);
        });

        Button delete = new Button("Delete");
        delete.setOnAction(e -> confirmDelete());

        Button close = new Button("Close");
        close.setDefaultButton(true);
        close.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(12, status, count, spacer, delete, close);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(8, titleField);
        VBox.setMargin(titleField, new Insets(0, 0, 4, 0));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(top);
        root.setCenter(textArea);
        root.setBottom(bottom);
        BorderPane.setMargin(textArea, new Insets(4, 0, 0, 0));
        BorderPane.setMargin(bottom, new Insets(10, 0, 0, 0));

        // Flush a final save on close (unless the entry was explicitly deleted).
        setOnHidden(e -> {
            debounce.stop();
            if (!deleted) {
                autosave();
            }
        });

        setScene(new Scene(root, 580, 480));
        settings.theme().applyTo(getScene());
    }

    private void onEdit(PauseTransition debounce) {
        status.setText("Editing…");
        debounce.playFromStart();
    }

    private void updateCount() {
        TextStats s = TextStats.of(textArea.getText());
        count.setText(s.words() + " words · " + s.chars() + " chars");
    }

    private void autosave() {
        EntryAutosave.persist(dao, date, titleField.getText(), textArea.getText());
        boolean empty = titleField.getText().isBlank() && textArea.getText().isBlank();
        status.setText(empty ? "" : "Saved");
    }

    private void confirmDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the journal entry for this day?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.initOwner(this);
        confirm.showAndWait()
                .filter(b -> b == ButtonType.YES)
                .ifPresent(b -> {
                    deleted = true;          // skip the on-close autosave
                    dao.delete(date);
                    close();
                });
    }
}
