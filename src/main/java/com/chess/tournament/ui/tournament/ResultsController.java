package com.chess.tournament.ui.tournament;

import com.chess.tournament.bootstrap.AppContext;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.DomainException;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.ResultService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.ui.util.Alerts;
import com.chess.tournament.ui.util.DisplayLabels;
import com.chess.tournament.ui.util.TaskExceptions;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ResultsController {

    private static final Logger log = LoggerFactory.getLogger(ResultsController.class);

    private static final StringConverter<GameResult> RESULT_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(GameResult result) {
            return DisplayLabels.gameResult(result);
        }

        @Override
        public GameResult fromString(String string) {
            return null;
        }
    };

    @FXML
    private Label titleLabel;
    @FXML
    private Label roundLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<ResultRow> resultsTable;
    @FXML
    private TableColumn<ResultRow, Number> boardColumn;
    @FXML
    private TableColumn<ResultRow, String> whiteColumn;
    @FXML
    private TableColumn<ResultRow, String> blackColumn;
    @FXML
    private TableColumn<ResultRow, GameResult> resultColumn;
    @FXML
    private Button saveButton;
    @FXML
    private Button completeButton;
    @FXML
    private Button nextRoundButton;
    @FXML
    private Button refreshButton;

    private ResultService resultService;
    private PairingService pairingService;
    private TournamentService tournamentService;
    private TournamentPlayerDao tournamentPlayerDao;
    private PlayerDao playerDao;
    private long tournamentId;
    private int roundNumber;
    private TournamentType tournamentType = TournamentType.SWISS;
    private boolean proceedToPairings;
    private Integer preparedNextRound;

    @FXML
    private void initialize() {
        resultService = AppContext.get().getResultService();
        pairingService = AppContext.get().getPairingService();
        tournamentService = AppContext.get().getTournamentService();
        tournamentPlayerDao = AppContext.get().getTournamentPlayerDao();
        playerDao = AppContext.get().getPlayerDao();

        boardColumn.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().board()));
        whiteColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().white()));
        blackColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().black()));
        resultColumn.setCellValueFactory(c -> c.getValue().resultProperty());

        resultsTable.setEditable(true);
        resultColumn.setEditable(true);
        resultColumn.setCellFactory(col -> {
            ComboBoxTableCell<ResultRow, GameResult> cell = new ComboBoxTableCell<>() {
                @Override
                public void startEdit() {
                    ResultRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null && row.byeBoard()) {
                        return;
                    }
                    getItems().setAll(allowedResults());
                    super.startEdit();
                }
            };
            cell.setConverter(RESULT_CONVERTER);
            cell.setComboBoxEditable(false);
            return cell;
        });
        resultColumn.setOnEditCommit(event -> {
            ResultRow row = event.getRowValue();
            if (row.byeBoard()) {
                row.resultProperty().set(GameResult.BYE);
            } else if (event.getNewValue() != null && event.getNewValue() != GameResult.BYE) {
                row.resultProperty().set(event.getNewValue());
            }
            updateCompleteEnabled();
        });
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
        this.proceedToPairings = false;
        this.preparedNextRound = null;
        Tournament tournament = tournamentService.findById(tournamentId).orElseThrow();
        this.tournamentType = tournament.getType();
        titleLabel.setText("Results — " + tournament.getName());

        Optional<Integer> resultsRound = resultService.findResultsRoundNumber(tournamentId);
        this.roundNumber = resultsRound.orElse(1);
        refresh();
    }

    /** After closing, dashboard may open pairings when the host chose next round. */
    public boolean shouldProceedToPairings() {
        return proceedToPairings;
    }

    @FXML
    private void onSaveAll() {
        List<ResultRow> rows = new ArrayList<>(resultsTable.getItems());
        setBusy(true);
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                int saved = 0;
                for (ResultRow row : rows) {
                    GameResult selected = row.resultProperty().get();
                    if (selected == null || selected == GameResult.PENDING) {
                        continue;
                    }
                    if (row.storedResult() == selected) {
                        continue;
                    }
                    resultService.saveGameResult(row.gameId(), selected);
                    saved++;
                }
                return saved;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            statusLabel.setText("Saved " + task.getValue() + " result(s)");
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to save results", error);
            Alerts.error("Save failed", TaskExceptions.message(error));
        });
        new Thread(task, "save-results").start();
    }

    @FXML
    private void onCompleteRound() {
        if (!Alerts.confirm("Complete round",
                "Complete round " + roundNumber
                        + "? This applies points and ratings and cannot be undone from the UI.")) {
            return;
        }
        onSaveAllThenComplete();
    }

    @FXML
    private void onNextRound() {
        if (preparedNextRound == null) {
            Optional<Integer> prepared = pairingService.prepareNextRound(tournamentId);
            preparedNextRound = prepared.orElse(null);
        }
        if (preparedNextRound == null) {
            Alerts.info("Next round", "No further round is available to pair.");
            return;
        }
        proceedToPairings = true;
        onClose();
    }

    private void onSaveAllThenComplete() {
        List<ResultRow> rows = new ArrayList<>(resultsTable.getItems());
        int completedRound = roundNumber;
        setBusy(true);
        Task<Optional<Integer>> task = new Task<>() {
            @Override
            protected Optional<Integer> call() {
                for (ResultRow row : rows) {
                    GameResult selected = row.resultProperty().get();
                    if (selected == null || selected == GameResult.PENDING) {
                        throw new DomainException(
                                "All boards need a result before completing the round");
                    }
                    if (row.storedResult() != selected) {
                        resultService.saveGameResult(row.gameId(), selected);
                    }
                }
                return resultService.completeRound(tournamentId, completedRound);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            Optional<Integer> next = task.getValue();
            preparedNextRound = next.orElse(null);
            statusLabel.setText("Round " + completedRound + " completed");
            if (next.isPresent()) {
                nextRoundButton.setDisable(false);
                nextRoundButton.setText("Proceed to Round " + next.get());
                Alerts.info("Round completed",
                        "Round " + completedRound + " is done. Round " + next.get()
                                + " is ready — use Proceed to Next Round to generate pairings.");
            } else {
                nextRoundButton.setDisable(true);
                Alerts.info("Round completed",
                        "All planned rounds are complete. You can finalize from the dashboard.");
            }
            refresh();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            log.error("Failed to complete round", error);
            Alerts.error("Complete failed", TaskExceptions.message(error));
            refresh();
        });
        new Thread(task, "complete-round").start();
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
                Optional<Integer> resultsRound = resultService.findResultsRoundNumber(tournamentId);
                int displayRound = resultsRound.orElse(roundNumber);
                List<Game> games = resultService.listGamesForRound(tournamentId, displayRound);
                Map<Long, String> names = resolveNames(tournamentId);
                List<ResultRow> rows = new ArrayList<>();
                for (Game g : games) {
                    boolean bye = g.getBlackTournamentPlayerId() == null;
                    String white = nameOf(g.getWhiteTournamentPlayerId(), names);
                    String black = bye ? "— Bye —" : nameOf(g.getBlackTournamentPlayerId(), names);
                    GameResult result = g.getResult() == null ? GameResult.PENDING : g.getResult();
                    rows.add(new ResultRow(g.getId(), g.getBoardNumber(), white, black, result, bye));
                }
                boolean editable = resultsRound.isPresent();
                boolean allSet = rows.stream()
                        .allMatch(r -> r.resultProperty().get() != null
                                && r.resultProperty().get() != GameResult.PENDING);
                Optional<Integer> pairable = pairingService.findPairableRoundNumber(tournamentId);
                return new RefreshData(displayRound, rows, editable, allSet && editable,
                        tournament.getType(), pairable.orElse(null));
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            RefreshData data = task.getValue();
            roundNumber = data.displayRound();
            tournamentType = data.type();
            roundLabel.setText("Round " + data.displayRound()
                    + "  ·  " + DisplayLabels.tournamentType(data.type()));
            resultsTable.setItems(FXCollections.observableArrayList(data.rows()));
            saveButton.setDisable(!data.editable());
            completeButton.setDisable(!data.canComplete());
            resultsTable.setEditable(data.editable());

            if (preparedNextRound != null) {
                nextRoundButton.setDisable(false);
                nextRoundButton.setText("Proceed to Round " + preparedNextRound);
            } else if (data.nextPairableRound() != null && data.nextPairableRound() > data.displayRound()) {
                preparedNextRound = data.nextPairableRound();
                nextRoundButton.setDisable(false);
                nextRoundButton.setText("Proceed to Round " + preparedNextRound);
            } else {
                nextRoundButton.setDisable(true);
            }

            if (data.rows().isEmpty()) {
                statusLabel.setText("No games for this round");
            } else if (!data.editable()) {
                statusLabel.setText(preparedNextRound != null
                        ? "Round completed — proceed to the next round when ready"
                        : "No round is open for results");
            } else {
                statusLabel.setText(data.rows().size()
                        + " board(s) — edit Result, then Save or Complete Round");
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            log.error("Failed to load results", task.getException());
            Alerts.error("Load failed", task.getException().getMessage());
        });
        new Thread(task, "load-results").start();
    }

    private List<GameResult> allowedResults() {
        EnumSet<GameResult> allowed = EnumSet.of(
                GameResult.WHITE_WIN, GameResult.BLACK_WIN, GameResult.DRAW);
        if (tournamentType == TournamentType.KNOCKOUT) {
            allowed.remove(GameResult.DRAW);
        }
        return List.copyOf(allowed);
    }

    private void updateCompleteEnabled() {
        boolean allSet = resultsTable.getItems().stream()
                .allMatch(r -> r.resultProperty().get() != null
                        && r.resultProperty().get() != GameResult.PENDING);
        completeButton.setDisable(!allSet || resultsTable.getItems().isEmpty());
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
            saveButton.setDisable(true);
            completeButton.setDisable(true);
            nextRoundButton.setDisable(true);
        }
    }

    public static final class ResultRow {
        private final long gameId;
        private final int board;
        private final String white;
        private final String black;
        private final boolean byeBoard;
        private final GameResult storedResult;
        private final SimpleObjectProperty<GameResult> resultProperty;

        ResultRow(long gameId, int board, String white, String black,
                  GameResult result, boolean byeBoard) {
            this.gameId = gameId;
            this.board = board;
            this.white = white;
            this.black = black;
            this.byeBoard = byeBoard;
            this.storedResult = result;
            this.resultProperty = new SimpleObjectProperty<>(result);
        }

        public long gameId() {
            return gameId;
        }

        public int board() {
            return board;
        }

        public String white() {
            return white;
        }

        public String black() {
            return black;
        }

        public boolean byeBoard() {
            return byeBoard;
        }

        public GameResult storedResult() {
            return storedResult;
        }

        public SimpleObjectProperty<GameResult> resultProperty() {
            return resultProperty;
        }
    }

    private record RefreshData(int displayRound, List<ResultRow> rows,
                               boolean editable, boolean canComplete, TournamentType type,
                               Integer nextPairableRound) {
    }
}
