package com.chess.tournament.ui;

import com.chess.tournament.ui.util.UiStyles;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Shared content-pane navigation for the main shell.
 */
public final class ContentNavigator {

    private static final Logger log = LoggerFactory.getLogger(ContentNavigator.class);

    private static StackPane contentPane;

    private ContentNavigator() {
    }

    public static void bind(StackPane pane) {
        contentPane = pane;
    }

    public static void show(Parent view) {
        if (contentPane == null) {
            throw new IllegalStateException("ContentNavigator is not bound");
        }
        UiStyles.apply(view);
        contentPane.getChildren().setAll(view);
    }

    public static void load(String fxmlClasspath) {
        load(fxmlClasspath, null);
    }

    public static <T> void load(String fxmlClasspath, Consumer<T> controllerConfigurer) {
        try {
            FXMLLoader loader = new FXMLLoader(ContentNavigator.class.getResource(fxmlClasspath));
            Parent view = loader.load();
            if (controllerConfigurer != null) {
                @SuppressWarnings("unchecked")
                T controller = (T) loader.getController();
                controllerConfigurer.accept(controller);
            }
            show(view);
        } catch (IOException e) {
            log.error("Failed to load {}", fxmlClasspath, e);
            throw new IllegalStateException("Failed to load " + fxmlClasspath + ": " + e.getMessage(), e);
        }
    }
}
