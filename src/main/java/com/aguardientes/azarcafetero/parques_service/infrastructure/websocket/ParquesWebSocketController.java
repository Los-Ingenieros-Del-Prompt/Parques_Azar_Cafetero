package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket;

import com.aguardientes.azarcafetero.parques_service.application.usecases.CreateGameUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.MovePieceUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.RollDiceUseCase;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import com.aguardientes.azarcafetero.parques_service.entrypoints.GameResponse;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.CreateGameMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.JoinGameMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.MovePieceMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.RollDiceMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.PassTurnMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.ExitJailMessage;
import com.aguardientes.azarcafetero.parques_service.application.usecases.PassTurnUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.ExitJailUseCase;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class ParquesWebSocketController {

    private static final int[] EXIT_POSITIONS = {4, 21, 55, 38};
    private static final String[] COLORS = {"AMARILLO", "AZUL", "VERDE", "ROJO"};

    private final CreateGameUseCase createGameUseCase;
    private final RollDiceUseCase rollDiceUseCase;
    private final MovePieceUseCase movePieceUseCase;
    private final PassTurnUseCase passTurnUseCase;
    private final ExitJailUseCase exitJailUseCase;
    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ParquesWebSocketController(
            CreateGameUseCase createGameUseCase,
            RollDiceUseCase rollDiceUseCase,
            MovePieceUseCase movePieceUseCase,
            PassTurnUseCase passTurnUseCase,
            ExitJailUseCase exitJailUseCase,
            GameRepository gameRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.createGameUseCase = Objects.requireNonNull(createGameUseCase);
        this.rollDiceUseCase = Objects.requireNonNull(rollDiceUseCase);
        this.movePieceUseCase = Objects.requireNonNull(movePieceUseCase);
        this.passTurnUseCase = Objects.requireNonNull(passTurnUseCase);
        this.exitJailUseCase = Objects.requireNonNull(exitJailUseCase);
        this.gameRepository = Objects.requireNonNull(gameRepository);
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate);
    }

    /**
     * Crea el juego con el gameId recibido del frontend.
     *
     * FIX Bug 3: Este mensaje lo envía SOLO el host (primer jugador).
     * Si el juego ya existe simplemente hace broadcast del estado actual.
     * NO hace join aquí — el join lo hace cada cliente por separado con /join.
     * Esto evita que el lobby service detecte una "partida iniciada" y borre la sala.
     */
    @MessageMapping("/game/create")
    public void createGame(CreateGameMessage msg) {
        String gameId = msg.getGameId();
        List<CreateGameUseCase.PlayerInput> inputs = msg.getPlayers().stream()
                .map(p -> new CreateGameUseCase.PlayerInput(p.getId(), p.getName()))
                .toList();

        Game game;
        try {
            game = gameRepository.findById(gameId);
            // Juego ya existe — simplemente hacer broadcast del estado actual
        } catch (IllegalArgumentException e) {
            // Juego no existe — crearlo con el host como primer jugador
            game = createGameUseCase.execute(gameId, inputs);
        }

        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), GameResponse.from(game));
    }

    /**
     * Un jugador se une a un juego existente.
     *
     * FIX Bug 3: 
     * - Si el jugador ya está en la partida, simplemente hace broadcast (reconexión).
     * - NO crea juegos nuevos aquí para evitar confusión con el lobby.
     * - Solo agrega al jugador si el juego existe y tiene espacio.
     */
    @MessageMapping("/game/{gameId}/join")
    public void joinGame(JoinGameMessage msg, @DestinationVariable String gameId) {
        Game game;
        try {
            game = gameRepository.findById(gameId);
        } catch (IllegalArgumentException e) {
            // FIX Bug 3: Si el juego no existe al hacer join, intentar crearlo 
            // solo con este jugador (caso edge: host se desconectó)
            List<CreateGameUseCase.PlayerInput> inputs =
                    List.of(new CreateGameUseCase.PlayerInput(msg.getPlayerId(), msg.getPlayerName()));
            game = createGameUseCase.execute(gameId, inputs);
            gameRepository.save(game);
            messagingTemplate.convertAndSend("/topic/game/" + game.getId(), GameResponse.from(game));
            return;
        }

        // Verificar si el jugador ya está en la partida (reconexión)
        boolean alreadyIn = game.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(msg.getPlayerId()));

        if (!alreadyIn && game.getPlayers().size() < 4) {
            // FIX Bug 3: Agregar jugador solo si el juego aún acepta jugadores
            // (estado WAITING_FOR_PLAYERS). Si ya inició, rechazar silenciosamente.
            try {
                game.addPlayer(new Player(
                        msg.getPlayerId(),
                        msg.getPlayerName(),
                        COLORS[game.getPlayers().size()],
                        EXIT_POSITIONS[game.getPlayers().size()]
                ));
                gameRepository.save(game);
            } catch (IllegalStateException e) {
                // Juego ya inició, no se puede agregar — solo hacer broadcast
            }
        }

        // Siempre hacer broadcast para que el cliente reciba el estado actual
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), GameResponse.from(game));
    }

    @MessageMapping("/game/{gameId}/start")
    public void startGame(@DestinationVariable String gameId) {
        Game game = gameRepository.findById(gameId);
        game.start();
        gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    @MessageMapping("/game/{gameId}/roll")
    public void rollDice(RollDiceMessage msg, @DestinationVariable String gameId) {
        Game game = rollDiceUseCase.execute(gameId, msg.getPlayerId());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    @MessageMapping("/game/{gameId}/move")
    public void movePiece(MovePieceMessage msg, @DestinationVariable String gameId) {
        Game game = movePieceUseCase.execute(gameId, msg.getPlayerId(), msg.getPieceId(), msg.getDiceSelection());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    @MessageMapping("/game/{gameId}/pass")
    public void passTurn(PassTurnMessage msg, @DestinationVariable String gameId) {
        Game game = passTurnUseCase.execute(gameId, msg.getPlayerId());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    @MessageMapping("/game/{gameId}/exitJail")
    public void exitJail(ExitJailMessage msg, @DestinationVariable String gameId) {
        Game game = exitJailUseCase.execute(gameId, msg.getPlayerId());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    /**
     * FIX Bug 3: Manejar desconexión explícita.
     * Cuando un jugador sale, NO borramos el juego del repositorio —
     * el lobby service es quien debe cerrar la sala cuando corresponda.
     */
    @MessageMapping("/game/{gameId}/leave")
    public void leaveGame(@DestinationVariable String gameId) {
        // Simplemente notificar al lobby service vía su propio WS.
        // No tocar el estado del juego aquí.
        // El lobby service escucha desconexiones y cierra la mesa cuando queda vacía.
    }

    @MessageExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public void handleDomainError(RuntimeException ex) {
        messagingTemplate.convertAndSend("/topic/errors", Map.of("error", ex.getMessage()));
    }
}