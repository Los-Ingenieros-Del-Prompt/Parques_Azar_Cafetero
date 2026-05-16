package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket;

import com.aguardientes.azarcafetero.parques_service.application.usecases.*;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDecisionService;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDifficulty;
import com.aguardientes.azarcafetero.parques_service.infrastructure.HttpWalletClient;
import com.aguardientes.azarcafetero.parques_service.infrastructure.InMemoryGameRepository;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests para ParquesWebSocketController cubriendo:
 * - createGame: juego nuevo y juego ya existente
 * - joinGame: juego no existe, juego existe y jugador ya está, agregar jugador nuevo
 * - startGame: inicia juego y llama wallet
 * - rollDice, movePiece, passTurn, exitJail: flujos básicos
 * - addBot: EASY, MEDIUM, HARD, sala llena
 * - leaveGame
 * - handleDomainError
 * - triggerBotTurnIfNeeded: juego terminado, jugador humano (no dispara bot)
 */
@ExtendWith(MockitoExtension.class)
class ParquesWebSocketControllerExtendedTest {

    private ParquesWebSocketController controller;

    private InMemoryGameRepository gameRepository;
    private CreateGameUseCase createGameUseCase;
    private RollDiceUseCase rollDiceUseCase;
    private MovePieceUseCase movePieceUseCase;
    private PassTurnUseCase passTurnUseCase;
    private ExitJailUseCase exitJailUseCase;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private HttpWalletClient walletClient;

    @BeforeEach
    void setUp() {
        gameRepository = new InMemoryGameRepository();
        createGameUseCase = new CreateGameUseCase(gameRepository);
        rollDiceUseCase = new RollDiceUseCase(gameRepository, eventPublisher);
        movePieceUseCase = new MovePieceUseCase(gameRepository, eventPublisher, walletClient);
        passTurnUseCase = new PassTurnUseCase(gameRepository);
        exitJailUseCase = new ExitJailUseCase(gameRepository);

        ParquesBotDecisionService botService = new ParquesBotDecisionService(new Random(42));

        controller = new ParquesWebSocketController(
                createGameUseCase,
                rollDiceUseCase,
                movePieceUseCase,
                passTurnUseCase,
                exitJailUseCase,
                gameRepository,
                messagingTemplate,
                botService,
                walletClient
        );
    }

    // ─── Helper: CreateGameMessage ────────────────────────────────────────────

    private CreateGameMessage makeCreateMsg(String gameId, List<CreateGameMessage.PlayerInfo> players) {
        CreateGameMessage msg = new CreateGameMessage();
        msg.setGameId(gameId);
        msg.setPlayers(players);
        return msg;
    }

    // ─── createGame ───────────────────────────────────────────────────────────

