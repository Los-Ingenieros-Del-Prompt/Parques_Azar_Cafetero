package com.aguardientes.azarcafetero.parques_service.domain.model;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class Game {

    private static final Set<Integer> SAFE_SQUARES = Set.of(8, 25, 42, 59);
    private static final int BOARD_SIZE = 68;

    private final String id;
    private final List<Player> players;
    private int currentTurn;
    private int die1;
    private int die2;
    private int moveValue;
    private boolean jailExitAvailable;
    private boolean diceRolled;
    private boolean finished;
    private String winnerId;

    public Game(String id, List<Player> players) {
        this.id = id;
        this.players = players;
        this.currentTurn = new Random().nextInt(players.size());
        this.finished = false;
        this.diceRolled = false;
    }

    // ─── Roll ────────────────────────────────────────────────────────────────

    public void rollDice(String playerId) {
        validateTurn(playerId);
        if (diceRolled) throw new IllegalStateException("Ya lanzaste el dado, debes mover primero");
        if (finished) throw new IllegalStateException("El juego ya terminó");

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
            if (dice.isSpecialPair()) {
                inJail.forEach(Piece::exitJail);
                this.moveValue = dice.getTotal();
            } else if (inJail.size() == 1) {
                inJail.get(0).exitJail();
                this.moveValue = die1;
            } else {
                inJail.get(0).exitJail();
                inJail.get(1).exitJail();
                this.moveValue = dice.getTotal();
            }
        } else {
            this.moveValue = dice.getTotal();
        }

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
        validateTurn(playerId);
        if (!diceRolled) throw new IllegalStateException("Debes lanzar el dado primero");
        if (finished) throw new IllegalStateException("El juego ya terminó");

        Player player = findPlayer(playerId);
        Piece piece = player.findPiece(pieceId);

        int effectiveMoveValue = resolveEffectiveMoveValue(player, piece);
        applyMove(player, piece, effectiveMoveValue);

        if (player.hasFinished()) {
            this.finished = true;
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
            piece.exitJail();
            if (steps > 0 && piece.canMove(steps)) {
                piece.move(steps);
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
        if (pos < 0 || movedPiece.isAtHome() || SAFE_SQUARES.contains(pos)) return false;

        boolean captured = false;
        for (Player opponent : players) {
            if (opponent.getId().equals(currentPlayer.getId())) continue;
            for (Piece op : opponent.getPieces()) {
                if (!op.isInJail() && !op.isAtHome() && op.getAbsolutePosition() == pos) {
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
            if (targetPos < 0 || SAFE_SQUARES.contains(targetPos)) continue;
            if (hasOpponentAt(player.getId(), targetPos)) return true;
        }
        return false;
    }

    private boolean hasOpponentAt(String currentPlayerId, int absPos) {
        for (Player opponent : players) {
            if (opponent.getId().equals(currentPlayerId)) continue;
            for (Piece p : opponent.getPieces()) {
                if (!p.isInJail() && !p.isAtHome() && p.getAbsolutePosition() == absPos) return true;
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

    // ─── Getters ─────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public int getDie1() { return die1; }
    public int getDie2() { return die2; }
    public int getMoveValue() { return moveValue; }
    public boolean isJailExitAvailable() { return jailExitAvailable; }
    public boolean isDiceRolled() { return diceRolled; }
    public int getCurrentTurn() { return currentTurn; }
    public List<Player> getPlayers() { return players; }
    public boolean isFinished() { return finished; }
    public String getWinnerId() { return winnerId; }
    public Player getCurrentPlayer() { return players.get(currentTurn); }
}
