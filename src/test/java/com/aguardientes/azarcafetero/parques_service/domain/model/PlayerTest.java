package com.aguardientes.azarcafetero.parques_service.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player("p1", "Karol", "AMARILLO", 4);
    }

    // ─── Estado inicial ───────────────────────────────────────────────────────

    @Test
    void shouldHaveFourPiecesInitially() {
        assertEquals(4, player.getPieces().size());
    }

    @Test
    void shouldHaveAllPiecesInJailInitially() {
        assertTrue(player.allPiecesInJail());
    }

    @Test
    void shouldHaveAnyPieceInJailInitially() {
        assertTrue(player.hasAnyPieceInJail());
    }

    @Test
    void shouldNotHaveFinishedInitially() {
        assertFalse(player.hasFinished());
    }

    @Test
    void shouldHaveZeroJailAttemptsInitially() {
        assertEquals(0, player.getJailAttempts());
    }

    @Test
    void shouldHaveZeroConsecutivePairsInitially() {
        assertEquals(0, player.getConsecutivePairs());
    }

    // ─── getters ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnCorrectId() {
        assertEquals("p1", player.getId());
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("Karol", player.getName());
    }

    @Test
    void shouldReturnCorrectColor() {
        assertEquals("AMARILLO", player.getColor());
    }

    @Test
    void shouldReturnCorrectExitPosition() {
        assertEquals(4, player.getExitPosition());
    }

    // ─── getPiecesInJail ─────────────────────────────────────────────────────

    @Test
    void getPiecesInJail_shouldReturnAllFourInitially() {
        assertEquals(4, player.getPiecesInJail().size());
    }

    @Test
    void getPiecesInJail_shouldReturnThreeAfterOneExits() {
        player.getPieces().get(0).exitJail();
        assertEquals(3, player.getPiecesInJail().size());
    }

    // ─── getActivePieces ─────────────────────────────────────────────────────

    @Test
    void getActivePieces_shouldReturnEmptyInitially() {
        assertTrue(player.getActivePieces().isEmpty());
    }

    @Test
    void getActivePieces_shouldReturnOneAfterExitingJail() {
        player.getPieces().get(0).exitJail();
        assertEquals(1, player.getActivePieces().size());
    }

    // ─── getMostAdvancedActivePiece ───────────────────────────────────────────

    @Test
    void getMostAdvancedActivePiece_shouldReturnNullWhenNoneActive() {
        assertNull(player.getMostAdvancedActivePiece());
    }

    @Test
    void getMostAdvancedActivePiece_shouldReturnMostAdvanced() {
        player.getPieces().get(0).exitJail();
        player.getPieces().get(0).move(10);
        player.getPieces().get(1).exitJail();
        player.getPieces().get(1).move(5);

        Piece most = player.getMostAdvancedActivePiece();
        assertNotNull(most);
        assertEquals(10, most.getRelativePosition());
    }

    // ─── findPiece ────────────────────────────────────────────────────────────

    @Test
    void findPiece_shouldReturnCorrectPiece() {
        String pieceId = player.getPieces().get(0).getId();
        Piece found = player.findPiece(pieceId);
        assertNotNull(found);
        assertEquals(pieceId, found.getId());
    }

    @Test
    void findPiece_shouldThrowWhenNotFound() {
        assertThrows(IllegalArgumentException.class, () -> player.findPiece("nonexistent"));
    }

    // ─── hasFinished ─────────────────────────────────────────────────────────

    @Test
    void hasFinished_shouldReturnTrueWhenAllPiecesAtVictory() {
        for (Piece p : player.getPieces()) {
            p.exitJail();
            p.move(70);
        }
        assertTrue(player.hasFinished());
    }

    @Test
    void hasFinished_shouldReturnFalseWhenOnePieceNotAtVictory() {
        player.getPieces().get(0).exitJail();
        player.getPieces().get(0).move(70);
        player.getPieces().get(1).exitJail();
        player.getPieces().get(1).move(70);
        player.getPieces().get(2).exitJail();
        player.getPieces().get(2).move(70);
        // la cuarta sigue en cárcel
        assertFalse(player.hasFinished());
    }

    // ─── jailAttempts ────────────────────────────────────────────────────────

    @Test
    void incrementJailAttempts_shouldIncreaseByOne() {
        player.incrementJailAttempts();
        assertEquals(1, player.getJailAttempts());
    }

    @Test
    void resetJailAttempts_shouldSetToZero() {
        player.incrementJailAttempts();
        player.incrementJailAttempts();
        player.resetJailAttempts();
        assertEquals(0, player.getJailAttempts());
    }

    @Test
    void hasExhaustedJailAttempts_shouldReturnFalseBeforeThree() {
        player.incrementJailAttempts();
        player.incrementJailAttempts();
        assertFalse(player.hasExhaustedJailAttempts());
    }

    @Test
    void hasExhaustedJailAttempts_shouldReturnTrueAtThree() {
        player.incrementJailAttempts();
        player.incrementJailAttempts();
        player.incrementJailAttempts();
        assertTrue(player.hasExhaustedJailAttempts());
    }

    // ─── consecutivePairs ────────────────────────────────────────────────────

    @Test
    void incrementConsecutivePairs_shouldIncreaseByOne() {
        player.incrementConsecutivePairs();
        assertEquals(1, player.getConsecutivePairs());
    }

    @Test
    void resetConsecutivePairs_shouldSetToZero() {
        player.incrementConsecutivePairs();
        player.incrementConsecutivePairs();
        player.resetConsecutivePairs();
        assertEquals(0, player.getConsecutivePairs());
    }

    // ─── getJailAttemptsRemaining ────────────────────────────────────────────

    @Test
    void getJailAttemptsRemaining_shouldReturnThreeWhenAllInJailAndNoAttempts() {
        assertEquals(3, player.getJailAttemptsRemaining());
    }

    @Test
    void getJailAttemptsRemaining_shouldReturnTwoAfterOneAttempt() {
        player.incrementJailAttempts();
        assertEquals(2, player.getJailAttemptsRemaining());
    }

    @Test
    void getJailAttemptsRemaining_shouldReturnZeroWhenPieceIsActive() {
        player.getPieces().get(0).exitJail();
        assertEquals(0, player.getJailAttemptsRemaining());
    }
}
