package com.chess.tournament.ui.util;

import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Applies the shared application stylesheet to scenes and roots.
 */
public final class UiStyles {

    private static final String APP_CSS = "/css/app.css";

    private UiStyles() {
    }

    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        String url = UiStyles.class.getResource(APP_CSS).toExternalForm();
        if (!scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
    }

    public static void apply(Parent root) {
        if (root == null) {
            return;
        }
        String url = UiStyles.class.getResource(APP_CSS).toExternalForm();
        if (!root.getStylesheets().contains(url)) {
            root.getStylesheets().add(url);
        }
    }
}
