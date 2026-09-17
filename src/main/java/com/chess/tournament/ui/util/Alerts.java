package com.chess.tournament.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Shared JavaFX dialog helpers. Always owned by the focused/frontmost window
 * so alerts appear on top of the app (including nested modal stages).
 */
public final class Alerts {

    private Alerts() {
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message, ButtonType.OK);
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message, ButtonType.OK);
    }

    public static boolean confirm(String title, String message) {
        Alert alert = build(Alert.AlertType.CONFIRMATION, title, message, ButtonType.YES, ButtonType.NO);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private static void show(Alert.AlertType type, String title, String message, ButtonType... buttons) {
        build(type, title, message, buttons).showAndWait();
    }

    private static Alert build(Alert.AlertType type, String title, String message, ButtonType... buttons) {
        Alert alert = new Alert(type, message, buttons);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initModality(Modality.APPLICATION_MODAL);
        Window owner = ownerWindow();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setOnShown(e -> bringToFront(alert));
        return alert;
    }

    private static void bringToFront(Alert alert) {
        Window window = alert.getDialogPane().getScene() != null
                ? alert.getDialogPane().getScene().getWindow()
                : null;
        if (window instanceof Stage stage) {
            stage.setAlwaysOnTop(true);
            stage.toFront();
            stage.requestFocus();
            // Drop always-on-top after focus so it doesn't stick above other apps forever
            stage.setAlwaysOnTop(false);
            stage.toFront();
        }
    }

    /**
     * Prefer the focused window, then any modal stage, then any showing window.
     */
    static Window ownerWindow() {
        Window focused = null;
        Window modal = null;
        Window any = null;
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) {
                continue;
            }
            if (any == null) {
                any = window;
            }
            if (window.isFocused()) {
                focused = window;
            }
            if (window instanceof Stage stage && stage.getModality() != Modality.NONE && modal == null) {
                modal = window;
            }
        }
        if (focused != null) {
            return focused;
        }
        if (modal != null) {
            return modal;
        }
        return any;
    }
}
