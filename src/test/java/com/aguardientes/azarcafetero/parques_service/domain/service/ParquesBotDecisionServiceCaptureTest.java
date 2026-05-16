package com.aguardientes.azarcafetero.parques_service.domain.service;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests adicionales para ParquesBotDecisionService cubriendo ramas que los
 * tests existentes no alcanzaban:
 *
 *   • CAPTURE_BONUS efectivo (línea 162): el bot tiene una ficha en el anillo
 *     común con un oponente justo en la casilla destino.
 *     Los tests previos posicionaban al bot en rel=67 (escalera!), por lo que
 *     getAbsolutePositionAfterMove() nunca apuntaba a la casilla del oponente.
 *
 *   • hasOpponentAt → return true (línea 252).
 *
 *   • getSteps con valor inválido (línea 286 – default): no es alcanzable por
 *     el flujo público, pero al llamar getSteps por reflexión podemos cubrirlo.
 */
class ParquesBotDecisionServiceCaptureTest {

    private final ParquesBotDecisionService service =
            new ParquesBotDecisionService(new Random(7));

    private void injectDice(Game g, int d1, int d2) throws Exception {
        setField(g, "die1", d1);
        setField(g, "die2", d2);
        setField(g, "die1Used", false);
        setField(g, "die2Used", false);
        setField(g, "diceRolled", true);
        setField(g, "jailExitAvailable", false);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    /**
     * Setup: bot AZUL (exit=21) en rel=10 → abs=(21+10)%68=31 (anillo común).
     * Humano AMARILLO (exit=4) en rel=29 → abs=(4+29)=33 (anillo común).
     * Dados (1,2). Con dado 2 → bot avanza a abs=33 → CAPTURA disponible.
     *
     * MEDIUM/HARD deben preferir esa opción (puntaje CAPTURE_BONUS=20).
     */
    @Test
    void mediumBot_shouldChooseCaptureOptionWhenAvailable() throws Exception {
        String botId = "BOT_MEDIUM_cap01";
        Player human = new Player("h1", "Hum", "AMARILLO", 4);
        Player bot   = new Player(botId, "Bot", "AZUL", 21);
        Game g = new Game("g-cap-medium", List.of(human, bot));
        g.start();

        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(10); // abs = 31

        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(29); // abs = 33

        // Verificación de pre-condiciones (sin esto el test sería frágil)
        assertEquals(31, bot.getPieces().get(0).getAbsolutePosition());
        assertEquals(33, human.getPieces().get(0).getAbsolutePosition());

        injectDice(g, 1, 2);
        var decision = service.decide(g, botId, ParquesBotDifficulty.MEDIUM);

        assertNotNull(decision);
        assertFalse(decision.isPass());
        assertFalse(decision.isExitJail());
        // Con die2=2 captura (puntaje >> avance solo), MEDIUM debe escogerlo.
        assertEquals(2, decision.diceSelection(),
                "MEDIUM debe priorizar la opción que captura al humano");
    }

    @Test
    void hardBot_shouldChooseCaptureWithExtraAggressionWhenLosing() throws Exception {
        // HARD + bot perdiendo → captura +8 extra (línea 163). Configuramos
        // al bot con menos progreso para que isBotWinning sea false.
        String botId = "BOT_HARD_cap02";
        Player human = new Player("h1", "Hum", "AMARILLO", 4);
        Player bot   = new Player(botId, "Bot", "AZUL", 21);
        Game g = new Game("g-cap-hard", List.of(human, bot));
        g.start();

        // Bot 1 ficha en abs=31 (rel=10). Humano con MUCHO progreso para que
        // isBotWinning sea false.
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(10);

        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(29); // abs=33 captura target
        human.getPieces().get(1).exitJail();
        human.getPieces().get(1).move(60); // mucho progreso

        injectDice(g, 1, 2);
        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);

        assertNotNull(decision);
        // El bot HARD perdiendo debe priorizar captura
        assertEquals(2, decision.diceSelection(),
                "HARD perdiendo debe escoger captura con bonus de agresividad");
    }

    @Test
    void hardBot_winning_capturePreferred_doesNotApplyLosingBonus() throws Exception {
        // HARD + bot ganando → captura sin bonus extra. Igual debe elegirla.
        String botId = "BOT_HARD_cap03";
        Player human = new Player("h1", "Hum", "AMARILLO", 4);
        Player bot   = new Player(botId, "Bot", "AZUL", 21);
        Game g = new Game("g-cap-hard-w", List.of(human, bot));
        g.start();

        // Bot con tres fichas activas en posiciones avanzadas → va ganando.
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(10); // abs=31, será la que captura
        bot.getPieces().get(1).exitJail();
        bot.getPieces().get(1).move(40);
        bot.getPieces().get(2).exitJail();
        bot.getPieces().get(2).move(50);

        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(29); // abs=33 target

        injectDice(g, 1, 2);
        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
        // La opción de capturar sigue siendo atractiva incluso ganando.
        // No exigimos una ficha específica porque el bot tiene varias, pero
        // SÍ esperamos que la decisión sea válida y no PASS/EXIT.
        assertFalse(decision.isPass());
        assertFalse(decision.isExitJail());
    }

    // ─── getSteps default (línea 286) ─────────────────────────────────────────

    @Test
    void getSteps_withInvalidSelection_shouldReturnZero() throws Exception {
        Game g = new Game("g-steps",
                List.of(new Player("a", "A", "AMARILLO", 4),
                        new Player("b", "B", "AZUL", 21)));
        g.start();
        injectDice(g, 3, 5);

        java.lang.reflect.Method m = ParquesBotDecisionService.class
                .getDeclaredMethod("getSteps", int.class, Game.class);
        m.setAccessible(true);

        // Selección 1, 2 y 3 son válidos
        assertEquals(3, m.invoke(service, 1, g));
        assertEquals(5, m.invoke(service, 2, g));
        assertEquals(8, m.invoke(service, 3, g));
        // 99 cae al default → 0
        assertEquals(0, m.invoke(service, 99, g));
    }
}
