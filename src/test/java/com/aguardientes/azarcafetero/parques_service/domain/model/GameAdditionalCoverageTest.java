package com.aguardientes.azarcafetero.parques_service.domain.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests adicionales para Game.java enfocados en las líneas que aún quedaban
 * sin cubrir según el reporte de JaCoCo:
 *
 *   • Línea 111, 113: passTurn() rama else (jugador con ficha activa que
 *     no puede mover con ninguno de los dados, con dados no-par).
 *
 *   • Líneas 212-214: applyMove() cuando la ficha está en cárcel — exitJail()
 *     y checkCaptures() sobre la salida.
 *
 *   • Línea 222: piece.sendToJail() cuando había oportunidad de matar pero el
 *     jugador eligió no aprovecharla (regla "soplar").
 *
 *   • Líneas 235-237: checkCaptures rama true — captura efectiva de ficha
 *     enemiga en la casilla destino.
 */
class GameAdditionalCoverageTest {

    private void setDice(Game g, int d1, int d2) throws Exception {
        setField(g, "die1", d1);
        setField(g, "die2", d2);
        setField(g, "die1Used", false);
        setField(g, "die2Used", false);
        setField(g, "diceRolled", true);
        setField(g, "moveValue", d1 + d2);
        setField(g, "jailExitAvailable", false);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // ─── passTurn rama else: con ficha activa que no puede mover ─────────────

    @Test
    void passTurn_withActivePieceThatCannotMove_andNonPair_shouldAdvanceTurn() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("g-pass-else", List.of(p1, p2));
        game.start();

        // Una ficha activa de p1 a rel=68. Las otras 3 en cárcel.
        // Con dados (3,5) no-par: 68+3=71>70, 68+5=73>70, suma 8 → 76>70.
        // Para ninguna otra ficha aplica: están en cárcel y no hay jailExit (no par).
        // → canPlayerMoveAnyPiece() = false → passTurn entra a la rama else.
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(68);

        setDice(game, 3, 5); // no par
        // Antes: turno=0 (p1)
        assertEquals(0, game.getCurrentTurn());

        game.passTurn("p1");

        // Rama else con die1 != die2 → diceRolled=false + nextTurn() → turno=1 (p2)
        assertFalse(game.isDiceRolled());
        assertEquals(1, game.getCurrentTurn());
    }

    @Test
    void passTurn_withActivePieceThatCannotMove_andPair_shouldKeepSameTurn() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("g-pass-pair", List.of(p1, p2));
        game.start();

        // Ficha p1 cerca de victoria y dados par pequeños: no puede mover.
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(69); // rel=69
        // Con par (6,6): 69+6=75>70, suma=12 → 81>70.
        // Las otras 3 en cárcel pero jailExitAvailable=false (lo seteamos así).
        setDice(game, 6, 6);

        // jailExitAvailable=false (lo fuerza setDice). canPlayerMoveAnyPiece=false.
        assertEquals(0, game.getCurrentTurn());

        game.passTurn("p1");

