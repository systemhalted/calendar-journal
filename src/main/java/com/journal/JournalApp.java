package com.journal;

import java.time.YearMonth;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * JavaFX application entry point. Wires the SQLite data layer and settings to the
 * calendar view and shows the main window.
 */
public class JournalApp extends Application {

    private JournalDao dao;
    private Settings settings;
    private Scene scene;

    @Override
    public void init() {
        String dbFile = AppPaths.databaseFile().toString();
        dao = new JournalDao(dbFile);
        dao.init();
        SettingsDao settingsDao = new SettingsDao(dbFile);
        settingsDao.init();
        settings = new Settings(settingsDao);
    }

    @Override
    public void start(Stage stage) {
        CalendarView view = new CalendarView(dao, settings, YearMonth.now());

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage, view));
        root.setCenter(view);

        scene = new Scene(root, 580, 560);
        settings.theme().applyTo(scene);
        stage.setTitle("Calendar Journal");
        stage.setScene(scene);
        stage.setMinWidth(460);
        stage.setMinHeight(460);
        stage.show();
    }

    private MenuBar buildMenuBar(Stage stage, CalendarView view) {
        MenuItem preferences = new MenuItem("Preferences…");
        preferences.setOnAction(e ->
                new PreferencesDialog(stage, settings, () -> {
                    settings.theme().applyTo(scene);   // re-apply theme live
                    view.refresh();
                }).showAndWait());

        MenuItem quit = new MenuItem("Quit");
        quit.setOnAction(e -> Platform.exit());

        Menu file = new Menu("File", null, preferences, new SeparatorMenuItem(), quit);

        MenuItem find = new MenuItem("Find…");
        find.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        find.setOnAction(e ->
                new SearchDialog(stage, dao, settings, view::refresh).showAndWait());

        Menu edit = new Menu("Edit", null, find);
        return new MenuBar(file, edit);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
