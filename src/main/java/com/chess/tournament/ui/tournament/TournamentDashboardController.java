package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.ResultService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.util.Alerts;
import com.chess.tournament.ui.util.DialogStages;
import com.chess.tournament.ui.util.DisplayLabels;
import com.chess.tournament.ui.util.TaskExceptions;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
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
    private Label nextStepLabel;
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
    private Button nextRoundButton;
    @FXML
    private Button resultsButton;
    @FXML
    private Button leaderboardButton;
    @FXML
    private Button finalizeButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button refreshButton;

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private ResultService resultService;
    private RoundDao roundDao;
    private long tournamentId;
    private Runnable onBack;
    private TournamentViewModel currentVm;

    @FXML
    private void initialize() {
        tournamentService = AppContext.get().getTournamentService();
        enrollmentService = AppContext.get().getEnrollmentService();
        pairingService = AppContext.get().getPairingService();
        resultService = AppContext.get().getResultService();
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
            showDialog(root, "Enrollment");
            refresh();
        } catch (IOException e) {
            log.error("Failed to open enrollment", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    @FXML
    private void onStart() {
        if (!Alerts.confirm("Start tournament",
                "Start this tournament? Round 1 will be created and status set to Active.")) {
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
            if (currentVm != null) {
                applyViewModel(currentVm);
            }
            Throwable error = task.getException();
            log.error("Failed to start tournament", error);
            Alerts.error("Start failed", TaskExceptions.message(error));
        });
        new Thread(task, "start-tournament").start();
    }

    @FXML
    private void onPairings() {
        openPairingsDialog();
        refresh();
    }

    @FXML
    private void onNextRound() {
        setBusy(true);
        Task<Optional<Integer>> task = new Task<>() {
            @Override
            protected Optional<Integer> call() {
                return pairingService.prepareNextRound(tournamentId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            Optional<Integer> next = task.getValue();
            if (next.isEmpty()) {
                Alerts.info("Next round",
                        "No further round is available. All planned rounds may already be complete.");
                refresh();
                return;
            }
            openPairingsDialog();
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            if (currentVm != null) {
                applyViewModel(currentVm);
            }
            Throwable error = task.getException();
            log.error("Failed to prepare next round", error);
            Alerts.error("Next round failed", TaskExceptions.message(error));
        });
        new Thread(task, "prepare-next-round").start();
    }

    @FXML
    private void onResults() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/results.fxml"));
            Parent root = loader.load();
            ResultsController controller = loader.getController();
            controller.setTournamentId(tournamentId);
            showDialog(root, "Results");
            if (controller.shouldProceedToPairings()) {
                openPairingsDialog();
            }
            refresh();
        } catch (IOException e) {
            log.error("Failed to open results", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    @FXML
    private void onLeaderboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/leaderboard.fxml"));
            Parent root = loader.load();
            LeaderboardController controller = loader.getController();
            controller.setTournamentId(tournamentId);
            showDialog(root, "Leaderboard");
            refresh();
        } catch (IOException e) {
            log.error("Failed to open leaderboard", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    @FXML
    private void onFinalize() {
        if (!Alerts.confirm("Finalize tournament",
                "Finalize this tournament? Qualification will be applied and the tournament locked as Completed.")) {
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
            Alerts.info("Finalized", "Tournament is completed. Open Leaderboard for the final standings.");
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            if (currentVm != null) {
                applyViewModel(currentVm);
            }
            Throwable error = task.getException();
            log.error("Finalize failed", error);
            Alerts.error("Finalize failed", TaskExceptions.message(error));
        });
        new Thread(task, "finalize-tournament").start();
    }

    @FXML
    private void onCancelTournament() {
        if (!Alerts.confirm("Cancel tournament",
                "Cancel this tournament? Status becomes Cancelled. History is kept; this cannot be undone from the UI.")) {
            return;
        }
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                tournamentService.cancelTournament(tournamentId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText("Tournament cancelled");
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            if (currentVm != null) {
                applyViewModel(currentVm);
            }
            Throwable error = task.getException();
            log.error("Cancel failed", error);
            Alerts.error("Cancel failed", TaskExceptions.message(error));
        });
        new Thread(task, "cancel-tournament").start();
    }

    private void openPairingsDialog() {
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
            showDialog(root, tournament.getType() == TournamentType.KNOCKOUT
                    ? "Knockout Bracket" : "Pairings");
        } catch (IOException e) {
            log.error("Failed to open pairings", e);
            Alerts.error("UI error", e.getMessage());
        }
    }

    private void showDialog(Parent root, String title) {
        DialogStages.showModal(root, title, titleLabel.getScene().getWindow());
    }

    private void refresh() {
        setBusy(true);
        Task<TournamentViewModel> task = new Task<>() {
            @Override
            protected TournamentViewModel call() {
                Tournament tournament = tournamentService.findById(tournamentId)
                        .orElseThrow(() -> new IllegalStateException("Tournament not found"));
                int enrolled = tournamentService.enrolledCount(tournamentId);
                List<Round> rounds = roundDao.findByTournament(tournamentId);
                Optional<Round> round1 = rounds.stream()
                        .filter(r -> r.getRoundNumber() == 1)
                        .findFirst();
                Optional<Round> pairable = pairingService.findPairableRoundNumber(tournamentId)
                        .flatMap(n -> rounds.stream().filter(r -> r.getRoundNumber() == n).findFirst());
                // If completeRound already created the next row, it is in rounds; else prepare for UI
                if (pairable.isEmpty() && tournament.getStatus()
                        == com.chess.tournament.domain.enums.TournamentStatus.ACTIVE) {
                    Optional<Integer> prepared = pairingService.prepareNextRound(tournamentId);
                    if (prepared.isPresent()) {
                        pairable = roundDao.findByTournamentAndNumber(tournamentId, prepared.get());
                    }
                }
                Optional<Round> results = resultService.findResultsRoundNumber(tournamentId)
                        .flatMap(n -> roundDao.findByTournamentAndNumber(tournamentId, n));
                boolean locked = enrollmentService.isEnrollmentLocked(tournamentId);
                boolean allComplete = tournamentService.areAllRoundsComplete(tournamentId);
                int highestCompleted = rounds.stream()
                        .filter(r -> r.getStatus() == RoundStatus.COMPLETED)
                        .mapToInt(Round::getRoundNumber)
                        .max()
                        .orElse(0);
                boolean hasCompleted = highestCompleted > 0;
                return new TournamentViewModel(tournament, enrolled, round1, pairable, results,
                        locked, allComplete, hasCompleted, highestCompleted);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            applyViewModel(task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false);
            if (currentVm != null) {
                applyViewModel(currentVm);
            }
            log.error("Failed to load dashboard", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-dashboard").start();
    }

    private void applyViewModel(TournamentViewModel vm) {
        this.currentVm = vm;
        Tournament t = vm.getTournament();
        titleLabel.setText(t.getName());
        StringBuilder config = new StringBuilder();
        config.append(DisplayLabels.tournamentType(t.getType()))
                .append("  ·  ")
                .append(DisplayLabels.tournamentStatus(t.getStatus()))
                .append("  ·  ")
                .append(t.getRoundsPlanned()).append(" rounds")
                .append("  ·  ")
                .append(t.getQualifiersCount()).append(" qualifiers");
        if (t.getType() == TournamentType.SWISS) {
            config.append("  ·  First round: ")
                    .append(DisplayLabels.swissMethod(t.getSwissFirstRoundMethod()));
        }
        configLabel.setText(config.toString());
        enrolledLabel.setText("Players enrolled: " + vm.getEnrolledCount());
        roundLabel.setText(vm.getCurrentRoundLabel());
        nextStepLabel.setText(vm.getNextStepHint());
        String warning = vm.swissWarningIfAny();
        warningLabel.setText(warning == null ? "" : warning);
        warningLabel.setVisible(warning != null);
        warningLabel.setManaged(warning != null);

        enrollButton.setDisable(!vm.canOpenEnrollment());
        startButton.setDisable(!vm.canStart());
        pairingsButton.setDisable(!vm.canOpenPairings());
        nextRoundButton.setDisable(!vm.canGeneratePairings());
        if (vm.canProceedToNextRound()) {
            nextRoundButton.setText("Proceed to Round " + vm.getPairableRound().get().getRoundNumber());
        } else if (vm.canGeneratePairings()) {
            int n = vm.getPairableRound().map(Round::getRoundNumber).orElse(1);
            nextRoundButton.setText(n == 1 ? "Generate Round 1 Pairings" : "Proceed to Round " + n);
        } else {
            nextRoundButton.setText("Proceed to Next Round");
        }
        resultsButton.setDisable(!vm.canEnterResults());
        leaderboardButton.setDisable(!vm.canViewLeaderboard());
        finalizeButton.setDisable(!vm.canFinalize());
        cancelButton.setDisable(!vm.canCancel());

        pairingsButton.getStyleClass().remove("primary-button");
        resultsButton.getStyleClass().remove("primary-button");
        nextRoundButton.getStyleClass().remove("primary-button");
        if (vm.canGeneratePairings()) {
            nextRoundButton.getStyleClass().add("primary-button");
        } else if (vm.canEnterResults()) {
            resultsButton.getStyleClass().add("primary-button");
        } else if (vm.canFinalize()) {
            // finalize already has primary-button in FXML
        }

        statusLabel.setText(vm.isEnrollmentLocked() ? "Enrollment is locked after pairings start." : "");
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        if (busy) {
            enrollButton.setDisable(true);
            startButton.setDisable(true);
            pairingsButton.setDisable(true);
            nextRoundButton.setDisable(true);
            resultsButton.setDisable(true);
            leaderboardButton.setDisable(true);
            finalizeButton.setDisable(true);
            cancelButton.setDisable(true);
        }
    }
}
