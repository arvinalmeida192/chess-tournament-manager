package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.ContentNavigator;
import com.chess.tournament.ui.util.Alerts;
import com.chess.tournament.ui.util.UiStyles;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

public class TournamentListController {

    private static final Logger log = LoggerFactory.getLogger(TournamentListController.class);
    private static final String ALL_STATUSES = "ALL";

    @FXML
    private TableView<Tournament> tournamentTable;
    @FXML
    private TableColumn<Tournament, Long> idColumn;
    @FXML
    private TableColumn<Tournament, String> nameColumn;
    @FXML
    private TableColumn<Tournament, Object> typeColumn;
    @FXML
    private TableColumn<Tournament, Object> statusColumn;
    @FXML
    private TableColumn<Tournament, Integer> roundsColumn;
    @FXML
    private TableColumn<Tournament, Integer> qualifiersColumn;
    @FXML
    private Button createButton;
    @FXML
    private Button openButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button refreshButton;
    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private Label statusLabel;

    private TournamentService tournamentService;

    @FXML
    private void initialize() {
        tournamentService = AppContext.get().getTournamentService();
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        roundsColumn.setCellValueFactory(new PropertyValueFactory<>("roundsPlanned"));
        qualifiersColumn.setCellValueFactory(new PropertyValueFactory<>("qualifiersCount"));

        statusFilter.setItems(FXCollections.observableArrayList(
                ALL_STATUSES,
                TournamentStatus.DRAFT.name(),
                TournamentStatus.ACTIVE.name(),
                TournamentStatus.COMPLETED.name(),
                TournamentStatus.CANCELLED.name()));
        statusFilter.getSelectionModel().select(ALL_STATUSES);
        statusFilter.setOnAction(e -> refreshList());

        openButton.setDisable(true);
        cancelButton.setDisable(true);
        tournamentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, selected) -> {
            openButton.setDisable(selected == null);
            cancelButton.setDisable(selected == null || !canCancel(selected));
        });

        tournamentTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tournamentTable.getSelectionModel().getSelectedItem() != null) {
                onOpen();
            }
        });

        refreshList();
    }

    @FXML
    private void onCreate() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/tournament_create.fxml"));
            Parent root = loader.load();
            TournamentCreateController controller = loader.getController();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(tournamentTable.getScene().getWindow());
            dialog.setTitle("Create Tournament");
            Scene scene = new Scene(root);
            UiStyles.apply(scene);
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            if (controller.isCreated()) {
                refreshList();
                statusLabel.setText("Tournament created");
                controller.getCreatedTournament().ifPresent(t -> openDashboard(t.getId()));
            }
        } catch (IOException e) {
            log.error("Failed to open create dialog", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    @FXML
    private void onOpen() {
        Tournament selected = tournamentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        openDashboard(selected.getId());
    }

    @FXML
    private void onCancelTournament() {
        Tournament selected = tournamentTable.getSelectionModel().getSelectedItem();
        if (selected == null || !canCancel(selected)) {
            return;
        }
        if (!Alerts.confirm("Cancel tournament",
                "Cancel \"" + selected.getName()
                        + "\"? Status becomes CANCELLED. History is kept.")) {
            return;
        }
        setBusy(true);
        long id = selected.getId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                tournamentService.cancelTournament(id);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText("Tournament cancelled");
            refreshList();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Cancel failed", error);
            Alerts.error("Cancel failed",
                    error instanceof DomainException ? error.getMessage() : error.getMessage());
        });
        new Thread(task, "cancel-tournament-list").start();
    }

    @FXML
    private void onRefresh() {
        refreshList();
    }

    private static boolean canCancel(Tournament tournament) {
        return tournament.getStatus() == TournamentStatus.DRAFT
                || tournament.getStatus() == TournamentStatus.ACTIVE;
    }

    private void openDashboard(long tournamentId) {
        ContentNavigator.load("/fxml/tournament_dashboard.fxml",
                (TournamentDashboardController controller) -> {
                    controller.setOnBack(() -> ContentNavigator.load("/fxml/tournament_list.fxml"));
                    controller.setTournamentId(tournamentId);
                });
    }

    private void refreshList() {
        setBusy(true);
        String filter = statusFilter.getSelectionModel().getSelectedItem();
        Task<List<Tournament>> task = new Task<>() {
            @Override
            protected List<Tournament> call() {
                if (filter == null || ALL_STATUSES.equals(filter)) {
                    return tournamentService.listAll();
                }
                return tournamentService.listByStatus(TournamentStatus.valueOf(filter));
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            List<Tournament> items = task.getValue();
            tournamentTable.getItems().setAll(items);
            statusLabel.setText(items.size() + " tournament(s)");
            Tournament selected = tournamentTable.getSelectionModel().getSelectedItem();
            cancelButton.setDisable(selected == null || !canCancel(selected));
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load tournaments", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-tournaments").start();
    }

    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        refreshButton.setDisable(busy);
        statusFilter.setDisable(busy);
        openButton.setDisable(busy || tournamentTable.getSelectionModel().getSelectedItem() == null);
        Tournament selected = tournamentTable.getSelectionModel().getSelectedItem();
        cancelButton.setDisable(busy || selected == null || !canCancel(selected));
    }
}
