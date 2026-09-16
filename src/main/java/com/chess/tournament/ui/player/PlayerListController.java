package com.chess.tournament.ui.player;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.domain.Player;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.PlayerService;
import com.chess.tournament.ui.util.Alerts;
import com.chess.tournament.ui.util.UiStyles;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class PlayerListController {

    private static final Logger log = LoggerFactory.getLogger(PlayerListController.class);

    @FXML
    private TableView<Player> playerTable;
    @FXML
    private TableColumn<Player, Long> idColumn;
    @FXML
    private TableColumn<Player, String> nameColumn;
    @FXML
    private TableColumn<Player, Integer> ageColumn;
    @FXML
    private TableColumn<Player, String> countryColumn;
    @FXML
    private TableColumn<Player, Integer> ratingColumn;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deactivateButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Label statusLabel;

    private PlayerService playerService;

    @FXML
    private void initialize() {
        playerService = AppContext.get().getPlayerService();
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        ageColumn.setCellValueFactory(new PropertyValueFactory<>("age"));
        countryColumn.setCellValueFactory(new PropertyValueFactory<>("country"));
        ratingColumn.setCellValueFactory(new PropertyValueFactory<>("globalRating"));
        playerTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean hasSelection = selected != null;
            editButton.setDisable(!hasSelection);
            deactivateButton.setDisable(!hasSelection);
        });
        editButton.setDisable(true);
        deactivateButton.setDisable(true);
        refreshPlayers();
    }

    @FXML
    private void onAdd() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        Player selected = playerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alerts.info("Edit player", "Select a player to edit.");
            return;
        }
        openForm(selected);
    }

    @FXML
    private void onDeactivate() {
        Player selected = playerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Alerts.confirm("Deactivate player",
                "Deactivate \"" + selected.getName() + "\"? They will no longer appear in the active list.")) {
            return;
        }
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                playerService.deactivate(selected.getId());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText("Deactivated " + selected.getName());
            refreshPlayers();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to deactivate player", error);
            String message = error instanceof DomainException
                    ? error.getMessage()
                    : "Could not deactivate player: " + error.getMessage();
            Alerts.error("Deactivate failed", message);
        });
        new Thread(task, "deactivate-player").start();
    }

    @FXML
    private void onRefresh() {
        refreshPlayers();
    }

    private void refreshPlayers() {
        setBusy(true);
        statusLabel.setText("Loading…");
        Task<List<Player>> task = new Task<>() {
            @Override
            protected List<Player> call() {
                return playerService.list();
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            List<Player> players = task.getValue();
            playerTable.getItems().setAll(players);
            statusLabel.setText(players.size() + " player(s)");
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to load players", error);
            statusLabel.setText("Load failed");
            Alerts.error("Load failed", error.getMessage());
        });
        new Thread(task, "load-players").start();
    }

    private void openForm(Player existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/player_form.fxml"));
            Parent root = loader.load();
            PlayerFormController formController = loader.getController();
            formController.setPlayerService(playerService);
            formController.setExisting(existing);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(playerTable.getScene().getWindow());
            dialog.setTitle(existing == null ? "Add Player" : "Edit Player");
            Scene scene = new Scene(root);
            UiStyles.apply(scene);
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            if (formController.isSaved()) {
                refreshPlayers();
                statusLabel.setText(existing == null ? "Player created" : "Player updated");
            }
        } catch (IOException e) {
            log.error("Failed to open player form", e);
            Alerts.error("UI error", "Could not open player form: " + e.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        addButton.setDisable(busy);
        refreshButton.setDisable(busy);
        boolean hasSelection = playerTable.getSelectionModel().getSelectedItem() != null;
        editButton.setDisable(busy || !hasSelection);
        deactivateButton.setDisable(busy || !hasSelection);
    }
}
