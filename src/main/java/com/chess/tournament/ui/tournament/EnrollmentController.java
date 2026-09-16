package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.util.Alerts;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnrollmentController {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label lockLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private ListView<Player> availableList;
    @FXML
    private TableView<EnrolledRow> enrolledTable;
    @FXML
    private TableColumn<EnrolledRow, Number> playerIdColumn;
    @FXML
    private TableColumn<EnrolledRow, String> nameColumn;
    @FXML
    private TableColumn<EnrolledRow, Number> ratingColumn;
    @FXML
    private Button enrollButton;
    @FXML
    private Button unenrollButton;

    private EnrollmentService enrollmentService;
    private TournamentService tournamentService;
    private PlayerDao playerDao;
    private long tournamentId;
    private boolean locked;

    @FXML
    private void initialize() {
        enrollmentService = AppContext.get().getEnrollmentService();
        tournamentService = AppContext.get().getTournamentService();
        playerDao = AppContext.get().getPlayerDao();

        availableList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Player item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getId() + " — " + item.getName() + " (" + item.getGlobalRating() + ")");
                }
            }
        });

        playerIdColumn.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().playerId()));
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        ratingColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().startRating()));
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        titleLabel.setText("Enrollment — " + tournament.getName());
        refresh();
    }

    @FXML
    private void onEnroll() {
        Player selected = availableList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alerts.info("Enroll", "Select an available player.");
            return;
        }
        runMutation(() -> {
            enrollmentService.enroll(tournamentId, selected.getId());
            return "Enrolled " + selected.getName();
        });
    }

    @FXML
    private void onUnenroll() {
        EnrolledRow selected = enrolledTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alerts.info("Remove", "Select an enrolled player.");
            return;
        }
        if (!Alerts.confirm("Remove enrollment", "Remove " + selected.name() + " from this tournament?")) {
            return;
        }
        runMutation(() -> {
            enrollmentService.unenroll(tournamentId, selected.playerId());
            return "Removed " + selected.name();
        });
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }

    private void runMutation(Mutation mutation) {
        setBusy(true);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return mutation.run();
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText(task.getValue());
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Enrollment action failed", error);
            String message = error instanceof DomainException ? error.getMessage() : error.getMessage();
            Alerts.error("Enrollment failed", message);
        });
        new Thread(task, "enrollment-action").start();
    }

    private void refresh() {
        setBusy(true);
        Task<EnrollmentSnapshot> task = new Task<>() {
            @Override
            protected EnrollmentSnapshot call() {
                boolean isLocked = enrollmentService.isEnrollmentLocked(tournamentId);
                List<Player> available = enrollmentService.listAvailablePlayers(tournamentId);
                List<TournamentPlayer> enrolled = enrollmentService.listEnrolled(tournamentId);
                Map<Long, Player> playersById = playerDao.findAll(false).stream()
                        .collect(Collectors.toMap(Player::getId, p -> p, (a, b) -> a));
                List<EnrolledRow> rows = new ArrayList<>();
                for (TournamentPlayer tp : enrolled) {
                    Player p = playersById.get(tp.getPlayerId());
                    String name = p == null ? ("#" + tp.getPlayerId()) : p.getName();
                    rows.add(new EnrolledRow(tp.getPlayerId(), name, tp.getStartRating()));
                }
                return new EnrollmentSnapshot(isLocked, available, rows);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            EnrollmentSnapshot snap = task.getValue();
            locked = snap.locked();
            lockLabel.setText(locked
                    ? "Enrollment is locked (round 1 pairings already published or tournament not open)."
                    : "");
            availableList.getItems().setAll(snap.available());
            enrolledTable.getItems().setAll(snap.enrolled());
            enrollButton.setDisable(locked);
            unenrollButton.setDisable(locked);
            statusLabel.setText(snap.enrolled().size() + " enrolled, " + snap.available().size() + " available");
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load enrollment", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-enrollment").start();
    }

    private void setBusy(boolean busy) {
        enrollButton.setDisable(busy || locked);
        unenrollButton.setDisable(busy || locked);
    }

    @FunctionalInterface
    private interface Mutation {
        String run() throws Exception;
    }

    private record EnrolledRow(long playerId, String name, int startRating) {
    }

    private record EnrollmentSnapshot(boolean locked, List<Player> available, List<EnrolledRow> enrolled) {
    }
}
