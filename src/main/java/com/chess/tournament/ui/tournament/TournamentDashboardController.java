package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.util.Alerts;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class TournamentDashboardController {

    private static final Logger log = LoggerFactory.getLogger(TournamentDashboardController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label configLabel;
    @FXML
    private Label enrolledLabel;
    @FXML
    private Label roundLabel;
    @FXML
    private Label warningLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button enrollButton;
    @FXML
    private Button startButton;
    @FXML
    private Button pairingsButton;
    @FXML
    private Button leaderboardButton;
    @FXML
    private Button refreshButton;

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private RoundDao roundDao;
    private long tournamentId;
    private Runnable onBack;

    @FXML
    private void initialize() {
        tournamentService = AppContext.get().getTournamentService();
        enrollmentService = AppContext.get().getEnrollmentService();
        pairingService = AppContext.get().getPairingService();
        roundDao = AppContext.get().getRoundDao();
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        refresh();
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML
    private void onBack() {
        if (onBack != null) {
            onBack.run();
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onEnrollment() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/enrollment.fxml"));
            Parent root = loader.load();
            EnrollmentController controller = loader.getController();
            controller.setTournamentId(tournamentId);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(titleLabel.getScene().getWindow());
            dialog.setTitle("Enrollment");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
            refresh();
        } catch (IOException e) {
            log.error("Failed to open enrollment", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    @FXML
    private void onStart() {
        if (!Alerts.confirm("Start tournament",
                "Start this tournament? Round 1 will be created and status set to ACTIVE.")) {
            return;
        }
        setBusy(true);
        Task<Optional<String>> task = new Task<>() {
            @Override
            protected Optional<String> call() {
                return tournamentService.start(tournamentId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            Optional<String> warning = task.getValue();
            warning.ifPresent(msg -> Alerts.info("Started with warning", msg));
            statusLabel.setText("Tournament started");
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to start tournament", error);
            String message = error instanceof DomainException ? error.getMessage() : error.getMessage();
            Alerts.error("Start failed", message);
        });
        new Thread(task, "start-tournament").start();
    }

    @FXML
    private void onPairings() {
        try {
            Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
            String fxml = tournament.getType() == TournamentType.KNOCKOUT
                    ? "/fxml/knockout_bracket.fxml"
                    : "/fxml/pairings.fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            if (tournament.getType() == TournamentType.KNOCKOUT) {
                KnockoutBracketController controller = loader.getController();
                controller.setTournamentId(tournamentId);
            } else {
                PairingsController controller = loader.getController();
                controller.setTournamentId(tournamentId);
            }

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(titleLabel.getScene().getWindow());
            dialog.setTitle(tournament.getType() == TournamentType.KNOCKOUT
                    ? "Knockout Bracket" : "Pairings");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
            refresh();
        } catch (IOException e) {
            log.error("Failed to open pairings", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    @FXML
    private void onLeaderboard() {
        Alerts.info("Leaderboard", "Leaderboard arrives in Phase 9.");
    }

    private void refresh() {
        setBusy(true);
        Task<TournamentViewModel> task = new Task<>() {
            @Override
            protected TournamentViewModel call() {
                Tournament tournament = tournamentService.findById(tournamentId)
                        .orElseThrow(() -> new IllegalStateException("Tournament not found"));
                int enrolled = tournamentService.enrolledCount(tournamentId);
                Optional<Round> round1 = roundDao.findByTournamentAndNumber(tournamentId, 1);
                Optional<Round> pairable = pairingService.findPairableRoundNumber(tournamentId)
                        .flatMap(n -> roundDao.findByTournamentAndNumber(tournamentId, n));
                boolean locked = enrollmentService.isEnrollmentLocked(tournamentId);
                return new TournamentViewModel(tournament, enrolled, round1, pairable, locked);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            applyViewModel(task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load dashboard", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-dashboard").start();
    }

    private void applyViewModel(TournamentViewModel vm) {
        Tournament t = vm.getTournament();
        titleLabel.setText(t.getName());
        configLabel.setText("Type: " + t.getType()
                + "  |  Status: " + t.getStatus()
                + "  |  Rounds planned: " + t.getRoundsPlanned()
                + "  |  Qualifiers: " + t.getQualifiersCount()
                + (t.getType().name().equals("SWISS")
                ? "  |  Swiss first round: " + t.getSwissFirstRoundMethod() : ""));
        enrolledLabel.setText("Enrolled players: " + vm.getEnrolledCount());
        roundLabel.setText("Current round: " + vm.getCurrentRoundLabel());
        String warning = vm.swissWarningIfAny();
        warningLabel.setText(warning == null ? "" : warning);
        warningLabel.setVisible(warning != null);

        enrollButton.setDisable(!vm.canEnroll() && vm.isEnrollmentLocked());
        // Allow opening enrollment screen when can enroll OR to view locked list
        enrollButton.setDisable(false);
        startButton.setDisable(!vm.canStart());
        pairingsButton.setDisable(!vm.canOpenPairings());
        leaderboardButton.setDisable(!vm.canViewLeaderboard());
        statusLabel.setText(vm.isEnrollmentLocked() ? "Enrollment is locked" : "Enrollment open");
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        if (busy) {
            startButton.setDisable(true);
            pairingsButton.setDisable(true);
            leaderboardButton.setDisable(true);
        }
    }
}
