package com.aguardientes.azarcafetero.parques_service.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Player {

    private static final int PIECES_PER_PLAYER = 4;
    private static final int MAX_JAIL_ATTEMPTS = 3;

    private final String id;
    private final String name;
    private final String color;
    private final int exitPosition;
    private final List<Piece> pieces;
    private int jailAttempts;
    private int consecutivePairs;

    public Player(String id, String name, String color, int exitPosition) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.exitPosition = exitPosition;
        this.jailAttempts = 0;
        this.consecutivePairs = 0;
        this.pieces = new ArrayList<>();
        for (int i = 0; i < PIECES_PER_PLAYER; i++) {
            pieces.add(new Piece(id + "-piece-" + i, color, exitPosition));
        }
    }

    public boolean hasFinished() {
        return pieces.stream().allMatch(Piece::isAtVictory);
    }

    public boolean allPiecesInJail() {
        return pieces.stream().allMatch(Piece::isInJail);
    }

    public boolean hasAnyPieceInJail() {
        return pieces.stream().anyMatch(Piece::isInJail);
    }

    public List<Piece> getPiecesInJail() {
        return pieces.stream().filter(Piece::isInJail).toList();
    }

    public List<Piece> getActivePieces() {
        return pieces.stream().filter(p -> !p.isInJail() && !p.isAtVictory()).toList();
    }

    public Piece getMostAdvancedActivePiece() {
        return getActivePieces().stream()
                .max(Comparator.comparingInt(Piece::getRelativePosition))
                .orElse(null);
    }

    public Piece findPiece(String pieceId) {
        return pieces.stream()
                .filter(p -> p.getId().equals(pieceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ficha no encontrada: " + pieceId));
    }

    public void incrementJailAttempts() { this.jailAttempts++; }
    public void resetJailAttempts() { this.jailAttempts = 0; }
    public boolean hasExhaustedJailAttempts() { return jailAttempts >= MAX_JAIL_ATTEMPTS; }

    public void incrementConsecutivePairs() { this.consecutivePairs++; }
    public void resetConsecutivePairs() { this.consecutivePairs = 0; }

    public int getJailAttemptsRemaining() {
        return allPiecesInJail() ? Math.max(0, MAX_JAIL_ATTEMPTS - jailAttempts) : 0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public int getExitPosition() { return exitPosition; }
    public List<Piece> getPieces() { return pieces; }
    public int getJailAttempts() { return jailAttempts; }
    public int getConsecutivePairs() { return consecutivePairs; }
}
