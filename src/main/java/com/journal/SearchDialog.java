package com.journal;

import java.time.format.DateTimeFormatter;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Full-text search across all entries: a query box over a results list. Selecting
 * a result opens that day's entry; on return the search re-runs and the caller is
 * notified so the calendar can refresh.
 */
public class SearchDialog extends Stage {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");

    private final JournalDao dao;
    private final Settings settings;
    private final Runnable onEntryChanged;

    private final TextField queryField = new TextField();
    private final ListView<SearchHit> results = new ListView<>();

    public SearchDialog(Window owner, JournalDao dao, Settings settings, Runnable onEntryChanged) {
        this.dao = dao;
        this.settings = settings;
        this.onEntryChanged = onEntryChanged;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Search journal");

        queryField.setPromptText("Search entries…");

        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        debounce.setOnFinished(e -> runSearch());
        queryField.textProperty().addListener((obs, old, val) -> debounce.playFromStart());
        queryField.setOnAction(e -> {
            if (!results.getItems().isEmpty()) {
                results.getSelectionModel().select(0);
                openSelected();
            }
        });

        results.setPlaceholder(new Label("No matching entries"));
        results.setCellFactory(list -> new HitCell());
        results.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelected();
            }
        });
        results.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
            }
        });

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(queryField);
        root.setCenter(results);
        BorderPane.setMargin(results, new Insets(10, 0, 0, 0));

        setScene(new Scene(root, 460, 480));
        settings.theme().applyTo(getScene());
    }

    private void runSearch() {
        String match = SearchQuery.toMatch(queryField.getText());
        if (match.isEmpty()) {
            results.getItems().clear();
        } else {
            results.getItems().setAll(dao.searchHits(match));
        }
    }

    private void openSelected() {
        SearchHit hit = results.getSelectionModel().getSelectedItem();
        if (hit == null) {
            return;
        }
        new JournalEditorDialog(this, dao, settings, hit.date()).showAndWait();
        if (onEntryChanged != null) {
            onEntryChanged.run();
        }
        runSearch();   // reflect any edits/deletes in the results
    }

    /** Renders a result as a date + title line over a content snippet. */
    private static class HitCell extends ListCell<SearchHit> {
        @Override
        protected void updateItem(SearchHit hit, boolean empty) {
            super.updateItem(hit, empty);
            if (empty || hit == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String heading = hit.date().format(DATE_FORMAT);
            if (hit.title() != null && !hit.title().isBlank()) {
                heading += " — " + hit.title();
            }
            Label headingLabel = new Label(heading);
            headingLabel.setStyle("-fx-font-weight: bold;");
            Label snippetLabel = new Label(hit.snippet());
            snippetLabel.setStyle("-fx-opacity: 0.8;");
            snippetLabel.setWrapText(true);
            setGraphic(new VBox(2, headingLabel, snippetLabel));
        }
    }
}
