package com.chess.tournament.bootstrap;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point for the Chess Tournament Management System.
 */
public class ChessTournamentApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(ChessTournamentApp.class);

    private AppContext appContext;

    @Override
    public void init() {
        appContext = AppContext.initialize();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("Chess Tournament Manager");
        primaryStage.setScene(new Scene(root, 1024, 768));
        primaryStage.show();
        log.info("Main window shown");
    }

    @Override
    public void stop() {
        if (appContext != null) {
            appContext.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
