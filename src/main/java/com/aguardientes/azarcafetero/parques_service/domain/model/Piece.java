package com.aguardientes.azarcafetero.parques_service.domain.model;

public class Piece {

    private static final int JAIL = -1;
    private static final int COMMON_TRACK = 64;
    private static final int LADDER_START = 64;

    private final String id;
    private final String color;
    private final int exitAbsolutePosition;
    private int relativePosition;

    public Piece(String id, String color, int exitAbsolutePosition) {
        this.id = id;
        this.color = color;
        this.exitAbsolutePosition = exitAbsolutePosition;
        this.relativePosition = JAIL;
    }

    /**
     * Retorna la posición RELATIVA necesaria para ganar (distancia total desde la
     * salida).
     */
    public int getVictoryRelative() {
        return switch (color) {
            case "AMARILLO" -> 70; // 64 (track) + (70-64 ladder)
            case "AZUL" -> 71; // 64 (track) + (78-71 ladder)
            case "VERDE" -> 74; // 64 (track) + (89-79 ladder)
            case "ROJO" -> 71; // 64 (track) + (97-90 ladder)
            default -> 100;
        };
    }

    public boolean isInJail() {
        return relativePosition == JAIL;
    }

    public boolean isAtVictory() {
        return relativePosition == getVictoryRelative();
    }

    public boolean isOnLadder() {
        return relativePosition >= LADDER_START;
    }

    public void exitJail() {
        if (!isInJail())
            throw new IllegalStateException("La ficha no está en la cárcel");
        this.relativePosition = 0;
    }

    public void move(int steps) {
        if (isInJail())
            throw new IllegalStateException("La ficha está en la cárcel");
        int newPos = relativePosition + steps;
        int victoryRel = getVictoryRelative();
        if (newPos > victoryRel) {
            throw new IllegalStateException(
                    "Necesitas exactamente " + (victoryRel - relativePosition) + " para llegar a la victoria");
        }
        this.relativePosition = newPos;
    }

    public boolean canMove(int steps) {
        if (isInJail() || isAtVictory())
            return false;
        return relativePosition + steps <= getVictoryRelative();
    }

    public void sendToJail() {
        this.relativePosition = JAIL;
    }

    public void sendHome() {
        this.relativePosition = LADDER_START;
    }

    public int getAbsolutePosition() {
        if (isInJail())
            return JAIL;
        if (relativePosition < COMMON_TRACK) {
            return (exitAbsolutePosition + relativePosition) % COMMON_TRACK;
        }
        // Asymmetric Ladder mapping
        int ladderRelative = relativePosition - LADDER_START;
        return switch (color) {
            case "AMARILLO" -> 64 + ladderRelative;
            case "AZUL" -> 71 + ladderRelative;
            case "VERDE" -> 79 + ladderRelative;
            case "ROJO" -> 90 + ladderRelative;
            default -> relativePosition;
        };
    }

    public int getAbsolutePositionAfterMove(int steps) {
        if (isInJail() || isAtVictory())
            return -1;
        int newRelPos = relativePosition + steps;
        int victoryRel = getVictoryRelative();
        if (newRelPos > victoryRel)
            return -1;

        if (newRelPos < COMMON_TRACK) {
            return (exitAbsolutePosition + newRelPos) % COMMON_TRACK;
        }

        int ladderRelative = newRelPos - LADDER_START;
        return switch (color) {
            case "AMARILLO" -> 64 + ladderRelative;
            case "AZUL" -> 71 + ladderRelative;
            case "VERDE" -> 79 + ladderRelative;
            case "ROJO" -> 90 + ladderRelative;
            default -> newRelPos;
        };
    }

    public String getId() {
        return id;
    }

    public int getRelativePosition() {
        return relativePosition;
    }

    public int getExitAbsolutePosition() {
        return exitAbsolutePosition;
    }
}
