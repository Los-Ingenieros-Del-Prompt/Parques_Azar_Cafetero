package com.aguardientes.azarcafetero.parques_service.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Game {

    
    private static final Set<Integer> SAFE_SQUARES = Set.of(4, 11, 21, 28, 38, 45, 55, 62);
    private static final int COMMON_TRACK = 68;

    private final String id;
    private final List<Player> players;
    private int currentTurn;
    private int die1;
    private int die2;
    private int moveValue;
    private boolean jailExitAvailable;
    private boolean diceRolled;
    private boolean die1Used;
    private boolean die2Used;
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
        this.die1Used = false;
        this.die2Used = false;
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
        player.resetJailAttempts();

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
        this.moveValue = dice.getTotal();
        this.diceRolled = true;
    }

    public void passTurn(String playerId) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("El juego no ha iniciado");
        validateTurn(playerId);
        if (!diceRolled) throw new IllegalStateException("Aún no has lanzado el dado");

        if (canPlayerMoveAnyPiece()) {
            throw new IllegalStateException("Tienes movimientos válidos, no puedes pasar");
        }

        Player player = findPlayer(playerId);

        if (player.allPiecesInJail() && die1 != die2) {
            player.incrementJailAttempts();
            if (player.hasExhaustedJailAttempts()) {
                player.resetJailAttempts();
                this.diceRolled = false;
                nextTurn();
            } else {
                this.diceRolled = false; 
            }
        } else {
            this.diceRolled = false;
            if (die1 != die2) {
                nextTurn();
            }
        }
    }

    // ─── Move ────────────────────────────────────────────────────────────────

    /**
     * Mueve una ficha usando el dado seleccionado.
     * @param diceSelection 1: Dado 1, 2: Dado 2, 3: Ambos (suma)
     */
    public void movePiece(String playerId, String pieceId, int diceSelection) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("El juego no ha iniciado");
        validateTurn(playerId);
        if (!diceRolled) throw new IllegalStateException("Debes lanzar el dado primero");

        Player player = findPlayer(playerId);
        Piece piece = player.findPiece(pieceId);

        // Validar selección de dados
        validateDiceSelection(diceSelection);

        int steps = calculateSteps(diceSelection);
        applyMove(player, piece, steps);

        // Marcar dados como usados
        updateDiceUsage(diceSelection);

        if (player.hasFinished()) {
            this.state = GameState.FINISHED;
            this.winnerId = playerId;
            return;
        }

        // Finalizar turno o permitir más movimientos
        checkTurnFinalization();
    }

    private void validateDiceSelection(int selection) {
        if (selection == 1 && die1Used) throw new IllegalStateException("El dado 1 ya fue usado");
        if (selection == 2 && die2Used) throw new IllegalStateException("El dado 2 ya fue usado");
        if (selection == 3 && (die1Used || die2Used)) throw new IllegalStateException("Uno de los dados ya fue usado, no puedes usar la suma");
    }

    private int calculateSteps(int selection) {
        return switch (selection) {
            case 1 -> die1;
            case 2 -> die2;
            case 3 -> die1 + die2;
            default -> throw new IllegalArgumentException("Selección de dados inválida");
        };
    }

    private void updateDiceUsage(int selection) {
        if (selection == 1) die1Used = true;
        else if (selection == 2) die2Used = true;
        else if (selection == 3) {
            die1Used = true;
            die2Used = true;
        }
    }

    private void checkTurnFinalization() {
        // Si ambos dados están usados, el turno termina
        if (die1Used && die2Used) {
            this.diceRolled = false;
            // Regla: Si sacó par, tiene otro turno (no llamamos a nextTurn)
            if (die1 != die2) {
                nextTurn();
            }
        } else {
            // Aún queda un dado, verificar si el jugador PUEDE moverlo
            if (!canPlayerMoveAnyPiece()) {
                this.diceRolled = false;
                if (die1 != die2) {
                    nextTurn();
                }
            }
        }
    }

    private boolean canPlayerMoveAnyPiece() {
        Player player = players.get(currentTurn);
        boolean d1 = !die1Used;
        boolean d2 = !die2Used;
        boolean dSum = d1 && d2;
        
        if (player.hasAnyPieceInJail() && jailExitAvailable) {
            return true;
        }

        return player.getPieces().stream().anyMatch(p -> {
            if (p.isInJail() || p.isAtVictory()) return false;
            if (d1 && p.canMove(die1)) return true;
            if (d2 && p.canMove(die2)) return true;
            if (dSum && p.canMove(die1 + die2)) return true;
            return false;
        });
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

    public void exitJail(String playerId) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("El juego no ha iniciado");
        validateTurn(playerId);
        if (!diceRolled) throw new IllegalStateException("Aún no has lanzado el dado");
        if (die1 != die2) throw new IllegalStateException("Solo puedes salir automáticamente con un par");
        if (die1Used || die2Used) throw new IllegalStateException("Los dados ya fueron usados");

        Player player = findPlayer(playerId);
        List<Piece> inJail = player.getPiecesInJail();
        if (inJail.isEmpty()) throw new IllegalStateException("No tienes fichas en la cárcel");

        // Release exactly up to 2 pieces
        inJail.stream().limit(2).forEach(Piece::exitJail);

        // Consume both dice
        this.die1Used = true;
        this.die2Used = true;
        this.jailExitAvailable = false;

        checkTurnFinalization();
    }

    private void applyMove(Player player, Piece piece, int steps) {
        if (piece.isInJail()) {
            piece.exitJail();
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
    public boolean isDie1Used() { return die1Used; }
    public boolean isDie2Used() { return die2Used; }
    public int getCurrentTurn() { return currentTurn; }
    public List<Player> getPlayers() { return players; }
    public GameState getState() { return state; }
    public boolean isFinished() { return state == GameState.FINISHED; }
    public String getWinnerId() { return winnerId; }
    public Player getCurrentPlayer() { return players.get(currentTurn); }
}
