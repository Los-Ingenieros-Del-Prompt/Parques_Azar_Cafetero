package com.aguardientes.azarcafetero.parques_service.domain.model;

public class Piece {

    private static final int JAIL = -1;
    private static final int COMMON_TRACK = 68;
    private static final int LADDER_START = 68;
    public static final int VICTORY = 76;
    private static final int LADDER_SIZE = 8;

    private final String id;
    private final int exitAbsolutePosition;
    private int relativePosition;

    public Piece(String id, int exitAbsolutePosition) {
        this.id = id;
        this.exitAbsolutePosition = exitAbsolutePosition;
        this.relativePosition = JAIL;
    }

    public boolean isInJail() { return relativePosition == JAIL; }
    public boolean isAtVictory() { return relativePosition == VICTORY; }
    public boolean isOnLadder() { return relativePosition >= LADDER_START && relativePosition < VICTORY; }

    public void exitJail() {
        if (!isInJail()) throw new IllegalStateException("La ficha no está en la cárcel");
        this.relativePosition = 0;
    }

    public void move(int steps) {
        if (isInJail()) throw new IllegalStateException("La ficha está en la cárcel");
        int newPos = relativePosition + steps;
        if (newPos > VICTORY) {
            throw new IllegalStateException(
                "Necesitas exactamente " + (VICTORY - relativePosition) + " para llegar a la victoria"
            );
        }
        this.relativePosition = newPos;
    }

    public boolean canMove(int steps) {
        if (isInJail() || isAtVictory()) return false;
        return relativePosition + steps <= VICTORY;
    }

    public void sendToJail() { this.relativePosition = JAIL; }

    public void sendHome() { this.relativePosition = LADDER_START; }

    public int getAbsolutePosition() {
        if (isInJail()) return JAIL;
        if (isAtVictory()) return VICTORY;
        if (isOnLadder()) return relativePosition;
        return (exitAbsolutePosition + relativePosition) % COMMON_TRACK;
    }

    public int getAbsolutePositionAfterMove(int steps) {
        if (isInJail() || isAtVictory()) return -1;
        int newRelPos = relativePosition + steps;
        if (newRelPos > VICTORY) return -1;
        if (newRelPos == VICTORY) return VICTORY;
        if (newRelPos >= LADDER_START) return newRelPos;
        return (exitAbsolutePosition + newRelPos) % COMMON_TRACK;
    }

    public String getId() { return id; }
    public int getRelativePosition() { return relativePosition; }
    public int getExitAbsolutePosition() { return exitAbsolutePosition; }
}
