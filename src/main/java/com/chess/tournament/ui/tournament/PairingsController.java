package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.util.Alerts;
import com.chess.tournament.ui.util.DisplayLabels;
import com.chess.tournament.ui.util.TaskExceptions;
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
    private RoundDao roundDao;
    private long tournamentId;
    private int roundNumber;
    private boolean canGenerate;

    @FXML
    private void initialize() {
        pairingService = AppContext.get().getPairingService();
        tournamentService = AppContext.get().getTournamentService();
        tournamentPlayerDao = AppContext.get().getTournamentPlayerDao();
        playerDao = AppContext.get().getPlayerDao();
        roundDao = AppContext.get().getRoundDao();

        boardColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().board()));
        whiteColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().white()));
        blackColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().black()));
        resultColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().result()));
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        titleLabel.setText("Pairings — " + tournament.getName());
        this.roundNumber = resolvePairableRound().orElse(1);
        refresh();
    }

    @FXML
    private void onGenerate() {
        Optional<Integer> pairable = resolvePairableRound();
        if (pairable.isEmpty()) {
            Alerts.info("Pairings", "No round is ready for pairing generation.");
            refresh();
            return;
        }
        int targetRound = pairable.get();
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        if (!Alerts.confirm("Generate pairings",
                "Generate and publish " + DisplayLabels.tournamentType(tournament.getType())
                        + " pairings for round " + targetRound + "?")) {
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
            Alerts.info("Pairings published",
                    "Round " + targetRound + " pairings are ready. Enter results from the dashboard.");
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to publish pairings", error);
            Alerts.error("Pairings failed", TaskExceptions.message(error));
            applyGenerateEnabled();
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

    private Optional<Integer> resolvePairableRound() {
        Optional<Integer> pairable = pairingService.findPairableRoundNumber(tournamentId);
        if (pairable.isPresent()) {
            return pairable;
        }
        return pairingService.prepareNextRound(tournamentId);
    }

    private void refresh() {
        setBusy(true);
        Task<RefreshData> task = new Task<>() {
            @Override
            protected RefreshData call() {
                Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
                Optional<Integer> pairable = resolvePairableRound();
                int displayRound;
                List<Game> games;
                if (pairable.isPresent()) {
                    displayRound = pairable.get();
                    games = safeListGames(displayRound);
                } else {
                    displayRound = latestRoundWithGames().orElse(roundNumber);
                    games = safeListGames(displayRound);
                }
                Map<Long, String> names = resolveNames(tournamentId);
                List<PairingRow> rows = new ArrayList<>();
                for (Game g : games) {
                    String white = nameOf(g.getWhiteTournamentPlayerId(), names);
                    String black = g.getBlackTournamentPlayerId() == null
                            ? "— Bye —"
                            : nameOf(g.getBlackTournamentPlayerId(), names);
                    rows.add(new PairingRow(g.getBoardNumber(), white, black,
                            DisplayLabels.gameResult(g.getResult())));
                }
                return new RefreshData(displayRound, rows, pairable.isPresent(), tournament.getType(),
                        pairable.orElse(null));
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            RefreshData data = task.getValue();
            roundNumber = data.displayRound();
            canGenerate = data.canGenerate();
            roundLabel.setText("Round " + data.displayRound()
                    + "  ·  " + DisplayLabels.tournamentType(data.type()));
            pairingsTable.getItems().setAll(data.rows());
            applyGenerateEnabled();
            if (data.canGenerate() && data.rows().isEmpty()) {
                statusLabel.setText("Ready to generate pairings for round " + data.displayRound());
            } else if (data.canGenerate()) {
                statusLabel.setText("Round " + data.pairableRound()
                        + " is ready — click Generate & Publish");
            } else if (data.rows().isEmpty()) {
                statusLabel.setText("No pairings published yet");
            } else {
                statusLabel.setText(data.rows().size() + " board(s) for round " + data.displayRound());
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load pairings", task.getException());
            Alerts.error("Load failed", TaskExceptions.message(task.getException()));
            applyGenerateEnabled();
        });
        new Thread(task, "load-pairings").start();
    }

    private Optional<Integer> latestRoundWithGames() {
        List<Round> rounds = roundDao.findByTournament(tournamentId);
        for (int i = rounds.size() - 1; i >= 0; i--) {
            Round round = rounds.get(i);
            if (round.getStatus() != RoundStatus.PENDING_PAIRINGS
                    || !safeListGames(round.getRoundNumber()).isEmpty()) {
                return Optional.of(round.getRoundNumber());
            }
        }
        return Optional.empty();
    }

    private List<Game> safeListGames(int round) {
        try {
            return pairingService.listGamesForRound(tournamentId, round);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private Map<Long, String> resolveNames(long tournamentId) {
        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournamentId);
        Map<Long, String> names = new HashMap<>();
        for (TournamentPlayer tp : tps) {
            String name = playerDao.findById(tp.getPlayerId())
                    .map(Player::getName)
                    .orElse("Unknown player");
            names.put(tp.getId(), name);
        }
        return names;
    }

    private static String nameOf(Long tpId, Map<Long, String> names) {
        if (tpId == null) {
            return "—";
        }
        return names.getOrDefault(tpId, "Unknown player");
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        if (busy) {
            generateButton.setDisable(true);
        } else {
            applyGenerateEnabled();
        }
    }

    private void applyGenerateEnabled() {
        generateButton.setDisable(!canGenerate);
    }

    public record PairingRow(int board, String white, String black, String result) {
    }

    private record RefreshData(int displayRound, List<PairingRow> rows,
                               boolean canGenerate, com.chess.tournament.domain.enums.TournamentType type,
                               Integer pairableRound) {
    }
}
