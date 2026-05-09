package com.aguardientes.azarcafetero.parques_service.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Game {

    private static final Set<Integer> SAFE_SQUARES = Set.of(0, 7, 12, 17, 24, 32, 39, 44, 49, 56, 60);
    private static final int COMMON_TRACK = 64;
    private static final int VICTORY = 100; // Placeholder, logic is now color-specific

    private final String id;
    private final List<Player> players;
    private int currentTurn;
    private int die1;
    private int die2;
    private int moveValue;
    private boolean jailExitAvailable;
    private boolean diceRolled;
    private GameState state;
    private String winnerId;

    public Game(String id, List<Player> players) {
        this.id = id;
        this.currentTurn = 0; // Host always starts
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.diceRolled = false;
        this.players = new ArrayList<>(players);
    }

    // ─── Roll ────────────────────────────────────────────────────────────────

    public void rollDice(String playerId) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("El juego no ha iniciado");
        validateTurn(playerId);
        if (diceRolled) throw new IllegalStateException("Ya lanzaste el dado, debes mover primero");

        Dice dice = new Dice();
        dice.roll();
        this.die1 = dice.getDie1();
        this.die2 = dice.getDie2();
        this.jailExitAvailable = false;

        Player player = findPlayer(playerId);

        if (dice.isPair()) {
            handlePairRoll(player, dice);
        } else {
            handleNormalRoll(player, dice);
        }
    }

    private void handlePairRoll(Player player, Dice dice) {
        player.incrementConsecutivePairs();

        if (player.getConsecutivePairs() >= 3) {
            Piece mostAdvanced = player.getMostAdvancedActivePiece();
            if (mostAdvanced != null) {
                mostAdvanced.sendHome();
            }
            player.resetConsecutivePairs();
            nextTurn();
            return;
        }

        List<Piece> inJail = player.getPiecesInJail();

        if (!inJail.isEmpty()) {
            this.jailExitAvailable = true;
        }
        this.moveValue = dice.getTotal();
        this.diceRolled = true;
    }

    private void handleNormalRoll(Player player, Dice dice) {
        player.resetConsecutivePairs();

        if (player.allPiecesInJail()) {
            boolean hasFive = die1 == 5 || die2 == 5;

            if (hasFive) {
                player.resetJailAttempts();
                this.moveValue = (die1 == 5) ? die2 : die1;
                this.jailExitAvailable = true;
                this.diceRolled = true;
            } else {
                player.incrementJailAttempts();
                if (player.hasExhaustedJailAttempts()) {
                    player.getPiecesInJail().get(0).exitJail();
                    player.resetJailAttempts();
                    this.moveValue = dice.getTotal();
                    this.diceRolled = true;
                } else {
                    nextTurn();
                }
            }
        } else if (player.hasAnyPieceInJail() && (die1 == 5 || die2 == 5)) {
            this.moveValue = (die1 == 5) ? die2 : die1;
            this.jailExitAvailable = true;
            this.diceRolled = true;
        } else {
            this.moveValue = dice.getTotal();
            this.diceRolled = true;
        }
    }

    // ─── Move ────────────────────────────────────────────────────────────────

    public void movePiece(String playerId, String pieceId) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("El juego no ha iniciado");
        validateTurn(playerId);
        if (!diceRolled) throw new IllegalStateException("Debes lanzar el dado primero");

        Player player = findPlayer(playerId);
        Piece piece = player.findPiece(pieceId);

        int effectiveMoveValue = resolveEffectiveMoveValue(player, piece);
        applyMove(player, piece, effectiveMoveValue);

        if (player.hasFinished()) {
            this.state = GameState.FINISHED;
            this.winnerId = playerId;
        }

        this.diceRolled = false;
        nextTurn();
    }

    private int resolveEffectiveMoveValue(Player player, Piece piece) {
        if (piece.isInJail()) {
            if (!jailExitAvailable) {
                throw new IllegalStateException("No puedes sacar esa ficha de la cárcel con este dado");
            }
            return moveValue;
        }
        if (jailExitAvailable) {
            return die1 + die2;
        }
        return moveValue;
    }

    private void applyMove(Player player, Piece piece, int steps) {
        if (piece.isInJail()) {
            boolean isPair = die1 == die2;
            if (isPair) {
                // Rule: Special pairs (1-1, 6-6) exit ALL. Others exit 2.
                boolean isSpecial = die1 == 1 || die1 == 6;
                List<Piece> inJail = player.getPiecesInJail();
                if (isSpecial) {
                    inJail.forEach(Piece::exitJail);
                } else {
                    inJail.stream().limit(2).forEach(Piece::exitJail);
                }
            } else {
                piece.exitJail();
            }
            checkCaptures(player, piece);
            return;
        }

        boolean killWasPossible = hasKillOpportunity(player, steps);
        piece.move(steps);
        boolean killed = checkCaptures(player, piece);

        if (killWasPossible && !killed) {
            piece.sendToJail();
        }
    }

    private boolean checkCaptures(Player currentPlayer, Piece movedPiece) {
        int pos = movedPiece.getAbsolutePosition();
        if (pos < 0 || movedPiece.isAtVictory() || movedPiece.isOnLadder() || SAFE_SQUARES.contains(pos)) return false;

        boolean captured = false;
        for (Player opponent : players) {
            if (opponent.getId().equals(currentPlayer.getId())) continue;
            for (Piece op : opponent.getPieces()) {
                if (!op.isInJail() && !op.isAtVictory() && !op.isOnLadder() && op.getAbsolutePosition() == pos) {
                    op.sendToJail();
                    captured = true;
                    break;
                }
            }
            if (captured) break;
        }
        return captured;
    }

    private boolean hasKillOpportunity(Player player, int steps) {
        for (Piece piece : player.getActivePieces()) {
            int targetPos = piece.getAbsolutePositionAfterMove(steps);
            if (targetPos < 0 || piece.isOnLadder() || SAFE_SQUARES.contains(targetPos)) continue;
            if (hasOpponentAt(player.getId(), targetPos)) return true;
        }
        return false;
    }

    private boolean hasOpponentAt(String currentPlayerId, int absPos) {
        for (Player opponent : players) {
            if (opponent.getId().equals(currentPlayerId)) continue;
            for (Piece p : opponent.getPieces()) {
                if (!p.isInJail() && !p.isAtVictory() && !p.isOnLadder() && p.getAbsolutePosition() == absPos) return true;
            }
        }
        return false;
    }

    // ─── Turn ────────────────────────────────────────────────────────────────

    public void nextTurn() {
        currentTurn = (currentTurn + 1) % players.size();
    }

    private void validateTurn(String playerId) {
        if (!players.get(currentTurn).getId().equals(playerId)) {
            throw new IllegalStateException("No es tu turno");
        }
    }

    private Player findPlayer(String playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado: " + playerId));
    }

    public void addPlayer(Player player) {
        if (state != GameState.WAITING_FOR_PLAYERS) throw new IllegalStateException("El juego ya inició");
        if (players.size() >= 4) throw new IllegalStateException("El juego ya tiene 4 jugadores");
        players.add(player);
    }

    public void start() {
        if (players.size() < 2) throw new IllegalStateException("Se necesitan al menos 2 jugadores");
        if (state != GameState.WAITING_FOR_PLAYERS) throw new IllegalStateException("El juego ya inició");
        this.state = GameState.IN_PROGRESS;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public int getDie1() { return die1; }
    public int getDie2() { return die2; }
    public int getMoveValue() { return moveValue; }
    public boolean isJailExitAvailable() { return jailExitAvailable; }
    public boolean isDiceRolled() { return diceRolled; }
    public int getCurrentTurn() { return currentTurn; }
    public List<Player> getPlayers() { return players; }
    public GameState getState() { return state; }
    public boolean isFinished() { return state == GameState.FINISHED; }
    public String getWinnerId() { return winnerId; }
    public Player getCurrentPlayer() { return players.get(currentTurn); }
}
