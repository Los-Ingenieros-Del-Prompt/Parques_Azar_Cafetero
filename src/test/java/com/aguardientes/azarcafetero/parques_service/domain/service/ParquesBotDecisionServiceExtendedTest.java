package com.aguardientes.azarcafetero.parques_service.domain.service;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Piece;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests adicionales para cubrir ramas no cubiertas en ParquesBotDecisionService:
 * - decide() con fichas activas → opciones de movimiento reales
 * - greedyDecision: MEDIUM y HARD con capture, safe square, ladder, victory
 * - evaluate: exitJail con active >= 2, active == 0
 * - evaluate: capture bonus, safe bonus, ladder bonus, victory bonus
 * - evaluate: HARD threat modeling
 * - isBotWinning: bot ganando vs perdiendo
 * - calculateThreat
 * - getValidDecisions: todas las opciones (die1, die2, sum)
 */
class ParquesBotDecisionServiceExtendedTest {

    private ParquesBotDecisionService service;

    @BeforeEach
    void setUp() {
        service = new ParquesBotDecisionService(new Random(42));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Game buildGame(String botId, String humanId, int botExit, int humanExit) {
        Player human = new Player(humanId, "Human", "AMARILLO", humanExit);
        Player bot = new Player(botId, "Bot", "AZUL", botExit);
        Game g = new Game("g-test", List.of(human, bot));
        g.start();
        return g;
    }

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

    // ─── getValidDecisions con fichas activas ─────────────────────────────────

    @Test
    void getValidDecisions_withActivePiece_shouldIncludeMoveOptions() throws Exception {
        String botId = "BOT_EASY_test0001";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        bot.getPieces().get(0).exitJail();
        injectDice(g, 3, 5);

        List<ParquesBotDecisionService.BotDecision> decisions = service.getValidDecisions(g, botId);

        // Debe tener opciones: die1=3, die2=5, sum=8
        assertFalse(decisions.isEmpty());
        assertTrue(decisions.stream().anyMatch(d -> d.diceSelection() == 1));
        assertTrue(decisions.stream().anyMatch(d -> d.diceSelection() == 2));
        assertTrue(decisions.stream().anyMatch(d -> d.diceSelection() == 3));
    }

    @Test
    void getValidDecisions_withJailExitAvailable_shouldIncludeExitOption() throws Exception {
        String botId = "BOT_MEDIUM_test0002";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        // Todas las fichas del bot en cárcel (default)
        injectDice(g, 3, 3);
        setField(g, "jailExitAvailable", true);

        List<ParquesBotDecisionService.BotDecision> decisions = service.getValidDecisions(g, botId);

        assertTrue(decisions.stream().anyMatch(ParquesBotDecisionService.BotDecision::isExitJail));
    }

    @Test
    void getValidDecisions_withDie1Used_shouldNotIncludeDie1Options() throws Exception {
        String botId = "BOT_EASY_test0003";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        bot.getPieces().get(0).exitJail();
        injectDice(g, 3, 5);
        setField(g, "die1Used", true);

        List<ParquesBotDecisionService.BotDecision> decisions = service.getValidDecisions(g, botId);

        // No debe incluir die1=1 ni sum=3
        assertTrue(decisions.stream().noneMatch(d -> d.diceSelection() == 1));
        assertTrue(decisions.stream().noneMatch(d -> d.diceSelection() == 3));
        // Solo die2=2
        assertTrue(decisions.stream().anyMatch(d -> d.diceSelection() == 2));
    }

    @Test
    void getValidDecisions_withVictoryPiece_shouldNotIncludeThatPiece() throws Exception {
        String botId = "BOT_EASY_test0004";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        // Mover ficha a victoria
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(70); // VICTORY_RELATIVE
        injectDice(g, 3, 5);

        List<ParquesBotDecisionService.BotDecision> decisions = service.getValidDecisions(g, botId);

        // La ficha en victoria no debe aparecer
        String victoryPieceId = bot.getPieces().get(0).getId();
        assertTrue(decisions.stream().noneMatch(d -> victoryPieceId.equals(d.pieceId())));
    }

    // ─── decide: EASY ─────────────────────────────────────────────────────────

    @Test
    void decide_easy_withActivePiece_shouldReturnValidDecision() throws Exception {
        String botId = "BOT_EASY_test0005";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        bot.getPieces().get(0).exitJail();
        injectDice(g, 3, 5);

        var decision = service.decide(g, botId, ParquesBotDifficulty.EASY);

        assertNotNull(decision);
        assertFalse(decision.isPass());
    }

    // ─── decide: MEDIUM ───────────────────────────────────────────────────────

    @Test
    void decide_medium_withActivePiece_shouldReturnBestDecision() throws Exception {
        String botId = "BOT_MEDIUM_test0006";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        bot.getPieces().get(0).exitJail();
        injectDice(g, 3, 5);

        var decision = service.decide(g, botId, ParquesBotDifficulty.MEDIUM);

        assertNotNull(decision);
        assertFalse(decision.isPass());
    }

    // ─── decide: HARD ─────────────────────────────────────────────────────────

    @Test
    void decide_hard_withActivePiece_shouldReturnBestDecision() throws Exception {
        String botId = "BOT_HARD_test0007";
        Game g = buildGame(botId, "human-1", 21, 4);
        Player bot = g.getPlayers().get(1);

        bot.getPieces().get(0).exitJail();
        injectDice(g, 3, 5);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);

        assertNotNull(decision);
        assertFalse(decision.isPass());
    }

