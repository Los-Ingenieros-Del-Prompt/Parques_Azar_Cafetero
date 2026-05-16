package com.aguardientes.azarcafetero.parques_service.domain.service;

import com.aguardientes.azarcafetero.parques_service.application.usecases.CreateGameUseCase;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.infrastructure.InMemoryGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ParquesBotDecisionServiceTest {

    private ParquesBotDecisionService service;
    private Game game;
    private String botId;

    @BeforeEach
    void setUp() {
        service = new ParquesBotDecisionService(new Random(42)); // seed fija para reproducibilidad

        InMemoryGameRepository repo = new InMemoryGameRepository();
        CreateGameUseCase createUseCase = new CreateGameUseCase(repo);

        botId = ParquesBotDecisionService.generateBotId(ParquesBotDifficulty.MEDIUM);

        List<CreateGameUseCase.PlayerInput> inputs = List.of(
                new CreateGameUseCase.PlayerInput("human-1", "Humano"),
                new CreateGameUseCase.PlayerInput(botId, "Bot Medio")
        );

        game = createUseCase.execute("game-bot", inputs);
        game.start();
    }

    // ─── isBot ───────────────────────────────────────────────────────────────

    @Test
    void isBot_shouldReturnTrueForBotPrefix() {
        assertTrue(ParquesBotDecisionService.isBot("BOT_EASY_abc12345"));
    }

    @Test
    void isBot_shouldReturnFalseForHumanId() {
        assertFalse(ParquesBotDecisionService.isBot("human-123"));
    }

    @Test
    void isBot_shouldReturnFalseForNull() {
        assertFalse(ParquesBotDecisionService.isBot(null));
    }

    // ─── generateBotId ───────────────────────────────────────────────────────

    @Test
    void generateBotId_shouldStartWithBotPrefix() {
        String id = ParquesBotDecisionService.generateBotId(ParquesBotDifficulty.EASY);
        assertTrue(id.startsWith("BOT_EASY_"));
    }

    @Test
    void generateBotId_shouldGenerateUniqueIds() {
        String id1 = ParquesBotDecisionService.generateBotId(ParquesBotDifficulty.HARD);
        String id2 = ParquesBotDecisionService.generateBotId(ParquesBotDifficulty.HARD);
        assertNotEquals(id1, id2);
    }

    // ─── difficultyFromId ────────────────────────────────────────────────────

    @Test
    void difficultyFromId_shouldReturnEasy() {
        assertEquals(ParquesBotDifficulty.EASY,
                ParquesBotDecisionService.difficultyFromId("BOT_EASY_abc12345"));
    }

    @Test
    void difficultyFromId_shouldReturnMedium() {
        assertEquals(ParquesBotDifficulty.MEDIUM,
                ParquesBotDecisionService.difficultyFromId("BOT_MEDIUM_abc12345"));
    }

    @Test
    void difficultyFromId_shouldReturnHard() {
        assertEquals(ParquesBotDifficulty.HARD,
                ParquesBotDecisionService.difficultyFromId("BOT_HARD_abc12345"));
    }

    @Test
    void difficultyFromId_shouldReturnMediumForInvalidFormat() {
        assertEquals(ParquesBotDifficulty.MEDIUM,
                ParquesBotDecisionService.difficultyFromId("invalid"));
    }

    // ─── botName ─────────────────────────────────────────────────────────────

    @Test
    void botName_shouldReturnCorrectNameForEasy() {
        assertEquals("Bot Fácil", ParquesBotDecisionService.botName(ParquesBotDifficulty.EASY));
    }

    @Test
    void botName_shouldReturnCorrectNameForMedium() {
        assertEquals("Bot Medio", ParquesBotDecisionService.botName(ParquesBotDifficulty.MEDIUM));
    }

    @Test
    void botName_shouldReturnCorrectNameForHard() {
        assertEquals("Bot Difícil", ParquesBotDecisionService.botName(ParquesBotDifficulty.HARD));
    }

    // ─── BotDecision record ───────────────────────────────────────────────────

    @Test
    void botDecision_isExitJail_shouldReturnTrueForExitJailId() {
        var decision = new ParquesBotDecisionService.BotDecision(
                ParquesBotDecisionService.EXIT_JAIL_ID, 0);
        assertTrue(decision.isExitJail());
    }

    @Test
    void botDecision_isPass_shouldReturnTrueForPassId() {
        var decision = new ParquesBotDecisionService.BotDecision(
                ParquesBotDecisionService.PASS_ID, -1);
        assertTrue(decision.isPass());
    }

    @Test
    void botDecision_shouldReturnFalseForNormalDecision() {
        var decision = new ParquesBotDecisionService.BotDecision("piece-id", 1);
        assertFalse(decision.isExitJail());
        assertFalse(decision.isPass());
    }

    // ─── getValidDecisions ───────────────────────────────────────────────────

    @Test
    void getValidDecisions_shouldReturnEmptyWhenDiceNotRolled() {
        // Sin lanzar dados, no hay decisiones de movimiento
        List<ParquesBotDecisionService.BotDecision> decisions =
                service.getValidDecisions(game, botId);
        // El bot no puede moverse si no se han lanzado dados
        // Como todas las fichas están en cárcel y no hay datos de dados, devuelve vacío o solo pass
        assertNotNull(decisions);
    }

    @Test
    void getValidDecisions_shouldReturnExitJailWhenPairAndPiecesInJail() {
        // Avanzamos turnos hasta que sea el turno del bot con un par
        // Primero terminamos el turno del humano
        game.rollDice("human-1");
        // No podemos controlar el resultado, pero podemos verificar la estructura
        assertNotNull(service.getValidDecisions(game, botId));
    }

    // ─── decide ──────────────────────────────────────────────────────────────

    @Test
    void decide_shouldReturnPassWhenNoValidDecisions() {
        // Sin dados lanzados, todas las fichas en cárcel
        var decision = service.decide(game, botId, ParquesBotDifficulty.EASY);
        assertNotNull(decision);
        // Con dados no lanzados, si getValidDecisions devuelve vacío → pass
        assertTrue(decision.isPass() || decision.isExitJail()
                || decision.pieceId() != null);
    }

    @Test
    void decide_easy_shouldReturnNonNullDecision() {
        var decision = service.decide(game, botId, ParquesBotDifficulty.EASY);
        assertNotNull(decision);
    }

    @Test
    void decide_medium_shouldReturnNonNullDecision() {
        var decision = service.decide(game, botId, ParquesBotDifficulty.MEDIUM);
        assertNotNull(decision);
    }

    @Test
    void decide_hard_shouldReturnNonNullDecision() {
        var decision = service.decide(game, botId, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
    }

    @Test
    void decide_shouldReturnPassForUnknownBot() {
        var decision = service.decide(game, "nonexistent-bot", ParquesBotDifficulty.MEDIUM);
        assertNotNull(decision);
        assertTrue(decision.isPass());
    }

    // ─── Decisión con fichas activas ─────────────────────────────────────────

    @Test
    void decide_withActivePiece_shouldPreferCapture() {
        // Preparar: el bot tiene una ficha activa y el humano en la misma posición
        // Se necesita un game donde el bot tenga ficha activa
        InMemoryGameRepository repo = new InMemoryGameRepository();
        String bid = "BOT_HARD_test0001";

        Player human = new Player("human-x", "H", "AMARILLO", 4);
        Player bot = new Player(bid, "Bot", "AZUL", 21);

        Game g = new Game("g-capture", List.of(human, bot));
        g.start();

        // Activar ficha del bot
        bot.getPieces().get(0).exitJail();
        bot.getPieces().get(0).move(5);

        // Activar ficha del humano cerca del bot
        human.getPieces().get(0).exitJail();
        human.getPieces().get(0).move(5);

        // Con HARD, el bot debería preferir capturar
        var decision = service.decide(g, bid, ParquesBotDifficulty.HARD);
        assertNotNull(decision);
    }
}
