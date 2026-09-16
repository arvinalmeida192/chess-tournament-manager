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
import com.chess.tournament.domain.enums.GameResult;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Knockout bracket view: games grouped by round (FR-KO-003).
 * Winner column shows a placeholder until results are entered (Phase 8).
 */
public class KnockoutBracketController {

    private static final Logger log = LoggerFactory.getLogger(KnockoutBracketController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<BracketRow> bracketTable;
    @FXML
    private TableColumn<BracketRow, Number> roundColumn;
    @FXML
    private TableColumn<BracketRow, Number> boardColumn;
    @FXML
    private TableColumn<BracketRow, String> whiteColumn;
    @FXML
    private TableColumn<BracketRow, String> blackColumn;
    @FXML
    private TableColumn<BracketRow, String> resultColumn;
    @FXML
    private TableColumn<BracketRow, String> winnerColumn;
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

    @FXML
    private void initialize() {
        pairingService = AppContext.get().getPairingService();
        tournamentService = AppContext.get().getTournamentService();
        tournamentPlayerDao = AppContext.get().getTournamentPlayerDao();
        playerDao = AppContext.get().getPlayerDao();
        roundDao = AppContext.get().getRoundDao();

        roundColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().round()));
        boardColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().board()));
        whiteColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().white()));
        blackColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().black()));
        resultColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().result()));
        winnerColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().winner()));
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        titleLabel.setText("Knockout Bracket — " + tournament.getName());
        refresh();
    }

    @FXML
    private void onGenerate() {
        Optional<Integer> pairable = pairingService.findPairableRoundNumber(tournamentId);
        if (pairable.isEmpty()) {
            Alerts.info("Bracket", "No round is ready for pairing generation.");
            return;
        }
        int targetRound = pairable.get();
        if (!Alerts.confirm("Generate pairings",
                "Generate and publish Knockout pairings for round " + targetRound + "?")) {
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
            statusLabel.setText("Published round " + targetRound);
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to publish KO pairings", error);
            String message = error instanceof DomainException ? error.getMessage() : error.getMessage();
            Alerts.error("Pairings failed", message);
        });
        new Thread(task, "publish-ko-pairings").start();
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
                if (tournament.getType() != TournamentType.KNOCKOUT) {
                    throw new IllegalStateException("Bracket view is for Knockout tournaments");
                }
                Map<Long, String> names = resolveNames(tournamentId);
                List<Round> rounds = roundDao.findByTournament(tournamentId);
                List<BracketRow> rows = new ArrayList<>();
                for (Round round : rounds) {
                    List<Game> games = pairingService.listGamesForRound(tournamentId, round.getRoundNumber());
                    for (Game g : games) {
                        String white = nameOf(g.getWhiteTournamentPlayerId(), names);
                        String black = g.getBlackTournamentPlayerId() == null
                                ? "— BYE —"
                                : nameOf(g.getBlackTournamentPlayerId(), names);
                        rows.add(new BracketRow(
                                round.getRoundNumber(),
                                g.getBoardNumber(),
                                white,
                                black,
                                g.getResult() == null ? "" : g.getResult().name(),
                                winnerLabel(g, names)));
                    }
                }
                rows.sort(Comparator.comparingInt(BracketRow::round)
                        .thenComparingInt(BracketRow::board));
                boolean canGenerate = pairingService.findPairableRoundNumber(tournamentId).isPresent();
                return new RefreshData(rows, canGenerate);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            RefreshData data = task.getValue();
            bracketTable.getItems().setAll(data.rows());
            generateButton.setDisable(!data.canGenerate());
            statusLabel.setText(data.rows().isEmpty()
                    ? "No bracket games yet — generate round 1 pairings"
                    : data.rows().size() + " game(s) across bracket");
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load bracket", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-ko-bracket").start();
    }

    private static String winnerLabel(Game g, Map<Long, String> names) {
        GameResult result = g.getResult();
        if (result == null || result == GameResult.PENDING) {
            return "(pending results — Phase 8)";
        }
        return switch (result) {
            case BYE, WHITE_WIN -> nameOf(g.getWhiteTournamentPlayerId(), names);
            case BLACK_WIN -> nameOf(g.getBlackTournamentPlayerId(), names);
            case DRAW -> "(draw — invalid for KO)";
            case PENDING -> "(pending results — Phase 8)";
        };
    }

    private Map<Long, String> resolveNames(long tournamentId) {
        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournamentId);
        Map<Long, String> names = new HashMap<>();
        for (TournamentPlayer tp : tps) {
            String name = playerDao.findById(tp.getPlayerId())
                    .map(Player::getName)
                    .orElse("Player#" + tp.getPlayerId());
            names.put(tp.getId(), name);
        }
        return names;
    }

    private static String nameOf(Long tpId, Map<Long, String> names) {
        if (tpId == null) {
            return "—";
        }
        return names.getOrDefault(tpId, "TP " + tpId);
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        if (busy) {
            generateButton.setDisable(true);
        }
    }

    public record BracketRow(int round, int board, String white, String black,
                             String result, String winner) {
    }

    private record RefreshData(List<BracketRow> rows, boolean canGenerate) {
    }
}
