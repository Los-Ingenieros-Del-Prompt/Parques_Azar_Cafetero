package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket;

import com.aguardientes.azarcafetero.parques_service.application.usecases.*;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDecisionService;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDecisionService.BotDecision;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDifficulty;
import com.aguardientes.azarcafetero.parques_service.entrypoints.GameResponse;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class ParquesWebSocketController {

    private static final int[] EXIT_POSITIONS = {4, 21, 55, 38};
    private static final String[] COLORS = {"AMARILLO", "AZUL", "VERDE", "ROJO"};

    /** Executor de un solo hilo para turnos de bot. No bloquea el handler de WebSocket. */
    private final ExecutorService botExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "parques-bot-thread");
        t.setDaemon(true);
        return t;
    });

    private final CreateGameUseCase createGameUseCase;
    private final RollDiceUseCase rollDiceUseCase;
    private final MovePieceUseCase movePieceUseCase;
    private final PassTurnUseCase passTurnUseCase;
    private final ExitJailUseCase exitJailUseCase;
    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ParquesBotDecisionService botDecisionService;

    public ParquesWebSocketController(
            CreateGameUseCase createGameUseCase,
            RollDiceUseCase rollDiceUseCase,
            MovePieceUseCase movePieceUseCase,
            PassTurnUseCase passTurnUseCase,
            ExitJailUseCase exitJailUseCase,
            GameRepository gameRepository,
            SimpMessagingTemplate messagingTemplate,
            ParquesBotDecisionService botDecisionService) {
        this.createGameUseCase  = Objects.requireNonNull(createGameUseCase);
        this.rollDiceUseCase    = Objects.requireNonNull(rollDiceUseCase);
        this.movePieceUseCase   = Objects.requireNonNull(movePieceUseCase);
        this.passTurnUseCase    = Objects.requireNonNull(passTurnUseCase);
        this.exitJailUseCase    = Objects.requireNonNull(exitJailUseCase);
        this.gameRepository     = Objects.requireNonNull(gameRepository);
        this.messagingTemplate  = Objects.requireNonNull(messagingTemplate);
        this.botDecisionService = Objects.requireNonNull(botDecisionService);
    }

    // ─── Mensajes existentes ──────────────────────────────────────────────────

    @MessageMapping("/game/create")
    public void createGame(CreateGameMessage msg) {
        String gameId = msg.getGameId();
        List<CreateGameUseCase.PlayerInput> inputs = msg.getPlayers().stream()
                .map(p -> new CreateGameUseCase.PlayerInput(p.getId(), p.getName()))
                .toList();

        Game game;
        try {
            game = gameRepository.findById(gameId);
        } catch (IllegalArgumentException e) {
            game = createGameUseCase.execute(gameId, inputs);
        }

        broadcast(game.getId());
    }

    @MessageMapping("/game/{gameId}/join")
    public void joinGame(JoinGameMessage msg, @DestinationVariable String gameId) {
        Game game;
        try {
            game = gameRepository.findById(gameId);
        } catch (IllegalArgumentException e) {
            List<CreateGameUseCase.PlayerInput> inputs =
                    List.of(new CreateGameUseCase.PlayerInput(msg.getPlayerId(), msg.getPlayerName()));
            game = createGameUseCase.execute(gameId, inputs);
            gameRepository.save(game);
            broadcast(game.getId());
            return;
        }

        boolean alreadyIn = game.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(msg.getPlayerId()));

        if (!alreadyIn && game.getPlayers().size() < 4) {
            try {
                game.addPlayer(new Player(
                        msg.getPlayerId(),
                        msg.getPlayerName(),
                        COLORS[game.getPlayers().size()],
                        EXIT_POSITIONS[game.getPlayers().size()]
                ));
                gameRepository.save(game);
            } catch (IllegalStateException ignored) {}
        }

        broadcast(game.getId());
    }

    @MessageMapping("/game/{gameId}/start")
    public void startGame(@DestinationVariable String gameId) {
        Game game = gameRepository.findById(gameId);
        game.start();
        gameRepository.save(game);
        broadcast(gameId);
        // Si el primer turno es de un bot, dispararlo
        triggerBotTurnIfNeeded(gameId);
    }

    @MessageMapping("/game/{gameId}/roll")
    public void rollDice(RollDiceMessage msg, @DestinationVariable String gameId) {
        rollDiceUseCase.execute(gameId, msg.getPlayerId());
        broadcast(gameId);
    }

    @MessageMapping("/game/{gameId}/move")
    public void movePiece(MovePieceMessage msg, @DestinationVariable String gameId) {
        movePieceUseCase.execute(gameId, msg.getPlayerId(), msg.getPieceId(), msg.getDiceSelection());
        broadcast(gameId);
        triggerBotTurnIfNeeded(gameId);
    }

    @MessageMapping("/game/{gameId}/pass")
    public void passTurn(PassTurnMessage msg, @DestinationVariable String gameId) {
        passTurnUseCase.execute(gameId, msg.getPlayerId());
        broadcast(gameId);
        triggerBotTurnIfNeeded(gameId);
    }

    @MessageMapping("/game/{gameId}/exitJail")
    public void exitJail(ExitJailMessage msg, @DestinationVariable String gameId) {
        exitJailUseCase.execute(gameId, msg.getPlayerId());
        broadcast(gameId);
        triggerBotTurnIfNeeded(gameId);
    }

    @MessageMapping("/game/{gameId}/leave")
    public void leaveGame(@DestinationVariable String gameId) {
        // El lobby service maneja el cierre de la sala
    }

    // ─── NUEVO: agregar bot ───────────────────────────────────────────────────

    /**
     * Agrega un bot a la partida antes de iniciarla.
     *
     * Mensaje cliente:
     *   stompClient.send('/app/game/GAME_ID/addBot', {},
     *       JSON.stringify({ difficulty: 'EASY' | 'MEDIUM' | 'HARD' }));
     */
    @MessageMapping("/game/{gameId}/addBot")
    public void addBot(AddBotRequest req, @DestinationVariable String gameId) {
        ParquesBotDifficulty difficulty = (req != null && req.difficulty() != null)
                ? req.difficulty()
                : ParquesBotDifficulty.MEDIUM;

        Game game = gameRepository.findById(gameId);

        if (game.getPlayers().size() >= 4) return; // sala llena

        String botId   = ParquesBotDecisionService.generateBotId(difficulty);
        String botName = ParquesBotDecisionService.botName(difficulty);
        int index      = game.getPlayers().size();

        game.addPlayer(new Player(botId, botName, COLORS[index], EXIT_POSITIONS[index]));
        gameRepository.save(game);
        broadcast(gameId);
    }

    // ─── Bot turn engine ─────────────────────────────────────────────────────

    /**
     * Encola el turno del bot en el executor de un solo hilo.
     * Retorna inmediatamente — no bloquea el handler de WebSocket.
     */
    private void triggerBotTurnIfNeeded(String gameId) {
        try {
            Game game = gameRepository.findById(gameId);
            if (game.isFinished()) return;
            Player current = game.getCurrentPlayer();
            if (current != null && ParquesBotDecisionService.isBot(current.getId())) {
                botExecutor.submit(() -> runBotTurns(gameId));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Ejecuta los turnos consecutivos del bot:
     *   1. Lanza dados
     *   2. Sale de la cárcel si corresponde
     *   3. Mueve fichas mientras haya dados disponibles
     *   4. Si saca par → el turno se queda con él → el loop vuelve a empezar
     *   5. Se detiene cuando pasa el turno a un humano o termina la partida
     */
    private void runBotTurns(String gameId) {
        int safetyLimit = 30; // evitar loop infinito ante cualquier estado raro

        while (safetyLimit-- > 0) {
            Game game = safeLoad(gameId);
            if (game == null || game.isFinished()) break;

            Player current = game.getCurrentPlayer();
            if (current == null || !ParquesBotDecisionService.isBot(current.getId())) break;

            String botId = current.getId();
            ParquesBotDifficulty difficulty = ParquesBotDecisionService.difficultyFromId(botId);

            // ── 1. Lanzar dados ───────────────────────────────────────────────
            if (!game.isDiceRolled()) {
                sleep(700);
                try {
                    rollDiceUseCase.execute(gameId, botId);
                } catch (Exception e) {
                    break;
                }
                broadcast(gameId);
                sleep(600);
                continue; // re-leer el estado actualizado
            }

            // ── 2. Salir de la cárcel ─────────────────────────────────────────
            game = safeLoad(gameId);
            if (game == null || game.isFinished()) break;

            if (game.isJailExitAvailable()) {
                // El bot evalúa si salir de la cárcel o mover una ficha activa
                BotDecision decision = botDecisionService.decide(game, botId, difficulty);
                if (decision.isExitJail()) {
                    sleep(500);
                    try {
                        exitJailUseCase.execute(gameId, botId);
                    } catch (Exception e) {
                        break;
                    }
                    broadcast(gameId);
                    sleep(500);
                    continue;
                }
                // Si el bot decide NO salir de la cárcel, cae al paso 3
            }

            // ── 3. Mover ficha ────────────────────────────────────────────────
            game = safeLoad(gameId);
            if (game == null || game.isFinished()) break;

            if (!game.isDiceRolled()) continue; // los dados se consumieron (exit jail con par)

            BotDecision decision = botDecisionService.decide(game, botId, difficulty);

            if (decision.isPass() || decision.isExitJail()) {
                // No hay movimientos → pasar turno
                sleep(400);
                try {
                    passTurnUseCase.execute(gameId, botId);
                } catch (Exception e) {
                    break;
                }
                broadcast(gameId);
                continue;
            }

            sleep(750);
            try {
                movePieceUseCase.execute(gameId, botId, decision.pieceId(), decision.diceSelection());
            } catch (Exception e) {
                // Movimiento inválido — pasar turno como fallback
                try { passTurnUseCase.execute(gameId, botId); } catch (Exception ignored) {}
            }
            broadcast(gameId);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void broadcast(String gameId) {
        try {
            Game game = gameRepository.findById(gameId);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
        } catch (Exception ignored) {}
    }

    private Game safeLoad(String gameId) {
        try { return gameRepository.findById(gameId); }
        catch (Exception e) { return null; }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @MessageExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public void handleDomainError(RuntimeException ex) {
        messagingTemplate.convertAndSend("/topic/errors", Map.of("error", ex.getMessage()));
    }

    // ─── DTO de entrada para addBot ───────────────────────────────────────────

    public record AddBotRequest(ParquesBotDifficulty difficulty) {}
}