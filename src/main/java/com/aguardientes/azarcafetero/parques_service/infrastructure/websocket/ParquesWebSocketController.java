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

import java.util.ArrayList;
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
     * Si ya existe, lo ignora. Siempre hace join del jugador.
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
            // El juego ya existe — no hacer nada, el join se encarga
        } catch (IllegalArgumentException e) {
            // El juego no existe — crearlo
            game = createGameUseCase.execute(gameId, inputs);
        }

        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), GameResponse.from(game));
    }

    /**
     * Un jugador se une a un juego existente.
     * Send to: /app/game/{gameId}/join
     * Payload: { "gameId": "...", "playerId": "...", "playerName": "..." }
     */
    @MessageMapping("/game/{gameId}/join")
    public void joinGame(JoinGameMessage msg, @DestinationVariable String gameId) {
        Game game;
        try {
            game = gameRepository.findById(gameId);
        } catch (IllegalArgumentException e) {
            List<CreateGameUseCase.PlayerInput> inputs =
                    List.of(new CreateGameUseCase.PlayerInput(msg.getPlayerId(), msg.getPlayerName()));
            game = createGameUseCase.execute(gameId, inputs);
            messagingTemplate.convertAndSend("/topic/game/" + game.getId(), GameResponse.from(game));
            return;
        }

        boolean alreadyIn = game.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(msg.getPlayerId()));

        if (!alreadyIn && game.getPlayers().size() < 4) {
            game.addPlayer(new Player(
                    msg.getPlayerId(),
                    msg.getPlayerName(),
                    COLORS[game.getPlayers().size()],
                    EXIT_POSITIONS[game.getPlayers().size()]
            ));
            gameRepository.save(game);
        }

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
    @MessageExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public void handleDomainError(RuntimeException ex) {
        messagingTemplate.convertAndSend("/topic/errors", Map.of("error", ex.getMessage()));
    }
}
