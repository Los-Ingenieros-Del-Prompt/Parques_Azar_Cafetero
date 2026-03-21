package com.aguardientes.azarcafetero.parques_service.domain.events;

public class DiceRolledEvent {

    private final String gameId;
    private final String playerId;
    private final int die1;
    private final int die2;

    public DiceRolledEvent(String gameId, String playerId, int die1, int die2) {
        this.gameId = gameId;
        this.playerId = playerId;
        this.die1 = die1;
        this.die2 = die2;
    }

    public String getGameId() { return gameId; }
    public String getPlayerId() { return playerId; }
    public int getDie1() { return die1; }
    public int getDie2() { return die2; }
    public int getTotal() { return die1 + die2; }
    public boolean isPair() { return die1 == die2; }

    @Override
    public String toString() {
        return "DiceRolledEvent{gameId='" + gameId + "', playerId='" + playerId
                + "', die1=" + die1 + ", die2=" + die2 + ", pair=" + isPair() + "}";
    }
}
