package com.aguardientes.azarcafetero.parques_service.domain.model;

public class Piece {

    private static final int JAIL = -1;
    public static final int HOME = 68;
    private static final int BOARD_SIZE = 68;

    private final String id;
    private final int exitAbsolutePosition;
    private int relativePosition;

    public Piece(String id, int exitAbsolutePosition) {
        this.id = id;
        this.exitAbsolutePosition = exitAbsolutePosition;
        this.relativePosition = JAIL;
    }

    public boolean isInJail() { return relativePosition == JAIL; }
    public boolean isAtHome() { return relativePosition >= HOME; }

    public void exitJail() {
        if (!isInJail()) throw new IllegalStateException("La ficha no está en la cárcel");
        this.relativePosition = 0;
    }

    public void move(int steps) {
        if (isInJail()) throw new IllegalStateException("La ficha está en la cárcel");
        int newPos = relativePosition + steps;
        if (newPos > HOME) {
            throw new IllegalStateException(
                "Necesitas exactamente " + (HOME - relativePosition) + " para entrar a la casa"
            );
        }
        this.relativePosition = newPos;
    }

    public boolean canMove(int steps) {
        if (isInJail() || isAtHome()) return false;
        return relativePosition + steps <= HOME;
    }

    public void sendToJail() { this.relativePosition = JAIL; }

    public void sendHome() { this.relativePosition = HOME; }

    public int getAbsolutePosition() {
        if (isInJail()) return JAIL;
        if (isAtHome()) return HOME;
        return (exitAbsolutePosition + relativePosition) % BOARD_SIZE;
    }

    public int getAbsolutePositionAfterMove(int steps) {
        if (isInJail() || isAtHome()) return -1;
        int newRelPos = relativePosition + steps;
        if (newRelPos > HOME) return -1;
        if (newRelPos == HOME) return HOME;
        return (exitAbsolutePosition + newRelPos) % BOARD_SIZE;
    }

    public String getId() { return id; }
    public int getRelativePosition() { return relativePosition; }
    public int getExitAbsolutePosition() { return exitAbsolutePosition; }
}
