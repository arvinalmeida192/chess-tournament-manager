package com.chess.tournament.ui.player;

import com.chess.tournament.domain.Player;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.PlayerService;
import com.chess.tournament.ui.util.Alerts;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerFormController {

    private static final Logger log = LoggerFactory.getLogger(PlayerFormController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField ageField;
    @FXML
    private TextField countryField;
    @FXML
    private TextField ratingField;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private PlayerService playerService;
    private Player existing;
    private boolean saved;

    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setExisting(Player existing) {
        this.existing = existing;
        if (existing != null) {
            titleLabel.setText("Edit Player");
            nameField.setText(existing.getName());
            ageField.setText(existing.getAge() == null ? "" : String.valueOf(existing.getAge()));
            countryField.setText(existing.getCountry() == null ? "" : existing.getCountry());
            ratingField.setText(String.valueOf(existing.getGlobalRating()));
        } else {
            titleLabel.setText("Add Player");
            ratingField.setPromptText("Default 1500");
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        String name = nameField.getText();
        Integer age;
        Integer rating;
        try {
            age = parseOptionalInt(ageField.getText(), "Age");
            rating = parseOptionalInt(ratingField.getText(), "Rating");
        } catch (ValidationException e) {
            Alerts.error("Validation", e.getMessage());
            return;
        }

        String country = countryField.getText();
        setBusy(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (existing == null) {
                    playerService.create(name, age, country, rating);
                } else {
                    int ratingValue = rating == null ? existing.getGlobalRating() : rating;
                    playerService.update(existing.getId(), name, age, country, ratingValue);
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            saved = true;
            setBusy(false);
            close();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to save player", error);
            String message = error instanceof DomainException
                    ? error.getMessage()
                    : "Could not save player: " + error.getMessage();
            Alerts.error("Save failed", message);
        });
        new Thread(task, "save-player").start();
    }

    private static Integer parseOptionalInt(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldLabel + " must be a whole number");
        }
    }

    private void setBusy(boolean busy) {
        saveButton.setDisable(busy);
        cancelButton.setDisable(busy);
        nameField.setDisable(busy);
        ageField.setDisable(busy);
        countryField.setDisable(busy);
        ratingField.setDisable(busy);
    }

    private void close() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}