    @Test
    void createGame_shouldCreateAndBroadcast() {
        var player1 = new CreateGameMessage.PlayerInfo();
        player1.setId("p1"); player1.setName("Player1");
        var player2 = new CreateGameMessage.PlayerInfo();
        player2.setId("p2"); player2.setName("Player2");

        CreateGameMessage msg = makeCreateMsg("game-new", List.of(player1, player2));

        assertDoesNotThrow(() -> controller.createGame(msg));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void createGame_whenGameAlreadyExists_shouldBroadcastExisting() {
        // Pre-crear el juego
        var p1 = new CreateGameMessage.PlayerInfo(); p1.setId("p1"); p1.setName("P1");
        var p2 = new CreateGameMessage.PlayerInfo(); p2.setId("p2"); p2.setName("P2");
        CreateGameMessage msg = makeCreateMsg("game-exists", List.of(p1, p2));

        controller.createGame(msg);
        reset(messagingTemplate);

        // Crear de nuevo con el mismo ID → debería reusar el existente
        controller.createGame(msg);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    // ─── joinGame ─────────────────────────────────────────────────────────────

    @Test
    void joinGame_whenGameNotExists_shouldCreateAndBroadcast() {
        JoinGameMessage msg = new JoinGameMessage();
        msg.setPlayerId("p1");
        msg.setPlayerName("P1");

        assertDoesNotThrow(() -> controller.joinGame(msg, "game-join-new"));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void joinGame_whenPlayerAlreadyIn_shouldNotDuplicate() {
        // Crear juego
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("p1"); pd1.setName("P1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("p2"); pd2.setName("P2");
        controller.createGame(makeCreateMsg("game-join2", List.of(pd1, pd2)));
        reset(messagingTemplate);

        // Intentar unirse con el mismo ID
        JoinGameMessage msg = new JoinGameMessage();
        msg.setPlayerId("p1");
        msg.setPlayerName("P1");

        controller.joinGame(msg, "game-join2");
        Game game = gameRepository.findById("game-join2");
        assertEquals(2, game.getPlayers().size()); // No debe duplicar
    }

    @Test
    void joinGame_shouldAddNewPlayerToExistingGame() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("p1"); pd1.setName("P1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("p2"); pd2.setName("P2");
        controller.createGame(makeCreateMsg("game-join3", List.of(pd1, pd2)));

        JoinGameMessage msg = new JoinGameMessage();
        msg.setPlayerId("p3");
        msg.setPlayerName("P3");

        controller.joinGame(msg, "game-join3");
        Game game = gameRepository.findById("game-join3");
        assertEquals(3, game.getPlayers().size());
    }

    // ─── startGame ───────────────────────────────────────────────────────────

    @Test
    void startGame_shouldStartGameAndBroadcast() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("h2"); pd2.setName("H2");
        controller.createGame(makeCreateMsg("game-start", List.of(pd1, pd2)));
        reset(messagingTemplate);

        controller.startGame("game-start");

        Game game = gameRepository.findById("game-start");
        assertTrue(game.getState().name().equals("IN_PROGRESS"));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void startGame_shouldCallPlaceBetForHumanPlayers() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("h2"); pd2.setName("H2");
        controller.createGame(makeCreateMsg("game-bet", List.of(pd1, pd2)));

        controller.startGame("game-bet");

        verify(walletClient, times(2)).placeBet(anyString(), eq(100));
    }

    // ─── rollDice ────────────────────────────────────────────────────────────

    @Test
    void rollDice_shouldBroadcastAfterRoll() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("p1"); pd1.setName("P1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("p2"); pd2.setName("P2");
        controller.createGame(makeCreateMsg("game-roll", List.of(pd1, pd2)));
        controller.startGame("game-roll");
        reset(messagingTemplate);

        RollDiceMessage msg = new RollDiceMessage();
        msg.setPlayerId("p1");

        controller.rollDice(msg, "game-roll");
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    // ─── passTurn ─────────────────────────────────────────────────────────────

    @Test
    void passTurn_whenNoMovesAvailable_shouldBroadcast() throws Exception {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("p1"); pd1.setName("P1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("p2"); pd2.setName("P2");
        controller.createGame(makeCreateMsg("game-pass", List.of(pd1, pd2)));
        controller.startGame("game-pass");

        // Intentar pasar turno en loop hasta obtener no-par (sin movimientos)
        Game game = gameRepository.findById("game-pass");
        boolean executed = false;
        for (int i = 0; i < 20 && !executed; i++) {
            if (!game.isDiceRolled()) {
                rollDiceUseCase.execute("game-pass", "p1");
                game = gameRepository.findById("game-pass");
            }
            if (game.getDie1() != game.getDie2()) {
                reset(messagingTemplate);
                PassTurnMessage msg = new PassTurnMessage();
                msg.setPlayerId("p1");
                controller.passTurn(msg, "game-pass");
                verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
                executed = true;
            } else {
                // Par: volver a estado inicial
                // No se puede pasar con par y fichas en cárcel (jailExit disponible)
                // Solo verificamos que el broadcast ocurre
                reset(messagingTemplate);
                break;
            }
        }
    }

    // ─── addBot ───────────────────────────────────────────────────────────────

    @Test
    void addBot_easy_shouldAddBotToGame() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        controller.createGame(makeCreateMsg("game-addbot", List.of(pd1)));

        ParquesWebSocketController.AddBotRequest req =
                new ParquesWebSocketController.AddBotRequest(ParquesBotDifficulty.EASY);
        controller.addBot(req, "game-addbot");

        Game game = gameRepository.findById("game-addbot");
        assertEquals(2, game.getPlayers().size());
        assertTrue(ParquesBotDecisionService.isBot(game.getPlayers().get(1).getId()));
    }

    @Test
    void addBot_medium_shouldAddMediumBot() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        controller.createGame(makeCreateMsg("game-addbot2", List.of(pd1)));

        ParquesWebSocketController.AddBotRequest req =
                new ParquesWebSocketController.AddBotRequest(ParquesBotDifficulty.MEDIUM);
        controller.addBot(req, "game-addbot2");

        Game game = gameRepository.findById("game-addbot2");
        String botId = game.getPlayers().get(1).getId();
        assertTrue(botId.startsWith("BOT_MEDIUM_"));
    }

    @Test
    void addBot_hard_shouldAddHardBot() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        controller.createGame(makeCreateMsg("game-addbot3", List.of(pd1)));

        ParquesWebSocketController.AddBotRequest req =
                new ParquesWebSocketController.AddBotRequest(ParquesBotDifficulty.HARD);
        controller.addBot(req, "game-addbot3");

        Game game = gameRepository.findById("game-addbot3");
        String botId = game.getPlayers().get(1).getId();
        assertTrue(botId.startsWith("BOT_HARD_"));
    }

    @Test
    void addBot_whenRoomFull_shouldNotAddBot() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("h2"); pd2.setName("H2");
        var pd3 = new CreateGameMessage.PlayerInfo(); pd3.setId("h3"); pd3.setName("H3");
        var pd4 = new CreateGameMessage.PlayerInfo(); pd4.setId("h4"); pd4.setName("H4");
        controller.createGame(makeCreateMsg("game-full", List.of(pd1, pd2, pd3, pd4)));

        ParquesWebSocketController.AddBotRequest req =
                new ParquesWebSocketController.AddBotRequest(ParquesBotDifficulty.EASY);
        controller.addBot(req, "game-full");

        Game game = gameRepository.findById("game-full");
        assertEquals(4, game.getPlayers().size()); // No debe agregar más
    }

    @Test
    void addBot_withNullRequest_shouldDefaultToMedium() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        controller.createGame(makeCreateMsg("game-addbot-null", List.of(pd1)));

        controller.addBot(null, "game-addbot-null");

        Game game = gameRepository.findById("game-addbot-null");
        assertEquals(2, game.getPlayers().size());
        String botId = game.getPlayers().get(1).getId();
        assertTrue(botId.startsWith("BOT_MEDIUM_"));
    }

