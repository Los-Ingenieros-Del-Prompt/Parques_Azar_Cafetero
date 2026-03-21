package com.aguardientes.azarcafetero.parques_service.domain.events;

public class GameFinishedEvent {

    private final String gameId;
    private final String winnerId;

    public GameFinishedEvent(String gameId, String winnerId) {
        this.gameId = gameId;
        this.winnerId = winnerId;
    }

    public String getGameId() { return gameId; }
    public String getWinnerId() { return winnerId; }

    @Override
    public String toString() {
        return "GameFinishedEvent{gameId='" + gameId + "', winnerId='" + winnerId + "'}";
    }
}
