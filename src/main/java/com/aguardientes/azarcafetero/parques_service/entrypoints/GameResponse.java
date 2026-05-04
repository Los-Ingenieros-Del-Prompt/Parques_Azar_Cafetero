package com.aguardientes.azarcafetero.parques_service.entrypoints;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Piece;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;

import java.util.List;

public class GameResponse {

    private String gameId;
    private String currentPlayerId;
    private int die1;
    private int die2;
    private int moveValue;
    private boolean diceRolled;
    private boolean jailExitAvailable;
    private boolean finished;
    private String winnerId;
    private List<PlayerResponse> players;

    public static GameResponse from(Game game) {
        GameResponse r = new GameResponse();
        r.gameId = game.getId();
        r.currentPlayerId = game.getCurrentPlayer().getId();
        r.die1 = game.getDie1();
        r.die2 = game.getDie2();
        r.moveValue = game.getMoveValue();
        r.diceRolled = game.isDiceRolled();
        r.jailExitAvailable = game.isJailExitAvailable();
        r.finished = game.isFinished();
        r.winnerId = game.getWinnerId();
        r.players = game.getPlayers().stream().map(PlayerResponse::from).toList();
        return r;
    }

    public String getGameId() { return gameId; }
    public String getCurrentPlayerId() { return currentPlayerId; }
    public int getDie1() { return die1; }
    public int getDie2() { return die2; }
    public int getMoveValue() { return moveValue; }
    public boolean isDiceRolled() { return diceRolled; }
    public boolean isJailExitAvailable() { return jailExitAvailable; }
    public boolean isFinished() { return finished; }
    public String getWinnerId() { return winnerId; }
    public List<PlayerResponse> getPlayers() { return players; }

    public static class PlayerResponse {
        private String id;
        private String name;
        private String color;
        private int jailAttemptsRemaining;
        private int consecutivePairs;
        private List<PieceResponse> pieces;

        public static PlayerResponse from(Player player) {
            PlayerResponse r = new PlayerResponse();
            r.id = player.getId();
            r.name = player.getName();
            r.color = player.getColor();
            r.jailAttemptsRemaining = player.getJailAttemptsRemaining();
            r.consecutivePairs = player.getConsecutivePairs();
            r.pieces = player.getPieces().stream().map(PieceResponse::from).toList();
            return r;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getColor() { return color; }
        public int getJailAttemptsRemaining() { return jailAttemptsRemaining; }
        public int getConsecutivePairs() { return consecutivePairs; }
        public List<PieceResponse> getPieces() { return pieces; }
    }

    public static class PieceResponse {
        private String id;
        private int absolutePosition;
        private int relativePosition;
        private boolean inJail;
        private boolean atVictory;

        public static PieceResponse from(Piece piece) {
            PieceResponse r = new PieceResponse();
            r.id = piece.getId();
            r.absolutePosition = piece.getAbsolutePosition();
            r.relativePosition = piece.getRelativePosition();
            r.inJail = piece.isInJail();
            r.atVictory = piece.isAtVictory();
            return r;
        }

        public String getId() { return id; }
        public int getAbsolutePosition() { return absolutePosition; }
        public int getRelativePosition() { return relativePosition; }
        public boolean isInJail() { return inJail; }
        public boolean isAtVictory() { return atVictory; }
    }
}
