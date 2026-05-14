package com.aguardientes.azarcafetero.parques_service.domain.model;

public class Piece {

    private static final int JAIL = -1;
    private static final int COMMON_TRACK = 68;
    private static final int THRESHOLD = 63; // casillas del anillo antes de la escalera
    private static final int VICTORY_RELATIVE = 70; // 63 + 7

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

    public int getVictoryRelative() {
        return VICTORY_RELATIVE;
    }

    private int getCommonTrackThreshold() {
        return THRESHOLD;
    }

    public boolean isInJail()    { return relativePosition == JAIL; }
    public boolean isAtVictory() { return relativePosition == VICTORY_RELATIVE; }
    public boolean isOnLadder()  { return relativePosition >= THRESHOLD && relativePosition < VICTORY_RELATIVE; }

    public void exitJail() {
        if (!isInJail()) throw new IllegalStateException("La ficha no está en la cárcel");
        this.relativePosition = 0;
    }

    public void move(int steps) {
        if (isInJail()) throw new IllegalStateException("La ficha está en la cárcel");
        int newPos = relativePosition + steps;
        if (newPos > VICTORY_RELATIVE) {
            throw new IllegalStateException(
                    "Necesitas exactamente " + (VICTORY_RELATIVE - relativePosition) + " para llegar a la victoria");
        }
        this.relativePosition = newPos;
    }

    public boolean canMove(int steps) {
        if (isInJail() || isAtVictory()) return false;
        // FIX: debe ser <= VICTORY_RELATIVE (70), no un número menor
        return relativePosition + steps <= VICTORY_RELATIVE;
    }

    public void sendToJail() {
        this.relativePosition = JAIL;
    }

    /**
     * FIX Bug 4: Por castigo de 3 pares consecutivos, la ficha más avanzada
     * va a la CÁRCEL, no al inicio de la escalera.
     * La regla colombiana es clara: 3 pares = te mandan preso.
     */
    public void sendHome() {
        this.relativePosition = JAIL;
    }

    public int getAbsolutePosition() {
        return mapRelToAbs(relativePosition);
    }

    public int getAbsolutePositionAfterMove(int steps) {
        if (isInJail() || isAtVictory()) return -1;
        int newRel = relativePosition + steps;
        if (newRel > VICTORY_RELATIVE) return -1;
        return mapRelToAbs(newRel);
    }

    private int mapRelToAbs(int rel) {
        if (rel == JAIL) return JAIL;

        // Victoria
        if (rel == VICTORY_RELATIVE) {
            return switch (color) {
                case "AMARILLO" -> 112;
                case "AZUL"     -> 113;
                case "ROJO"     -> 114;
                case "VERDE"    -> 115;
                default         -> -1;
            };
        }

        // Anillo común (0–67)
        if (rel < THRESHOLD) {
            return (exitAbsolutePosition + rel) % COMMON_TRACK;
        }

        // Escalera (7 casillas)
        int ladderRel = rel - THRESHOLD;
        return switch (color) {
            case "AZUL"     -> 68 + ladderRel; // 68–74
            case "ROJO"     -> 75 + ladderRel; // 75–81
            case "VERDE"    -> 82 + ladderRel; // 82–88
            case "AMARILLO" -> 89 + ladderRel; // 89–95
            default         -> rel;
        };
    }

    public String getId() { return id; }
    public int getRelativePosition() { return relativePosition; }
    public int getExitAbsolutePosition() { return exitAbsolutePosition; }
}