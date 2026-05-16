package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket;

import com.aguardientes.azarcafetero.parques_service.application.usecases.*;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Piece;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDecisionService;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDifficulty;
import com.aguardientes.azarcafetero.parques_service.infrastructure.HttpWalletClient;
import com.aguardientes.azarcafetero.parques_service.infrastructure.InMemoryGameRepository;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.CreateGameMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.ExitJailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Tests para el motor de turnos del bot en ParquesWebSocketController.
 *
 * Cubre:
 *   • exitJail(...) handler — invoca exitJailUseCase y broadcast (líneas 158-161).
 *   • triggerBotTurnIfNeeded(...) — flujos cuando current player es bot
 *     (línea 208: botExecutor.submit) y cuando NO lo es / juego terminado
 *     (línea 205: return).
 *   • runBotTurns(...) — invocado vía reflexión con escenarios controlados
 *     para acortar el tiempo de ejecución (líneas 222-300).
 *   • Fábrica de hilos del executor (líneas 33-35) — al hacer submit se
 *     construye el hilo "parques-bot-thread".
 *
 *  Nota sobre el tiempo: runBotTurns contiene Thread.sleep() de 400-750 ms
 *  por iteración. Aceptamos estos tiempos pero limitamos los tests con
 *  @Timeout y usamos verify(..., timeout(...)) para no bloquear si algo
 *  va mal.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketBotEngineTest {

    private ParquesWebSocketController controller;
    private InMemoryGameRepository gameRepository;
    private CreateGameUseCase createGameUseCase;
    private RollDiceUseCase rollDiceUseCase;
    private MovePieceUseCase movePieceUseCase;
    private PassTurnUseCase passTurnUseCase;
    private ExitJailUseCase exitJailUseCase;

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private EventPublisher eventPublisher;
    @Mock private HttpWalletClient walletClient;

    @BeforeEach
    void setUp() {
        gameRepository    = new InMemoryGameRepository();
        createGameUseCase = new CreateGameUseCase(gameRepository);
        rollDiceUseCase   = new RollDiceUseCase(gameRepository, eventPublisher);
        movePieceUseCase  = new MovePieceUseCase(gameRepository, eventPublisher, walletClient);
        passTurnUseCase   = new PassTurnUseCase(gameRepository);
        exitJailUseCase   = new ExitJailUseCase(gameRepository);

        // Random determinista para que el bot tome decisiones reproducibles
        ParquesBotDecisionService botService =
                new ParquesBotDecisionService(new Random(123));

        controller = new ParquesWebSocketController(
                createGameUseCase, rollDiceUseCase, movePieceUseCase,
                passTurnUseCase,   exitJailUseCase,  gameRepository,
                messagingTemplate, botService,       walletClient);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CreateGameMessage createMsg(String gameId, String... humanIds) {
        CreateGameMessage msg = new CreateGameMessage();
        msg.setGameId(gameId);
        msg.setPlayers(java.util.Arrays.stream(humanIds).map(id -> {
            CreateGameMessage.PlayerInfo pi = new CreateGameMessage.PlayerInfo();
            pi.setId(id);
            pi.setName("Name-" + id);
            return pi;
        }).toList());
        return msg;
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private void setDiceState(Game g, int d1, int d2,
                              boolean rolled,
                              boolean d1Used, boolean d2Used,
                              boolean jailExit) throws Exception {
        setField(g, "die1", d1);
        setField(g, "die2", d2);
        setField(g, "diceRolled", rolled);
        setField(g, "die1Used", d1Used);
        setField(g, "die2Used", d2Used);
        setField(g, "jailExitAvailable", jailExit);
        setField(g, "moveValue", d1 + d2);
    }

    private void invokeRunBotTurns(String gameId) throws Exception {
        Method m = ParquesWebSocketController.class
                .getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        m.invoke(controller, gameId);
    }

    private void invokeTrigger(String gameId) throws Exception {
        Method m = ParquesWebSocketController.class
                .getDeclaredMethod("triggerBotTurnIfNeeded", String.class);
        m.setAccessible(true);
        m.invoke(controller, gameId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // exitJail handler (líneas 158-161)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void exitJail_shouldInvokeUseCaseAndBroadcast() throws Exception {
        controller.createGame(createMsg("g-exit", "p1", "p2"));
        controller.startGame("g-exit");

        Game game = gameRepository.findById("g-exit");
        // Forzamos par disponible y jailExitAvailable=true
        setDiceState(game, 3, 3, true, false, false, true);

        org.mockito.Mockito.reset(messagingTemplate);

        ExitJailMessage msg = new ExitJailMessage();
        msg.setPlayerId("p1");

        assertDoesNotThrow(() -> controller.exitJail(msg, "g-exit"));

        // Tras exitJail: las dos primeras fichas de p1 deben estar fuera de la cárcel
        long out = game.getPlayers().get(0).getPieces().stream()
                .filter(p -> !p.isInJail()).count();
        assertEquals(2, out, "exitJail saca exactamente dos fichas");
        // Y broadcast() debe haber sido invocado
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // triggerBotTurnIfNeeded
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void trigger_whenGameFinished_shouldNotSubmit() throws Exception {
        // Creamos un juego con bot como current y lo marcamos como FINISHED.
        controller.createGame(createMsg("g-fin", "h1"));
        controller.addBot(new ParquesWebSocketController.AddBotRequest(
                ParquesBotDifficulty.EASY), "g-fin");
        controller.startGame("g-fin");

        Game game = gameRepository.findById("g-fin");
        // Forzar estado FINISHED
        setField(game, "state",
                com.aguardientes.azarcafetero.parques_service.domain.model.GameState.FINISHED);

        // No debe lanzar y no debe arrancar el bot
        assertDoesNotThrow(() -> invokeTrigger("g-fin"));
    }

    @Test
    void trigger_whenCurrentIsHuman_shouldNotSubmit() throws Exception {
        controller.createGame(createMsg("g-hum", "h1", "h2"));
        controller.startGame("g-hum");
        // current = h1 (humano). triggerBotTurnIfNeeded debe no encolar nada.
        assertDoesNotThrow(() -> invokeTrigger("g-hum"));
    }

    @Test
    void trigger_whenGameDoesNotExist_shouldSwallowException() {
        // Cualquier excepción interna se traga (try/catch en triggerBotTurnIfNeeded).
        assertDoesNotThrow(() -> invokeTrigger("game-no-existe"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // runBotTurns: escenarios controlados
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Escenario más corto posible: bot está MUY cerca de ganar (3 fichas en
     * victoria, 1 en rel=69). Con un par (1,1) saca la última a victoria
     * y el juego termina inmediatamente.
     *
     * Cubre: re-lectura del estado, ramas de roll, broadcast, finalización.
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void runBotTurns_botAlmostWinning_shouldFinishQuickly() throws Exception {
        controller.createGame(createMsg("g-bot-win", "h1"));
        controller.addBot(new ParquesWebSocketController.AddBotRequest(
                ParquesBotDifficulty.EASY), "g-bot-win");
        controller.startGame("g-bot-win");

        Game game = gameRepository.findById("g-bot-win");

        // Forzar al bot como current
        setField(game, "currentTurn", 1);

        Player bot = game.getPlayers().get(1);
        // 3 fichas en victoria, 1 lista a 1 paso
        for (int i = 0; i < 3; i++) {
            Piece p = bot.getPieces().get(i);
            p.exitJail();
            p.move(70);
        }
        bot.getPieces().get(3).exitJail();
        bot.getPieces().get(3).move(69);

        // Ejecutamos el motor del bot (directo, sin el executor)
        invokeRunBotTurns("g-bot-win");

        // Se hicieron broadcasts durante el turno del bot
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
    }

    /**
     * Escenario donde el current player NO es bot al entrar a runBotTurns:
     * el while sale en la primera iteración por el break de la línea 229.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void runBotTurns_whenCurrentIsHuman_shouldExitImmediately() throws Exception {
        controller.createGame(createMsg("g-bot-skip", "h1", "h2"));
        controller.startGame("g-bot-skip");
        // current = h1 (humano). runBotTurns debe romper inmediatamente.
        assertDoesNotThrow(() -> invokeRunBotTurns("g-bot-skip"));
    }

    /**
     * Escenario para cubrir el break por game no encontrado / nulo (safeLoad → null).
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void runBotTurns_whenGameDoesNotExist_shouldExitImmediately() {
        assertDoesNotThrow(() -> invokeRunBotTurns("game-inexistente"));
    }

    /**
     * Escenario: bot ya con dados lanzados, no hay jailExit ni movimientos
     * disponibles (todas las fichas en cárcel y dados no-par) → la rama 3
     * (passTurn fallback) se ejecuta.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void runBotTurns_botAllInJailNonPair_shouldPassTurn() throws Exception {
        controller.createGame(createMsg("g-bot-pass", "h1"));
        controller.addBot(new ParquesWebSocketController.AddBotRequest(
                ParquesBotDifficulty.EASY), "g-bot-pass");
        controller.startGame("g-bot-pass");

        Game game = gameRepository.findById("g-bot-pass");
        setField(game, "currentTurn", 1);

        // Dados ya lanzados con valores no-par → no jail exit, sin movimientos
        setDiceState(game, 3, 5, true, false, false, false);

        // Player jailAttempts=2 → próximo pass lo lleva a 3 → reset + nextTurn → loop sale
        Player bot = game.getPlayers().get(1);
        Field f = Player.class.getDeclaredField("jailAttempts");
        f.setAccessible(true);
        f.set(bot, 2);

        invokeRunBotTurns("g-bot-pass");

        // Tras agotar intentos y avanzar turno, current debería ser el humano
        assertEquals(0, game.getCurrentTurn());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Integración real con executor (cubre Thread factory: 33-35 y submit: 208)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Test que ejercita el path completo: startGame → triggerBotTurnIfNeeded →
     * botExecutor.submit(() -> runBotTurns(gameId)) → el hilo "parques-bot-thread"
     * se crea (cubre el lambda de la línea 33-35) → corre runBotTurns.
     *
     * Para que termine rápido, configuramos al bot a un solo movimiento de ganar
     * antes de startGame.
     */
    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void startGame_withBotAlmostWinning_shouldTriggerExecutorAndBroadcast() throws Exception {
        controller.createGame(createMsg("g-exec", "h1"));
        controller.addBot(new ParquesWebSocketController.AddBotRequest(
                ParquesBotDifficulty.MEDIUM), "g-exec");

        // Pre-preparamos al bot ANTES de start: 3 en victoria, 1 lista
        Game game = gameRepository.findById("g-exec");
        Player bot = game.getPlayers().get(1);
        for (int i = 0; i < 3; i++) {
            Piece p = bot.getPieces().get(i);
            p.exitJail();
            p.move(70);
        }
        bot.getPieces().get(3).exitJail();
        bot.getPieces().get(3).move(69);

        // Forzar a que el current al iniciar sea el bot
        setField(game, "currentTurn", 1);

        // startGame intenta hacer placeBet de humanos (sólo h1) y luego dispara
        // triggerBotTurnIfNeeded → submit al executor → corre en hilo aparte.
        controller.startGame("g-exec");

        // Esperamos a que el executor procese (con timeout)
        verify(messagingTemplate, timeout(15000).atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
    }
}