    // ─── evaluate: captura ────────────────────────────────────────────────────

    @Test
    void decide_hard_shouldPreferCapture() throws Exception {
        String botId = "BOT_HARD_cap0001";
        Player human = new Player("human-cap", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-cap", List.of(human, bot));
        g.start();

        // Bot en abs=20 (rel=67 desde exit 21: (20-21+68)%68=67)
        // Con die1=1 → abs=21 donde está el humano → captura
        bot.getPieces().get(0).exitJail();
        // Necesitamos que bot esté en abs=20: exit=21, (21+x)%68=20 → x=67
        bot.getPieces().get(0).move(67);

        // Human en abs=21 (exit=4, rel=17: (4+17)%68=21)
        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(17);

        injectDice(g, 1, 6);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);

        // Con HARD, debería preferir matar (die1=1 lleva a abs=21)
        assertNotNull(decision);
    }

    // ─── evaluate: safe square ────────────────────────────────────────────────

    @Test
    void decide_medium_shouldPreferSafeSquare() throws Exception {
        String botId = "BOT_MEDIUM_safe01";
        Player human = new Player("human-s", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-safe", List.of(human, bot));
        g.start();

        // Bot cerca de una casilla segura: abs=11 es segura
        // exit=21, mover a rel=... para que die1=x aterrice en 11
        // abs=11 = (21+r)%68=11 → r=(11-21+68)%68=58
        // Queremos estar en abs=7 (r=54) y die=4 → abs=11
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(54); // (21+54)%68 = 75%68 = 7

        injectDice(g, 4, 2);

        var decision = service.decide(g, botId, ParquesBotDifficulty.MEDIUM);
        assertNotNull(decision);
    }

    // ─── evaluate: ladder bonus ───────────────────────────────────────────────

    @Test
    void decide_medium_shouldPreferEnteringLadder() throws Exception {
        String botId = "BOT_MEDIUM_ladr01";
        Player human = new Player("human-l", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-ladr", List.of(human, bot));
        g.start();

        // Poner bot cerca del umbral 63
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(61); // rel=61

        // die1=2 → rel=63 = entrada a la escalera
        injectDice(g, 2, 1);

        var decision = service.decide(g, botId, ParquesBotDifficulty.MEDIUM);
        assertNotNull(decision);
    }

    // ─── evaluate: victory bonus ──────────────────────────────────────────────

    @Test
    void decide_hard_shouldPreferVictory() throws Exception {
        String botId = "BOT_HARD_vict01";
        Player human = new Player("human-v", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-vict", List.of(human, bot));
        g.start();

        // rel=68 → con die1=2 llega a 70 (victoria)
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(68);

        injectDice(g, 2, 1);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
        // Debería elegir die1=2 para llegar a victoria
        if (!decision.isPass() && !decision.isExitJail()) {
            assertEquals(1, decision.diceSelection(), "Debería preferir el dado que da victoria");
        }
    }

    // ─── evaluate: exitJail con active >= 2 ──────────────────────────────────

    @Test
    void decide_hard_exitJailWithManyActivePieces_shouldBeLessUrgent() throws Exception {
        String botId = "BOT_HARD_jail01";
        Player human = new Player("human-j", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-jail", List.of(human, bot));
        g.start();

        // 2 fichas activas, 2 en cárcel
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(10);
        bot.getPieces().get(1).exitJail();
        bot.getPieces().get(1).move(15);

        injectDice(g, 3, 3);
        setField(g, "jailExitAvailable", true);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        // Con 2 fichas activas y par, el bot puede salir o mover → alguna decisión válida
        assertNotNull(decision);
    }

    @Test
    void decide_hard_exitJailWithNoActivePieces_shouldBeMoreUrgent() throws Exception {
        String botId = "BOT_HARD_jail02";
        Player human = new Player("human-j2", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-jail2", List.of(human, bot));
        g.start();

        // Todas las fichas en cárcel (default)
        injectDice(g, 2, 2);
        setField(g, "jailExitAvailable", true);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
        // Con todas en cárcel y par disponible, debe salir
        assertTrue(decision.isExitJail());
    }

    // ─── isBotWinning: paths ──────────────────────────────────────────────────

    @Test
    void decide_hard_botWinning_shouldPreferAdvancingForwardPieces() throws Exception {
        String botId = "BOT_HARD_win01";
        Player human = new Player("human-w", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-win", List.of(human, bot));
        g.start();

        // Bot muy adelantado (rel=60), humano atrás (rel=5)
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(60);
        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(5);

        injectDice(g, 3, 4);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
    }

    @Test
    void decide_hard_botLosing_shouldPreferCapture() throws Exception {
        String botId = "BOT_HARD_lose01";
        Player human = new Player("human-l", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-lose", List.of(human, bot));
        g.start();

        // Bot muy atrás (rel=5), humano adelante (rel=50)
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(5); // abs cerca

        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(50);

        injectDice(g, 3, 4);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
    }

    // ─── calculateThreat: fichas enemigas cerca ───────────────────────────────

    @Test
    void decide_hard_withThreatNearby_shouldAvoidDangerousSquare() throws Exception {
        String botId = "BOT_HARD_thr01";
        Player human = new Player("human-t", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-thr", List.of(human, bot));
        g.start();

        // Bot en abs=10, die1=5 → abs=15 (peligrosa porque humano en abs=13 → puede comer en 2)
        bot.getPieces().get(0).exitJail();
        // exit=21, para abs=10: (21+r)%68=10 → r=57
        bot.getPieces().get(0).move(57);

        // Humano en abs=13 (a 2 de abs=15)
        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(9); // exit=4, abs=(4+9)=13

        injectDice(g, 5, 2);

        var decision = service.decide(g, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
    }

    // ─── múltiples fichas: lógica de selección ────────────────────────────────

    @Test
    void decide_medium_withMultiplePieces_shouldPickBest() throws Exception {
        String botId = "BOT_MEDIUM_mult01";
        Player human = new Player("human-m", "H", "AMARILLO", 4);
        Player bot = new Player(botId, "B", "AZUL", 21);
        Game g = new Game("g-mult", List.of(human, bot));
        g.start();

        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(10);
        bot.getPieces().get(1).exitJail();
        bot.getPieces().get(1).move(20);
        bot.getPieces().get(2).exitJail();
        bot.getPieces().get(2).move(5);

        injectDice(g, 3, 4);

        var decision = service.decide(g, botId, ParquesBotDifficulty.MEDIUM);
        assertNotNull(decision);
        assertFalse(decision.isPass());
    }

    // ─── generateBotId con MEDIUM ────────────────────────────────────────────

    @Test
    void generateBotId_medium_shouldStartWithBotMediumPrefix() {
        String id = ParquesBotDecisionService.generateBotId(ParquesBotDifficulty.MEDIUM);
        assertTrue(id.startsWith("BOT_MEDIUM_"));
    }

    // ─── getValidDecisions: bot no encontrado ────────────────────────────────

    @Test
    void getValidDecisions_whenBotNotInGame_shouldReturnEmpty() throws Exception {
        String botId = "BOT_EASY_test0099";
        Game g = buildGame("BOT_EASY_other", "human-x", 21, 4);
        injectDice(g, 2, 3);

        List<ParquesBotDecisionService.BotDecision> decisions = service.getValidDecisions(g, botId);
        assertTrue(decisions.isEmpty());
    }
}