        // Rama else con die1 == die2 → diceRolled=false pero NO nextTurn → sigue p1
        assertFalse(game.isDiceRolled());
        assertEquals(0, game.getCurrentTurn());
    }

    // ─── movePiece sobre ficha en cárcel → applyMove rama jail (212-214) ─────

    @Test
    void movePiece_onPieceInJail_shouldExitJailAndCheckCaptures() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("g-move-jail", List.of(p1, p2));
        game.start();

        // Forzar el escenario: p1 con ficha en cárcel; movePiece sobre esa ficha.
        // Game.applyMove ramifica en "if (piece.isInJail()) { piece.exitJail(); ... }".
        // Para que pase canPlayerMoveAnyPiece dentro de movePiece, basta con
        // que la ficha pase la validación. movePiece no llama canPlayerMoveAnyPiece
        // explícitamente — sólo validateDiceSelection y luego applyMove.

        // p1.piece.0 está en cárcel (default). Lanzamos dados (3,4).
        setDice(game, 3, 4);

        Piece jailedPiece = p1.getPieces().get(0);
        assertTrue(jailedPiece.isInJail());

        game.movePiece("p1", jailedPiece.getId(), 1);

        // applyMove ejecutó piece.exitJail() → rel=0
        assertFalse(jailedPiece.isInJail());
        assertEquals(0, jailedPiece.getRelativePosition());
    }

    // ─── checkCaptures: captura efectiva (líneas 235-237) ─────────────────────

    @Test
    void movePiece_shouldCaptureOpponentAndSendItToJail() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("g-capture", List.of(p1, p2));
        game.start();

        // Pongamos p1 con ficha en abs=20 (rel para AMARILLO: abs=(4+rel)%68=20 → rel=16)
        // Pongamos p2 con ficha en abs=22 (no segura: 21 sí lo es; 22 no)
        // Con die1=2 → p1 mueve a abs=22 → captura.
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(16); // AMARILLO abs = (4+16)%68 = 20

        p2.getPieces().get(0).exitJail();
        // AZUL exit=21, abs=22 → rel = (22-21+68)%68 = 1
        p2.getPieces().get(0).move(1);

        // Verificar pre-condiciones
        assertEquals(20, p1.getPieces().get(0).getAbsolutePosition());
        assertEquals(22, p2.getPieces().get(0).getAbsolutePosition());

        setDice(game, 2, 6);
        game.movePiece("p1", p1.getPieces().get(0).getId(), 1);

        // p1 ahora en abs=22
        assertEquals(22, p1.getPieces().get(0).getAbsolutePosition());
        // p2 fue enviada a cárcel
        assertTrue(p2.getPieces().get(0).isInJail());
    }

    // ─── hasKillOpportunity true pero no se ejecutó → ficha a cárcel (222) ──

    @Test
    void movePiece_whenKillWasPossibleButNotTaken_shouldSendOwnPieceToJail() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("g-soplar", List.of(p1, p2));
        game.start();

        // p1 tiene DOS fichas activas:
        //   • A: en posición tal que con die=3 puede capturar a un enemigo
        //   • B: en posición tal que con die=3 NO captura
        // p1 mueve B → "sopla": no aprovechó la captura → B va a cárcel.

        // p1 piece A en rel=10 (AMARILLO abs=(4+10)%68=14)
        // Enemigo en abs=17 (AZUL exit=21, rel=(17-21+68)%68=64 → en escalera, no captura).
        // Hmm, eso no funciona. Probemos:
        // p1.A en abs=10 (rel=6), enemigo en abs=13 → die=3 → A llegaría a abs=13 → capture.
        // p1.B en abs=30 (rel=26), die=3 → abs=33 → es segura (33 está en SAFE_SQUARES).
        //   Esa casilla segura evita la auto-cárcel? Veamos: applyMove no chequea safe
        //   antes de sendToJail. La única forma de evitarlo es que hasKillOpportunity
        //   sea false o que la propia jugada haya capturado.
        //   Pero queremos que SÍ haya kill opportunity y NO se aproveche → sendToJail.

        // Setup más cuidadoso:
        // p1.A en abs=10 (rel=6 AMARILLO), enemigo p2 en abs=13.
        //   die1=3 → A→abs=13 captura posible.
        // p1.B en abs=40 (rel=36 AMARILLO: (4+36)%68=40), die1=3 → abs=43 (no segura, no enemigo).
        p1.getPieces().get(0).exitJail();
        p1.getPieces().get(0).move(6);  // A → abs=10

        p1.getPieces().get(1).exitJail();
        p1.getPieces().get(1).move(36); // B → abs=40

        p2.getPieces().get(0).exitJail();
        // p2.0 en abs=13: AZUL exit=21, abs=13 → rel=(13-21+68)%68=60
        p2.getPieces().get(0).move(60);

        assertEquals(10, p1.getPieces().get(0).getAbsolutePosition());
        assertEquals(40, p1.getPieces().get(1).getAbsolutePosition());
        assertEquals(13, p2.getPieces().get(0).getAbsolutePosition());

        // hasKillOpportunity: ¿A en abs=10 con steps=3 → abs=13 donde está enemigo?
        // → true. ¿B en abs=40 con steps=3 → abs=43 donde NO hay enemigo? → false.
        // hasKillOpportunity itera todas las activas → true.

        // Movemos B (no aprovecha la captura) → B debe ir a cárcel.
        setDice(game, 3, 5);
        game.movePiece("p1", p1.getPieces().get(1).getId(), 1);

        // B fue enviada a la cárcel por no aprovechar el kill
        assertTrue(p1.getPieces().get(1).isInJail(),
                "La ficha B debe estar en cárcel por no aprovechar la captura disponible");
    }

    // ─── Jail attempts: 3 intentos exhaustos → reset + nextTurn ──────────────

    @Test
    void passTurn_allInJail_threeAttemptsExhausted_shouldResetAndAdvanceTurn() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("g-jail-3", List.of(p1, p2));
        game.start();

        // p1 con todas las fichas en cárcel. jailAttempts=2 → próximo passTurn lo
        // lleva a 3 → hasExhaustedJailAttempts() = true → reset + nextTurn.
        Field f = Player.class.getDeclaredField("jailAttempts");
        f.setAccessible(true);
        f.set(p1, 2);

        setDice(game, 3, 5); // no par
        assertEquals(0, game.getCurrentTurn());

        game.passTurn("p1");

        assertEquals(0, p1.getJailAttempts(), "Los intentos deben quedar reseteados");
        assertEquals(1, game.getCurrentTurn(), "El turno debe avanzar a p2");
    }
}
