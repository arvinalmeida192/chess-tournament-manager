package com.chess.tournament.ui;

import com.chess.tournament.bootstrap.AppContext;
import javafx.application.Platform;
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
        try (Connection connection = AppContext.get().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            if (rs.next() && rs.getInt(1) == 1) {
                statusLabel.setText("Connected to database");
                statusLabel.getStyleClass().removeAll("status-error");
                statusLabel.getStyleClass().add("status-ok");
                log.info("Database connectivity check succeeded");
            } else {
                statusLabel.setText("Database check failed: unexpected SELECT 1 result");
                statusLabel.getStyleClass().add("status-error");
            }
        } catch (Exception e) {
            log.error("Database connectivity check failed", e);
            statusLabel.setText("Database connection failed: " + e.getMessage()
                    + " — check CTMS_DB_* env vars or config/application.properties");
            statusLabel.getStyleClass().add("status-error");
        }
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
    private void onExit() {
        Platform.exit();
    }
}
