package com.aguardientes.azarcafetero.parques_service.domain.events;

public class PieceMovedEvent {

    private final String gameId;
    private final String playerId;
    private final String pieceId;
    private final boolean gameFinished;
    private final boolean byPairPrize;

    public PieceMovedEvent(String gameId, String playerId, String pieceId,
                           boolean gameFinished, boolean byPairPrize) {
        this.gameId = gameId;
        this.playerId = playerId;
        this.pieceId = pieceId;
        this.gameFinished = gameFinished;
        this.byPairPrize = byPairPrize;
    }

    public String getGameId() { return gameId; }
    public String getPlayerId() { return playerId; }
    public String getPieceId() { return pieceId; }
    public boolean isGameFinished() { return gameFinished; }
    public boolean isByPairPrize() { return byPairPrize; }

    @Override
    public String toString() {
        return "PieceMovedEvent{gameId='" + gameId + "', playerId='" + playerId
                + "', pieceId='" + pieceId + "', finished=" + gameFinished
                + ", byPairPrize=" + byPairPrize + "}";
    }
}
