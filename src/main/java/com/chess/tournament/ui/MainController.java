package com.chess.tournament.ui;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.ui.util.Alerts;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Main shell controller — hosts navigation and loads feature screens into the content pane.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML
    private StackPane contentPane;

    @FXML
    private VBox homePane;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        contentPane.setId("contentPane");
        ContentNavigator.bind(contentPane);
        testConnection(false);
    }

    @FXML
    private void onHome() {
        ContentNavigator.show(homePane);
    }

    @FXML
    private void onPlayers() {
        ContentNavigator.load("/fxml/player_list.fxml");
    }

    @FXML
    private void onTournaments() {
        ContentNavigator.load("/fxml/tournament_list.fxml");
    }

    @FXML
    private void onTestConnection() {
        testConnection(true);
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

    private void testConnection(boolean showDialog) {
        statusLabel.setText("Checking database connection...");
        statusLabel.getStyleClass().removeAll("status-ok", "status-error");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                try (Connection connection = AppContext.get().getDataSource().getConnection();
                     Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT 1")) {
                    if (rs.next() && rs.getInt(1) == 1) {
                        return null;
                    }
                    throw new IllegalStateException("unexpected SELECT 1 result");
                }
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Connected to database");
            statusLabel.getStyleClass().add("status-ok");
            log.info("Database connectivity check succeeded");
            if (showDialog) {
                Alerts.info("Database connection", "Connection OK — SELECT 1 succeeded.");
            }
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            log.error("Database connectivity check failed", error);
            String message = "Database connection failed: " + error.getMessage()
                    + " — check CTMS_DB_* env vars or config/application.properties";
            statusLabel.setText(message);
            statusLabel.getStyleClass().add("status-error");
            if (showDialog) {
                Alerts.error("Database connection", message);
            }
        });
        new Thread(task, "db-connection-check").start();
    }
}
