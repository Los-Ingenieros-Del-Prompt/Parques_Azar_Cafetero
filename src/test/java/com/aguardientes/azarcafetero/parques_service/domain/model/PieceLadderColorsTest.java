package com.aguardientes.azarcafetero.parques_service.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests adicionales para Piece.java enfocados en mapRelToAbs():
 *
 *  - switch de la escalera (rel 63..69) por color:
 *      AZUL     → 68..74
 *      ROJO     → 75..81
 *      VERDE    → 82..88
 *      AMARILLO → 89..95
 *      default  → rel
 *
 *  - switch de victoria (rel == 70) caso default (color desconocido).
 *
 *  Los tests existentes en PieceTest.java solo cubren AZUL en la escalera
 *  (vía absolutePosition_shouldWrapAroundCommonTrack es del anillo común) y
 *  los 4 colores en victoria (rel=70), pero NO la rama default de victoria
 *  ni las 4 ramas de la escalera.
 */
class PieceLadderColorsTest {

    // ─── Escalera por color (rel 63..69, ladderRel = rel - 63) ─────────────────

    @Test
    void azul_atLadderEntry_shouldMapTo68() {
        Piece azul = new Piece("p-azul-0", "AZUL", 21);
        azul.exitJail();
        azul.move(63); // rel=63 → ladderRel=0 → AZUL = 68 + 0 = 68
        assertTrue(azul.isOnLadder());
        assertEquals(68, azul.getAbsolutePosition());
    }

    @Test
    void azul_atLadderEnd_shouldMapTo74() {
        Piece azul = new Piece("p-azul-1", "AZUL", 21);
        azul.exitJail();
        azul.move(69); // ladderRel=6 → 68+6 = 74
        assertTrue(azul.isOnLadder());
        assertEquals(74, azul.getAbsolutePosition());
    }

    @Test
    void rojo_atLadderEntry_shouldMapTo75() {
        Piece rojo = new Piece("p-rojo-0", "ROJO", 38);
        rojo.exitJail();
        rojo.move(63); // ladderRel=0 → ROJO = 75
        assertTrue(rojo.isOnLadder());
        assertEquals(75, rojo.getAbsolutePosition());
    }

    @Test
    void rojo_atLadderEnd_shouldMapTo81() {
        Piece rojo = new Piece("p-rojo-1", "ROJO", 38);
        rojo.exitJail();
        rojo.move(69); // ladderRel=6 → 75+6 = 81
        assertEquals(81, rojo.getAbsolutePosition());
    }

    @Test
    void verde_atLadderEntry_shouldMapTo82() {
        Piece verde = new Piece("p-verde-0", "VERDE", 55);
        verde.exitJail();
        verde.move(63); // ladderRel=0 → VERDE = 82
        assertTrue(verde.isOnLadder());
        assertEquals(82, verde.getAbsolutePosition());
    }

    @Test
    void verde_atLadderEnd_shouldMapTo88() {
        Piece verde = new Piece("p-verde-1", "VERDE", 55);
        verde.exitJail();
        verde.move(69); // ladderRel=6 → 82+6 = 88
        assertEquals(88, verde.getAbsolutePosition());
    }

    @Test
    void amarillo_atLadderEntry_shouldMapTo89() {
        Piece amarillo = new Piece("p-amarillo-0", "AMARILLO", 4);
        amarillo.exitJail();
        amarillo.move(63); // ladderRel=0 → AMARILLO = 89
        assertTrue(amarillo.isOnLadder());
        assertEquals(89, amarillo.getAbsolutePosition());
    }

    @Test
    void amarillo_atLadderMiddle_shouldMapTo92() {
        Piece amarillo = new Piece("p-amarillo-1", "AMARILLO", 4);
        amarillo.exitJail();
        amarillo.move(66); // ladderRel=3 → 89+3 = 92
        assertEquals(92, amarillo.getAbsolutePosition());
    }

    // ─── Default cases ─────────────────────────────────────────────────────────

    @Test
    void unknownColor_atVictory_shouldReturnMinusOne() {
        // Cubre rama default del switch de victoria (rel == 70)
        Piece unknown = new Piece("p-unknown-0", "DESCONOCIDO", 4);
        unknown.exitJail();
        unknown.move(70);
        assertTrue(unknown.isAtVictory());
        assertEquals(-1, unknown.getAbsolutePosition());
    }

    @Test
    void unknownColor_atLadder_shouldReturnRelativePosition() {
        // Cubre rama default del switch de la escalera
        Piece unknown = new Piece("p-unknown-1", "FUCSIA", 4);
        unknown.exitJail();
        unknown.move(65); // rel=65, default → rel = 65
        assertEquals(65, unknown.getAbsolutePosition());
    }
}
