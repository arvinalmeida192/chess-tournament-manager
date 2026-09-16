package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.LeaderboardService;
import com.chess.tournament.service.QualificationService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.leaderboard.StandingRow;
import com.chess.tournament.ui.util.Alerts;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

public class LeaderboardController {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label headerLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<Integer> roundCombo;
    @FXML
    private TableView<StandingRow> standingsTable;
    @FXML
    private TableColumn<StandingRow, Number> rankColumn;
    @FXML
    private TableColumn<StandingRow, String> playerColumn;
    @FXML
    private TableColumn<StandingRow, Number> ratingColumn;
    @FXML
    private TableColumn<StandingRow, BigDecimal> pointsColumn;
    @FXML
    private TableColumn<StandingRow, Number> winsColumn;
    @FXML
    private TableColumn<StandingRow, Number> drawsColumn;
    @FXML
    private TableColumn<StandingRow, Number> lossesColumn;
    @FXML
    private TableColumn<StandingRow, Number> startRatingColumn;
    @FXML
    private TableColumn<StandingRow, Number> finalRatingColumn;
    @FXML
    private TableColumn<StandingRow, String> qualificationColumn;
    @FXML
    private Button refreshButton;
    @FXML
    private Button qualifyButton;
    @FXML
    private Button finalizeButton;

    private LeaderboardService leaderboardService;
    private QualificationService qualificationService;
    private TournamentService tournamentService;
    private long tournamentId;
    private Tournament tournament;
    private boolean suppressRoundChange;

    @FXML
    private void initialize() {
        leaderboardService = AppContext.get().getLeaderboardService();
        qualificationService = AppContext.get().getQualificationService();
        tournamentService = AppContext.get().getTournamentService();

        rankColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getRank()));
        playerColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPlayerName()));
        ratingColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getRating()));
        pointsColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPoints()));
        winsColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getWins()));
        drawsColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getDraws()));
        lossesColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getLosses()));
        startRatingColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStartRating()));
        finalRatingColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getFinalRating()));
        qualificationColumn.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQualificationStatus() == null
                        ? ""
                        : c.getValue().getQualificationStatus().name()));

        roundCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (!suppressRoundChange && newV != null) {
                loadStandings(newV);
            }
        });

        setFinalColumnsVisible(false);
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        this.tournament = tournamentService.findById(tournamentId).orElseThrow();
        titleLabel.setText("Leaderboard — " + tournament.getName());
        refreshMetaAndLoad();
    }

    @FXML
    private void onRefresh() {
        refreshMetaAndLoad();
    }

    @FXML
    private void onApplyQualification() {
        if (!Alerts.confirm("Apply qualification",
                "Mark top " + tournament.getQualifiersCount()
                        + " players as QUALIFIED (others ELIMINATED)?")) {
            return;
        }
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                qualificationService.applyQualification(tournamentId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText("Qualification applied");
            tournament = tournamentService.findById(tournamentId).orElseThrow();
            updateActionButtons();
            Integer selected = roundCombo.getValue();
            if (selected != null) {
                loadStandings(selected);
            } else {
                refreshMetaAndLoad();
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable err = task.getException();
            log.error("Qualification failed", err);
            Alerts.error("Qualification failed",
                    err instanceof DomainException ? err.getMessage() : err.getMessage());
        });
        new Thread(task, "apply-qualification").start();
    }

    @FXML
    private void onFinalize() {
        if (!Alerts.confirm("Finalize tournament",
                "Finalize this tournament? Qualification will be applied and the tournament locked as COMPLETED.")) {
            return;
        }
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                tournamentService.finalizeTournament(tournamentId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText("Tournament finalized");
            tournament = tournamentService.findById(tournamentId).orElseThrow();
            updateActionButtons();
            setFinalColumnsVisible(true);
            Integer selected = roundCombo.getValue();
            if (selected != null) {
                loadStandings(selected);
            } else {
                refreshMetaAndLoad();
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable err = task.getException();
            log.error("Finalize failed", err);
            Alerts.error("Finalize failed",
                    err instanceof DomainException ? err.getMessage() : err.getMessage());
        });
        new Thread(task, "finalize-tournament").start();
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }

    private void refreshMetaAndLoad() {
        setBusy(true);
        Task<List<Integer>> task = new Task<>() {
            @Override
            protected List<Integer> call() {
                return leaderboardService.listCompletedRoundNumbers(tournamentId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            tournament = tournamentService.findById(tournamentId).orElseThrow();
            List<Integer> rounds = task.getValue();
            suppressRoundChange = true;
            roundCombo.setItems(FXCollections.observableArrayList(rounds));
            if (rounds.isEmpty()) {
                headerLabel.setText("No completed rounds yet");
                standingsTable.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Complete a round to see standings");
            } else {
                Integer latest = rounds.get(rounds.size() - 1);
                roundCombo.setValue(latest);
                suppressRoundChange = false;
                loadStandings(latest);
            }
            suppressRoundChange = false;
            updateActionButtons();
            setFinalColumnsVisible(tournament.getStatus() == TournamentStatus.COMPLETED);
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load rounds", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-leaderboard-rounds").start();
    }

    private void loadStandings(int roundNumber) {
        setBusy(true);
        Task<List<StandingRow>> task = new Task<>() {
            @Override
            protected List<StandingRow> call() {
                return leaderboardService.getLeaderboard(tournamentId, roundNumber);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            List<StandingRow> rows = task.getValue();
            standingsTable.setItems(FXCollections.observableArrayList(rows));
            headerLabel.setText("Round " + roundNumber + " Leaderboard");
            statusLabel.setText(rows.size() + " players");
            updateActionButtons();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load standings", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-standings").start();
    }

    private void updateActionButtons() {
        boolean hasCompleted = roundCombo.getItems() != null && !roundCombo.getItems().isEmpty();
        boolean activeOrDone = tournament.getStatus() == TournamentStatus.ACTIVE
                || tournament.getStatus() == TournamentStatus.COMPLETED;
        qualifyButton.setDisable(!(activeOrDone && hasCompleted));
        finalizeButton.setDisable(!tournamentService.areAllRoundsComplete(tournamentId)
                || tournament.getStatus() != TournamentStatus.ACTIVE);
    }

    private void setFinalColumnsVisible(boolean visible) {
        startRatingColumn.setVisible(visible);
        finalRatingColumn.setVisible(visible);
        qualificationColumn.setVisible(visible);
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        roundCombo.setDisable(busy);
        if (busy) {
            qualifyButton.setDisable(true);
            finalizeButton.setDisable(true);
        }
    }
}
