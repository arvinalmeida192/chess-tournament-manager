package com.chess.tournament.ui.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Modal Stage helpers that stay in front of their owner window.
 */
public final class DialogStages {

    private DialogStages() {
    }

    public static void showModal(Parent root, String title, Window owner) {
        showModal(root, title, owner, true);
    }

    public static void showModal(Parent root, String title, Window owner, boolean resizable) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setResizable(resizable);
        Scene scene = new Scene(root);
        UiStyles.apply(scene);
        dialog.setScene(scene);
        dialog.setOnShown(e -> {
            dialog.setAlwaysOnTop(true);
            dialog.toFront();
            dialog.requestFocus();
            dialog.setAlwaysOnTop(false);
            dialog.toFront();
        });
        dialog.showAndWait();
    }
}
