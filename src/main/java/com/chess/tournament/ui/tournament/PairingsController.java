package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.util.Alerts;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PairingsController {

    private static final Logger log = LoggerFactory.getLogger(PairingsController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label roundLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<PairingRow> pairingsTable;
    @FXML
    private TableColumn<PairingRow, Number> boardColumn;
    @FXML
    private TableColumn<PairingRow, String> whiteColumn;
    @FXML
    private TableColumn<PairingRow, String> blackColumn;
    @FXML
    private TableColumn<PairingRow, String> resultColumn;
    @FXML
    private Button generateButton;
    @FXML
    private Button refreshButton;

    private PairingService pairingService;
    private TournamentService tournamentService;
    private TournamentPlayerDao tournamentPlayerDao;
    private PlayerDao playerDao;
    private long tournamentId;
    private int roundNumber;

    @FXML
    private void initialize() {
        pairingService = AppContext.get().getPairingService();
        tournamentService = AppContext.get().getTournamentService();
        tournamentPlayerDao = AppContext.get().getTournamentPlayerDao();
        playerDao = AppContext.get().getPlayerDao();

        boardColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().board()));
        whiteColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().white()));
        blackColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().black()));
        resultColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().result()));
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        titleLabel.setText("Pairings — " + tournament.getName());

        Optional<Integer> pairable = pairingService.findPairableRoundNumber(tournamentId);
        if (pairable.isPresent()) {
            this.roundNumber = pairable.get();
        } else {
            // Show latest round that has games, else round 1
            this.roundNumber = 1;
            List<Game> existing = safeListGames(1);
            if (!existing.isEmpty()) {
                this.roundNumber = 1;
            }
        }
        refresh();
    }

    @FXML
    private void onGenerate() {
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        if (tournament.getType() != TournamentType.ROUND_ROBIN
                && tournament.getType() != TournamentType.KNOCKOUT
                && tournament.getType() != TournamentType.SWISS) {
            Alerts.info("Pairings", "Unsupported tournament type for pairings.");
            return;
        }
        Optional<Integer> pairable = pairingService.findPairableRoundNumber(tournamentId);
        if (pairable.isEmpty()) {
            Alerts.info("Pairings", "No round is ready for pairing generation.");
            return;
        }
        int targetRound = pairable.get();
        if (!Alerts.confirm("Generate pairings",
                "Generate and publish " + tournament.getType() + " pairings for round "
                        + targetRound + "?")) {
            return;
        }

        setBusy(true);
        Task<List<Game>> task = new Task<>() {
            @Override
            protected List<Game> call() {
                return pairingService.generateAndPublish(tournamentId, targetRound);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            roundNumber = targetRound;
            statusLabel.setText("Published " + task.getValue().size() + " boards for round " + targetRound);
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to publish pairings", error);
            String message = error instanceof DomainException ? error.getMessage() : error.getMessage();
            Alerts.error("Pairings failed", message);
        });
        new Thread(task, "publish-pairings").start();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }

    private void refresh() {
        setBusy(true);
        Task<RefreshData> task = new Task<>() {
            @Override
            protected RefreshData call() {
                Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
                Optional<Integer> pairable = pairingService.findPairableRoundNumber(tournamentId);
                int displayRound = pairable.orElse(roundNumber);
                List<Game> games = pairingService.listGamesForRound(tournamentId, displayRound);
                if (games.isEmpty() && pairable.isEmpty() && displayRound != 1) {
                    games = pairingService.listGamesForRound(tournamentId, 1);
                    displayRound = 1;
                }
                Map<Long, String> names = resolveNames(tournamentId);
                List<PairingRow> rows = new ArrayList<>();
                for (Game g : games) {
                    String white = nameOf(g.getWhiteTournamentPlayerId(), names);
                    String black = g.getBlackTournamentPlayerId() == null
                            ? "— BYE —"
                            : nameOf(g.getBlackTournamentPlayerId(), names);
                    rows.add(new PairingRow(g.getBoardNumber(), white, black,
                            g.getResult() == null ? "" : g.getResult().name()));
                }
                boolean canGenerate = pairable.isPresent()
                        && (tournament.getType() == TournamentType.ROUND_ROBIN
                        || tournament.getType() == TournamentType.KNOCKOUT
                        || tournament.getType() == TournamentType.SWISS);
                return new RefreshData(displayRound, rows, canGenerate, tournament.getType());
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            RefreshData data = task.getValue();
            roundNumber = data.displayRound();
            roundLabel.setText("Round " + data.displayRound()
                    + "  |  Type: " + data.type());
            pairingsTable.getItems().setAll(data.rows());
            generateButton.setDisable(!data.canGenerate());
            if (data.rows().isEmpty() && data.canGenerate()) {
                statusLabel.setText("Ready to generate pairings for round " + data.displayRound());
            } else if (data.rows().isEmpty()) {
                statusLabel.setText("No pairings published yet");
            } else {
                statusLabel.setText(data.rows().size() + " board(s)");
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load pairings", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-pairings").start();
    }

    private Map<Long, String> resolveNames(long tournamentId) {
        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournamentId);
        Map<Long, String> names = new HashMap<>();
        for (TournamentPlayer tp : tps) {
            String name = playerDao.findById(tp.getPlayerId())
                    .map(Player::getName)
                    .orElse("Player#" + tp.getPlayerId());
            names.put(tp.getId(), name + " (TP " + tp.getId() + ")");
        }
        return names;
    }

    private static String nameOf(Long tpId, Map<Long, String> names) {
        if (tpId == null) {
            return "—";
        }
        return names.getOrDefault(tpId, "TP " + tpId);
    }

    private List<Game> safeListGames(int round) {
        try {
            return pairingService.listGamesForRound(tournamentId, round);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        if (busy) {
            generateButton.setDisable(true);
        }
    }

    public record PairingRow(int board, String white, String black, String result) {
    }

    private record RefreshData(int displayRound, List<PairingRow> rows,
                               boolean canGenerate, TournamentType type) {
    }
}
