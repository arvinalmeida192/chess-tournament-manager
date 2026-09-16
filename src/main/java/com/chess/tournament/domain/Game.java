package com.chess.tournament.domain;

import com.chess.tournament.domain.enums.GameResult;

import java.math.BigDecimal;
import java.util.Objects;

public final class Game {

    private Long id;
    private long roundId;
    private int boardNumber;
    private Long whiteTournamentPlayerId;
    private Long blackTournamentPlayerId;
    private GameResult result;
    private BigDecimal whiteScore;
    private BigDecimal blackScore;
    private boolean rematch;
    private Integer whiteRatingDelta;
    private Integer blackRatingDelta;

    public Game() {
        this.result = GameResult.PENDING;
        this.rematch = false;
    }

    public Game(Long id, long roundId, int boardNumber, Long whiteTournamentPlayerId,
                Long blackTournamentPlayerId, GameResult result, BigDecimal whiteScore,
                BigDecimal blackScore, boolean rematch, Integer whiteRatingDelta,
                Integer blackRatingDelta) {
        this.id = id;
        this.roundId = roundId;
        this.boardNumber = boardNumber;
        this.whiteTournamentPlayerId = whiteTournamentPlayerId;
        this.blackTournamentPlayerId = blackTournamentPlayerId;
        this.result = result;
        this.whiteScore = whiteScore;
        this.blackScore = blackScore;
        this.rematch = rematch;
        this.whiteRatingDelta = whiteRatingDelta;
        this.blackRatingDelta = blackRatingDelta;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getRoundId() {
        return roundId;
    }

    public void setRoundId(long roundId) {
        this.roundId = roundId;
    }

    public int getBoardNumber() {
        return boardNumber;
    }

    public void setBoardNumber(int boardNumber) {
        this.boardNumber = boardNumber;
    }

    public Long getWhiteTournamentPlayerId() {
        return whiteTournamentPlayerId;
    }

    public void setWhiteTournamentPlayerId(Long whiteTournamentPlayerId) {
        this.whiteTournamentPlayerId = whiteTournamentPlayerId;
    }

    public Long getBlackTournamentPlayerId() {
        return blackTournamentPlayerId;
    }

    public void setBlackTournamentPlayerId(Long blackTournamentPlayerId) {
        this.blackTournamentPlayerId = blackTournamentPlayerId;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public BigDecimal getWhiteScore() {
        return whiteScore;
    }

    public void setWhiteScore(BigDecimal whiteScore) {
        this.whiteScore = whiteScore;
    }

    public BigDecimal getBlackScore() {
        return blackScore;
    }

    public void setBlackScore(BigDecimal blackScore) {
        this.blackScore = blackScore;
    }

    public boolean isRematch() {
        return rematch;
    }

    public void setRematch(boolean rematch) {
        this.rematch = rematch;
    }

    public Integer getWhiteRatingDelta() {
        return whiteRatingDelta;
    }

    public void setWhiteRatingDelta(Integer whiteRatingDelta) {
        this.whiteRatingDelta = whiteRatingDelta;
    }

    public Integer getBlackRatingDelta() {
        return blackRatingDelta;
    }

    public void setBlackRatingDelta(Integer blackRatingDelta) {
        this.blackRatingDelta = blackRatingDelta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Game game)) {
            return false;
        }
        return Objects.equals(id, game.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
