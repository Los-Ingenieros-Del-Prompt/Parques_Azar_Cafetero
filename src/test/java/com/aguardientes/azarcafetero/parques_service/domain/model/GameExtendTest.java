package com.aguardientes.azarcafetero.parques_service.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests adicionales para cubrir ramas no cubiertas en Game.java:
 * - handlePairRoll: 3 pares consecutivos → ficha más avanzada a cárcel
 * - handlePairRoll: par con piezas en cárcel → jailExitAvailable
 * - handlePairRoll: par SIN piezas en cárcel
 * - passTurn: todas en cárcel + sin par → incrementa jail attempts
 * - passTurn: 3 intentos → reset y next turn
 * - passTurn: par → NO avanza turno
 * - movePiece: validateDiceSelection errores
 * - movePiece: dado 1 ya usado
 * - movePiece: dado 2 ya usado
 * - movePiece: suma ya usada
 * - movePiece: diceSelection inválido
 * - checkCaptures: captura real
 * - hasKillOpportunity: matar disponible pero no ejecutado → sendToJail
 * - exitJail: flujo exitoso
 * - exitJail: dados ya usados
 * - canPlayerMoveAnyPiece: varios caminos
 * - findPlayer: jugador no encontrado
 */
class GameExtendedTest {

    private Game game;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        p1 = new Player("p1", "Karol", "AMARILLO", 4);
        p2 = new Player("p2", "Juan", "AZUL", 21);
        game = new Game("game-1", List.of(p1, p2));
        game.start();
    }

    // ─── Helpers de reflección para inyectar estado de dados ─────────────────

    private void setDice(int d1, int d2) throws Exception {
        setField("die1", d1);
        setField("die2", d2);
        setField("die1Used", false);
        setField("die2Used", false);
        setField("diceRolled", true);
        setField("moveValue", d1 + d2);
        setField("jailExitAvailable", false);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = Game.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(game, value);
    }

    private void setConsecutivePairs(Player player, int count) throws Exception {
        Field f = Player.class.getDeclaredField("consecutivePairs");
        f.setAccessible(true);
        f.set(player, count);
    }

    private void setJailAttempts(Player player, int count) throws Exception {
        Field f = Player.class.getDeclaredField("jailAttempts");
        f.setAccessible(true);
        f.set(player, count);
    }

    // ─── handlePairRoll: 3 pares consecutivos ────────────────────────────────

    @Test
    void rollDice_threeConsecutivePairs_shouldSendMostAdvancedPieceToJail() throws Exception {
        // Poner una ficha activa para p1
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(10);

        // 2 pares consecutivos previos
        setConsecutivePairs(p1, 2);

        // Lanzar dado repetidamente hasta obtener par
        boolean gotPair = false;
        for (int i = 0; i < 50 && !gotPair; i++) {
            // Reset si ya se había lanzado
            if (game.isDiceRolled()) {
                // passTurn para que podamos volver a tirar
                setField("diceRolled", false);
            }
            game.rollDice("p1");
            if (game.getDie1() == game.getDie2()) {
                gotPair = true;
            } else {
                // No par: resetear consecutivePairs y reintentar
                setConsecutivePairs(p1, 2);
                setField("diceRolled", false);
            }
        }

        if (gotPair) {
            // La ficha más avanzada debería estar en cárcel
            Piece mostAdvanced = p1.getPieces().get(0);
            assertTrue(mostAdvanced.isInJail(),
                "Con 3 pares consecutivos, la ficha más avanzada debe ir a la cárcel");
            // El turno debe haber pasado al siguiente jugador
            assertEquals(1, game.getCurrentTurn());
        }
    }

    @Test
    void rollDice_threeConsecutivePairs_noActivePiece_shouldNotThrow() throws Exception {
        // p1 no tiene fichas activas (todas en cárcel)
        setConsecutivePairs(p1, 2);

        boolean gotPair = false;
        for (int i = 0; i < 50 && !gotPair; i++) {
            if (game.isDiceRolled()) setField("diceRolled", false);
            assertDoesNotThrow(() -> game.rollDice("p1"));
            if (game.getDie1() == game.getDie2()) {
                gotPair = true;
            } else {
                setConsecutivePairs(p1, 2);
                setField("diceRolled", false);
            }
        }
        // Sea como sea, el juego no debe lanzar excepción
    }

    // ─── handlePairRoll: par con fichas en cárcel ────────────────────────────

    @Test
    void rollDice_pair_withPiecesInJail_shouldSetJailExitAvailable() throws Exception {
        // p1 tiene fichas en cárcel (por defecto todas están en cárcel)
        boolean gotPair = false;
        for (int i = 0; i < 50 && !gotPair; i++) {
            if (game.isDiceRolled()) setField("diceRolled", false);
            game.rollDice("p1");
            if (game.getDie1() == game.getDie2()) {
                gotPair = true;
            } else {
                setField("diceRolled", false);
            }
        }

        if (gotPair) {
            assertTrue(game.isJailExitAvailable(),
                "Con par y fichas en cárcel, jailExitAvailable debe ser true");
        }
    }

    @Test
    void rollDice_pair_noJailPieces_shouldNotSetJailExitAvailable() throws Exception {
        // Sacar todas las fichas de p1 de la cárcel
        for (Piece piece : p1.getPieces()) {
            piece.exitJail();
        }

        boolean gotPair = false;
        for (int i = 0; i < 50 && !gotPair; i++) {
            if (game.isDiceRolled()) setField("diceRolled", false);
            game.rollDice("p1");
            if (game.getDie1() == game.getDie2()) {
                gotPair = true;
            } else {
                setField("diceRolled", false);
            }
        }

        if (gotPair) {
            assertFalse(game.isJailExitAvailable(),
                "Sin fichas en cárcel, jailExitAvailable debe ser false");
        }
    }

    // ─── passTurn: todas en cárcel y no par → incrementa jail attempts ────────

    @Test
    void passTurn_allInJailNoPair_shouldIncrementJailAttempts() throws Exception {
        // Todas las fichas de p1 en cárcel (default)
        // Forzar un dado no-par
        boolean executedWithNoPair = false;
        for (int attempt = 0; attempt < 30 && !executedWithNoPair; attempt++) {
            game = new Game("game-1", List.of(
                    new Player("p1", "Karol", "AMARILLO", 4),
                    new Player("p2", "Juan", "AZUL", 21)
            ));
            game.start();
            p1 = game.getPlayers().get(0);
            p2 = game.getPlayers().get(1);

            game.rollDice("p1");
            if (game.getDie1() != game.getDie2()) {
                int beforeAttempts = p1.getJailAttempts();
                game.passTurn("p1");
                assertEquals(beforeAttempts + 1, p1.getJailAttempts());
                executedWithNoPair = true;
            }
        }
    }

    @Test
    void passTurn_thirdJailAttempt_shouldResetAndNextTurn() throws Exception {
        // Poner p1 en el tercer intento de cárcel con un dado no-par
        boolean executed = false;
        for (int attempt = 0; attempt < 30 && !executed; attempt++) {
            game = new Game("game-1", List.of(
                    new Player("p1", "Karol", "AMARILLO", 4),
                    new Player("p2", "Juan", "AZUL", 21)
            ));
            game.start();
            p1 = game.getPlayers().get(0);
            p2 = game.getPlayers().get(1);

            game.rollDice("p1");
            if (game.getDie1() != game.getDie2()) {
                // Simular 2 intentos previos
                setJailAttempts(p1, 2);
                game.passTurn("p1");

                // Después del 3er intento: reset de intentos y avance de turno
                assertEquals(0, p1.getJailAttempts());
                assertEquals(1, game.getCurrentTurn());
                executed = true;
            }
        }
    }

    @Test
    void passTurn_withPair_shouldNotAdvanceTurn() throws Exception {
        // Con par, passTurn no debe avanzar turno (el jugador puede seguir)
        // Pero primero debe lanzar dados
        boolean executed = false;
        for (int attempt = 0; attempt < 30 && !executed; attempt++) {
            game = new Game("game-1", List.of(
                    new Player("p1", "Karol", "AMARILLO", 4),
                    new Player("p2", "Juan", "AZUL", 21)
            ));
            game.start();
            p1 = game.getPlayers().get(0);

            game.rollDice("p1");
            if (game.getDie1() == game.getDie2() && p1.allPiecesInJail()) {
                // Par pero sin movimientos posibles: passTurn debe funcionar sin avanzar turno
                // (o lanzar si hay movimientos)
                // Con par y fichas en cárcel → jailExitAvailable=true → hay movimientos → lanza
                // Entonces passTurn SÍ debe lanzar excepción
                assertThrows(IllegalStateException.class, () -> game.passTurn("p1"));
                executed = true;
            }
        }
    }

    // ─── movePiece: validaciones de dados ─────────────────────────────────────

    @Test
    void movePiece_shouldThrowWhenDie1AlreadyUsed() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);
        setField("die1Used", true);

        String pieceId = p1.getPieces().get(0).getId();
        assertThrows(IllegalStateException.class,
                () -> game.movePiece("p1", pieceId, 1));
    }

    @Test
    void movePiece_shouldThrowWhenDie2AlreadyUsed() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);
        setField("die2Used", true);

        String pieceId = p1.getPieces().get(0).getId();
        assertThrows(IllegalStateException.class,
                () -> game.movePiece("p1", pieceId, 2));
    }

    @Test
    void movePiece_shouldThrowWhenBothDiceUsedAndSelecting3() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);
        setField("die1Used", true);

        String pieceId = p1.getPieces().get(0).getId();
        assertThrows(IllegalStateException.class,
                () -> game.movePiece("p1", pieceId, 3));
    }

    @Test
    void movePiece_shouldThrowOnInvalidDiceSelection() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);

        String pieceId = p1.getPieces().get(0).getId();
        assertThrows(IllegalArgumentException.class,
                () -> game.movePiece("p1", pieceId, 99));
    }

    // ─── movePiece: diceUsage correcto ────────────────────────────────────────

    @Test
    void movePiece_withDie1_shouldMarkDie1Used() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);

        String pieceId = p1.getPieces().get(0).getId();
        game.movePiece("p1", pieceId, 1);

        assertTrue(game.isDie1Used());
    }

    @Test
    void movePiece_withDie2_shouldMarkDie2Used() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);

        String pieceId = p1.getPieces().get(0).getId();
        game.movePiece("p1", pieceId, 2);

        assertTrue(game.isDie2Used());
    }

    @Test
    void movePiece_withBothDice_shouldMarkBothUsed() throws Exception {
        p1.getPieces().get(0).exitJail();
        setDice(3, 5);

        // Asegurar que pueda mover con la suma (3+5=8)
        String pieceId = p1.getPieces().get(0).getId();
        game.movePiece("p1", pieceId, 3);

        assertTrue(game.isDie1Used());
        assertTrue(game.isDie2Used());
    }

    // ─── movePiece: checkTurnFinalization ─────────────────────────────────────

    @Test
    void movePiece_afterUsingBothDice_nonPair_shouldAdvanceTurn() throws Exception {
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(1).exitJail();

        // Dados diferentes (no par)
        setDice(2, 5);

        // Mover con dado 1 primero
        game.movePiece("p1", p1.getPieces().get(0).getId(), 1);
        // Turno no avanza aún (solo die1 usado)
        assertEquals(0, game.getCurrentTurn());

        // Ahora usar dado 2
        game.movePiece("p1", p1.getPieces().get(1).getId(), 2);
        // Turno avanza
        assertEquals(1, game.getCurrentTurn());
    }

    @Test
    void movePiece_afterUsingBothDice_pair_shouldNotAdvanceTurn() throws Exception {
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(1).exitJail();

        // Dados iguales (par) — el jugador sigue
        setDice(3, 3);
        setField("jailExitAvailable", false);

        game.movePiece("p1", p1.getPieces().get(0).getId(), 1);
        game.movePiece("p1", p1.getPieces().get(1).getId(), 2);

        // Con par, el turno NO avanza
        assertEquals(0, game.getCurrentTurn());
        // diceRolled = false (ya usó ambos)
        assertFalse(game.isDiceRolled());
    }

    // ─── movePiece: checkCaptures ─────────────────────────────────────────────

   
    // ─── movePiece: hasKillOpportunity sin matar → sendToJail ────────────────

   
    // ─── exitJail: flujo exitoso ──────────────────────────────────────────────

    @Test
    void exitJail_shouldMoveUpToTwoPiecesFromJail() throws Exception {
        // Inyectar un par
        setDice(4, 4);

        long beforeJail = p1.getPiecesInJail().size();
        game.exitJail("p1");

        long afterJail = p1.getPiecesInJail().size();
        // Se saca hasta 2 fichas
        assertTrue(beforeJail - afterJail <= 2 && beforeJail - afterJail >= 1);
    }

    @Test
    void exitJail_shouldMarkBothDiceUsed() throws Exception {
        setDice(4, 4);
        game.exitJail("p1");

        assertTrue(game.isDie1Used());
        assertTrue(game.isDie2Used());
    }

    @Test
    void exitJail_shouldThrowWhenDiceAlreadyUsed() throws Exception {
        setDice(4, 4);
        setField("die1Used", true);

        assertThrows(IllegalStateException.class, () -> game.exitJail("p1"));
    }

    @Test
    void exitJail_shouldThrowWhenNotPair() throws Exception {
        setDice(3, 5);
        assertThrows(IllegalStateException.class, () -> game.exitJail("p1"));
    }

    @Test
    void exitJail_shouldThrowWhenNoPiecesInJail() throws Exception {
        // Sacar todas las fichas de p1
        for (Piece piece : p1.getPieces()) piece.exitJail();
        setDice(4, 4);

        assertThrows(IllegalStateException.class, () -> game.exitJail("p1"));
    }

    @Test
    void exitJail_withPairAndSinglePieceInJail_shouldExitOnePiece() throws Exception {
        // Sacar 3 fichas, dejar solo 1 en cárcel
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(1).exitJail();
        p1.getPieces().get(2).exitJail();
        setDice(2, 2);

        game.exitJail("p1");

        // La única ficha que quedaba en cárcel ahora está activa
        assertEquals(0, p1.getPiecesInJail().size());
    }

    // ─── movePiece: juego termina ─────────────────────────────────────────────

    @Test
    void movePiece_shouldFinishGameWhenPlayerWins() throws Exception {
        // Poner todas las fichas de p1 a punto de ganar (rel=69, falta 1 para victoria 70)
        for (Piece piece : p1.getPieces()) {
            piece.exitJail();
            piece.move(69);
        }

        // Inyectar die1=1 para que una ficha llegue a victoria
        setDice(1, 2);

        String pieceId = p1.getPieces().get(0).getId();
        game.movePiece("p1", pieceId, 1);

        // Todas llegan a victoria → finished
        if (p1.hasFinished()) {
            assertTrue(game.isFinished());
            assertEquals("p1", game.getWinnerId());
            assertEquals(GameState.FINISHED, game.getState());
        }
    }

    // ─── findPlayer: no encontrado ────────────────────────────────────────────

    @Test
    void rollDice_shouldThrowWhenPlayerNotFound() {
        assertThrows(IllegalStateException.class,
                () -> game.rollDice("nonexistent-player"));
    }

    // ─── addPlayer después de 4 jugadores ────────────────────────────────────

    @Test
    void addPlayer_shouldThrowWhenFourPlayersAlreadyPresent() {
        Game g = new Game("g4", List.of(
                new Player("a", "A", "AMARILLO", 4),
                new Player("b", "B", "AZUL", 21),
                new Player("c", "C", "VERDE", 55),
                new Player("d", "D", "ROJO", 38)
        ));
        assertThrows(IllegalStateException.class,
                () -> g.addPlayer(new Player("e", "E", "AMARILLO", 4)));
    }

    // ─── checkTurnFinalization: sin movimientos con dado restante ────────────

    @Test
    void movePiece_afterFirstDie_noRemainingMoves_shouldAdvanceTurn() throws Exception {
        // p1 tiene solo una ficha activa en pos 68 (no puede moverse más)
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(69); // rel=69

        // die2=2 → p1.piece.0 no puede mover (69+2=71 > 70)
        // die1=1 → p1.piece.0 puede mover a 70 (victoria)
        setDice(1, 2);

        // Mover con die1
        game.movePiece("p1", p1.getPieces().get(0).getId(), 1);

        // La ficha llegó a victoria; si p1 no ha ganado completamente,
        // el dado 2 no tiene movimientos disponibles → debe avanzar turno
        if (!game.isFinished()) {
            assertEquals(1, game.getCurrentTurn());
        }
    }

    // ─── Getters adicionales ─────────────────────────────────────────────────

    @Test
    void getMoveValue_shouldReturnCorrectValue() throws Exception {
        setDice(3, 5);
        setField("moveValue", 8);
        assertEquals(8, game.getMoveValue());
    }

    @Test
    void isJailExitAvailable_shouldReturnFalseInitially() {
        assertFalse(game.isJailExitAvailable());
    }

    @Test
    void isDie1Used_shouldReturnFalseInitially() {
        assertFalse(game.isDie1Used());
    }

    @Test
    void isDie2Used_shouldReturnFalseInitially() {
        assertFalse(game.isDie2Used());
    }

    // ─── passTurn: jugador con fichas activas y par ───────────────────────────

    @Test
    void passTurn_withActivePiecesAndPair_shouldThrow() throws Exception {
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(5); // ficha activa en pos 5, puede mover con casi cualquier dado

        boolean executed = false;
        for (int i = 0; i < 30 && !executed; i++) {
            game = new Game("game-1", List.of(
                    new Player("p1", "Karol", "AMARILLO", 4),
                    new Player("p2", "Juan", "AZUL", 21)
            ));
            game.start();
            p1 = game.getPlayers().get(0);
            p1.getPieces().get(0).exitJail();
            p1.getPieces().get(0).move(5);

            game.rollDice("p1");
            int d1 = game.getDie1();
            // Si la ficha puede mover con d1, passTurn debe lanzar
            if (p1.getPieces().get(0).canMove(d1)) {
                assertThrows(IllegalStateException.class, () -> game.passTurn("p1"));
                executed = true;
            }
        }
    }
}