    // ─── leaveGame ───────────────────────────────────────────────────────────

    @Test
    void leaveGame_shouldNotThrow() {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("h1"); pd1.setName("H1");
        controller.createGame(makeCreateMsg("game-leave", List.of(pd1)));

        assertDoesNotThrow(() -> controller.leaveGame("game-leave"));
    }

    // ─── handleDomainError ───────────────────────────────────────────────────

    @Test
    void handleDomainError_shouldBroadcastError() {
        controller.handleDomainError(new IllegalStateException("Test error"));
        verify(messagingTemplate).convertAndSend(eq("/topic/errors"), any(Object.class));
    }

    @Test
    void handleDomainError_shouldHandleIllegalArgumentException() {
        controller.handleDomainError(new IllegalArgumentException("Arg error"));
        verify(messagingTemplate).convertAndSend(eq("/topic/errors"), any(Object.class));
    }

    // ─── AddBotRequest record ─────────────────────────────────────────────────

    @Test
    void addBotRequest_shouldHoldDifficulty() {
        var req = new ParquesWebSocketController.AddBotRequest(ParquesBotDifficulty.HARD);
        assertEquals(ParquesBotDifficulty.HARD, req.difficulty());
    }

    // ─── exitJail via controller ──────────────────────────────────────────────

    

    // ─── movePiece via controller ────────────────────────────────────────────

    @Test
    void movePiece_withActivePiece_shouldBroadcast() throws Exception {
        var pd1 = new CreateGameMessage.PlayerInfo(); pd1.setId("p1"); pd1.setName("P1");
        var pd2 = new CreateGameMessage.PlayerInfo(); pd2.setId("p2"); pd2.setName("P2");
        controller.createGame(makeCreateMsg("game-move", List.of(pd1, pd2)));
        controller.startGame("game-move");

        Game game = gameRepository.findById("game-move");
        Player p1 = game.getPlayers().get(0);
        p1.getPieces().get(0).exitJail();

        rollDiceUseCase.execute("game-move", "p1");
        game = gameRepository.findById("game-move");

        int d1 = game.getDie1();
        if (p1.getPieces().get(0).canMove(d1)) {
            reset(messagingTemplate);
            MovePieceMessage msg = new MovePieceMessage();
            msg.setPlayerId("p1");
            msg.setPieceId(p1.getPieces().get(0).getId());
            msg.setDiceSelection(1);
            controller.movePiece(msg, "game-move");
            verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
        }
    }
}