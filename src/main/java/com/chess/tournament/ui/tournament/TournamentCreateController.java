package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.ui.util.Alerts;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TournamentCreateController {

    private static final Logger log = LoggerFactory.getLogger(TournamentCreateController.class);

    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<TournamentType> typeCombo;
    @FXML
    private Spinner<Integer> roundsSpinner;
    @FXML
    private Spinner<Integer> qualifiersSpinner;
    @FXML
    private Label swissMethodLabel;
    @FXML
    private ComboBox<SwissFirstRoundMethod> swissMethodCombo;
    @FXML
    private Label hintLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private TournamentService tournamentService;
    private Tournament created;
    private boolean createdFlag;

    @FXML
    private void initialize() {
        tournamentService = AppContext.get().getTournamentService();
        typeCombo.setItems(FXCollections.observableArrayList(TournamentType.values()));
        typeCombo.getSelectionModel().select(TournamentType.SWISS);
        swissMethodCombo.setItems(FXCollections.observableArrayList(SwissFirstRoundMethod.values()));
        swissMethodCombo.getSelectionModel().select(SwissFirstRoundMethod.RANDOM);

        roundsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 5));
        qualifiersSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99, 0));

        typeCombo.valueProperty().addListener((obs, o, type) -> updateTypeUi(type));
        updateTypeUi(typeCombo.getValue());
    }

    private void updateTypeUi(TournamentType type) {
        boolean swiss = type == TournamentType.SWISS;
        swissMethodLabel.setVisible(swiss);
        swissMethodLabel.setManaged(swiss);
        swissMethodCombo.setVisible(swiss);
        swissMethodCombo.setManaged(swiss);

        if (type == TournamentType.ROUND_ROBIN) {
            hintLabel.setText("Round Robin: rounds must equal N−1 (even N) or N (odd N). Validated at start.");
            roundsSpinner.getValueFactory().setValue(3);
        } else if (type == TournamentType.KNOCKOUT) {
            hintLabel.setText("Knockout: rounds must equal ⌈log₂(N)⌉. Validated at start.");
            roundsSpinner.getValueFactory().setValue(2);
            qualifiersSpinner.getValueFactory().setValue(1);
        } else {
            hintLabel.setText("Swiss: choose first-round method. At least 2 players (4 recommended).");
            roundsSpinner.getValueFactory().setValue(5);
        }
    }

    public boolean isCreated() {
        return createdFlag;
    }

    public Optional<Tournament> getCreatedTournament() {
        return Optional.ofNullable(created);
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        TournamentType type = typeCombo.getValue();
        CreateTournamentCommand command = new CreateTournamentCommand(
                nameField.getText(),
                type,
                roundsSpinner.getValue(),
                qualifiersSpinner.getValue(),
                type == TournamentType.SWISS ? swissMethodCombo.getValue() : null);

        setBusy(true);
        Task<Tournament> task = new Task<>() {
            @Override
            protected Tournament call() {
                return tournamentService.create(command);
            }
        };
        task.setOnSucceeded(e -> {
            created = task.getValue();
            createdFlag = true;
            setBusy(false);
            close();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to create tournament", error);
            String message = error instanceof DomainException ? error.getMessage() : error.getMessage();
            Alerts.error("Create failed", message);
        });
        new Thread(task, "create-tournament").start();
    }

    private void setBusy(boolean busy) {
        saveButton.setDisable(busy);
        cancelButton.setDisable(busy);
        nameField.setDisable(busy);
        typeCombo.setDisable(busy);
        roundsSpinner.setDisable(busy);
        qualifiersSpinner.setDisable(busy);
        swissMethodCombo.setDisable(busy);
    }

    private void close() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}
