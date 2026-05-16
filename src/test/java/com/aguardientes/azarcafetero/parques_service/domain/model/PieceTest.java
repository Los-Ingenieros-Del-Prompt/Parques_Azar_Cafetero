package com.aguardientes.azarcafetero.parques_service.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class PieceTest {

    private Piece piece;

    @BeforeEach
    void setUp() {
        // AMARILLO sale en posición absoluta 4
        piece = new Piece("p1-piece-0", "AMARILLO", 4);
    }

    // ─── Estado inicial ───────────────────────────────────────────────────────

    @Test
    void shouldStartInJail() {
        assertTrue(piece.isInJail());
    }

    @Test
    void shouldNotBeAtVictoryInitially() {
        assertFalse(piece.isAtVictory());
    }

    @Test
    void shouldNotBeOnLadderInitially() {
        assertFalse(piece.isOnLadder());
    }

    @Test
    void shouldHaveNegativeAbsolutePositionWhenInJail() {
        assertEquals(-1, piece.getAbsolutePosition());
    }

    // ─── exitJail ─────────────────────────────────────────────────────────────

    @Test
    void exitJail_shouldMoveToRelativePositionZero() {
        piece.exitJail();
        assertEquals(0, piece.getRelativePosition());
        assertFalse(piece.isInJail());
    }

    @Test
    void exitJail_shouldThrowWhenNotInJail() {
        piece.exitJail();
        assertThrows(IllegalStateException.class, piece::exitJail);
    }

    // ─── move ─────────────────────────────────────────────────────────────────

    @Test
    void move_shouldAdvanceRelativePosition() {
        piece.exitJail();
        piece.move(5);
        assertEquals(5, piece.getRelativePosition());
    }

    @Test
    void move_shouldThrowWhenInJail() {
        assertThrows(IllegalStateException.class, () -> piece.move(3));
    }

    @Test
    void move_shouldThrowWhenExceedsVictory() {
        piece.exitJail();
        piece.move(60);
        // relativePosition = 60, solo puede avanzar 10 más (hasta 70)
        assertThrows(IllegalStateException.class, () -> piece.move(11));
    }

    @Test
    void move_shouldReachVictoryExactly() {
        piece.exitJail();
        piece.move(70);
        assertTrue(piece.isAtVictory());
        assertEquals(70, piece.getRelativePosition());
    }

    // ─── canMove ──────────────────────────────────────────────────────────────

    @Test
    void canMove_shouldReturnFalseWhenInJail() {
        assertFalse(piece.canMove(5));
    }

    @Test
    void canMove_shouldReturnFalseWhenAtVictory() {
        piece.exitJail();
        piece.move(70);
        assertFalse(piece.canMove(1));
    }

    @Test
    void canMove_shouldReturnTrueWhenStepsDoNotExceedVictory() {
        piece.exitJail();
        assertTrue(piece.canMove(5));
    }

    @Test
    void canMove_shouldReturnFalseWhenStepsExceedVictory() {
        piece.exitJail();
        piece.move(65);
        assertFalse(piece.canMove(6)); // 65 + 6 = 71 > 70
    }

    @Test
    void canMove_shouldReturnTrueWhenStepsReachVictoryExactly() {
        piece.exitJail();
        piece.move(65);
        assertTrue(piece.canMove(5)); // 65 + 5 = 70 exacto
    }

    // ─── sendToJail / sendHome ────────────────────────────────────────────────

    @Test
    void sendToJail_shouldReturnPieceToJail() {
        piece.exitJail();
        piece.move(10);
        piece.sendToJail();
        assertTrue(piece.isInJail());
    }

    @Test
    void sendHome_shouldAlsoSendToJail() {
        piece.exitJail();
        piece.move(10);
        piece.sendHome();
        assertTrue(piece.isInJail());
    }

    // ─── isOnLadder ───────────────────────────────────────────────────────────

    @Test
    void shouldBeOnLadderWhenRelativePositionBetween63And69() {
        piece.exitJail();
        piece.move(63);
        assertTrue(piece.isOnLadder());
    }

    @Test
    void shouldNotBeOnLadderBefore63() {
        piece.exitJail();
        piece.move(62);
        assertFalse(piece.isOnLadder());
    }

    // ─── getAbsolutePosition ─────────────────────────────────────────────────

    @Test
    void absolutePosition_shouldWrapAroundCommonTrack() {
        // AMARILLO sale en 4; avanzar 68 vuelve al inicio del track
        piece.exitJail();
        piece.move(4); // 4 + 4 = 8 en absoluto
        assertEquals(8, piece.getAbsolutePosition());
    }

    @Test
    void absolutePosition_shouldReturnVictoryPositionForAmarillo() {
        piece.exitJail();
        piece.move(70);
        assertEquals(112, piece.getAbsolutePosition());
    }

    @Test
    void absolutePosition_shouldReturnVictoryPositionForAzul() {
        Piece azul = new Piece("p2-piece-0", "AZUL", 21);
        azul.exitJail();
        azul.move(70);
        assertEquals(113, azul.getAbsolutePosition());
    }

    @Test
    void absolutePosition_shouldReturnVictoryPositionForRojo() {
        Piece rojo = new Piece("p3-piece-0", "ROJO", 38);
        rojo.exitJail();
        rojo.move(70);
        assertEquals(114, rojo.getAbsolutePosition());
    }

    @Test
    void absolutePosition_shouldReturnVictoryPositionForVerde() {
        Piece verde = new Piece("p4-piece-0", "VERDE", 55);
        verde.exitJail();
        verde.move(70);
        assertEquals(115, verde.getAbsolutePosition());
    }

    // ─── getAbsolutePositionAfterMove ────────────────────────────────────────

    @Test
    void absolutePositionAfterMove_shouldReturnMinusOneWhenInJail() {
        assertEquals(-1, piece.getAbsolutePositionAfterMove(5));
    }

    @Test
    void absolutePositionAfterMove_shouldReturnMinusOneWhenExceedsVictory() {
        piece.exitJail();
        piece.move(65);
        assertEquals(-1, piece.getAbsolutePositionAfterMove(6));
    }

    @Test
    void absolutePositionAfterMove_shouldReturnCorrectPosition() {
        piece.exitJail();
        int expected = piece.getAbsolutePositionAfterMove(5);
        piece.move(5);
        assertEquals(expected, piece.getAbsolutePosition());
    }

    // ─── getters básicos ─────────────────────────────────────────────────────

    @Test
    void shouldReturnCorrectId() {
        assertEquals("p1-piece-0", piece.getId());
    }

    @Test
    void shouldReturnCorrectExitPosition() {
        assertEquals(4, piece.getExitAbsolutePosition());
    }

    @Test
    void shouldReturnVictoryRelative70() {
        assertEquals(70, piece.getVictoryRelative());
    }
}
