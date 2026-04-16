package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket;

import com.aguardientes.azarcafetero.parques_service.application.usecases.CreateGameUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.MovePieceUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.RollDiceUseCase;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.entrypoints.GameResponse;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.CreateGameMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.MovePieceMessage;
import com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto.RollDiceMessage;
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

    private final CreateGameUseCase createGameUseCase;
    private final RollDiceUseCase rollDiceUseCase;
    private final MovePieceUseCase movePieceUseCase;
    private final SimpMessagingTemplate messagingTemplate;

    public ParquesWebSocketController(
            CreateGameUseCase createGameUseCase,
            RollDiceUseCase rollDiceUseCase,
            MovePieceUseCase movePieceUseCase,
            SimpMessagingTemplate messagingTemplate) {
        this.createGameUseCase = Objects.requireNonNull(createGameUseCase);
        this.rollDiceUseCase = Objects.requireNonNull(rollDiceUseCase);
        this.movePieceUseCase = Objects.requireNonNull(movePieceUseCase);
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate);
    }

    /**
     * Create a new Parqués game.
     * Send to: /app/game/create
     * Payload: { "players": [{"id":"p1","name":"Alice"}, ...] }
     * Broadcasts to: /topic/lobby  and  /topic/game/{gameId}
     */
    @MessageMapping("/game/create")
    public void createGame(CreateGameMessage msg) {
        List<CreateGameUseCase.PlayerInput> inputs = msg.getPlayers().stream()
                .map(p -> new CreateGameUseCase.PlayerInput(p.getId(), p.getName()))
                .toList();

        Game game = createGameUseCase.execute(inputs);
        GameResponse response = GameResponse.from(game);

        messagingTemplate.convertAndSend("/topic/lobby", response);
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), response);
    }

    /**
     * Roll dice for the current player.
     * Send to: /app/game/{gameId}/roll
     * Payload: { "playerId": "p1" }
     * Broadcasts to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/roll")
    public void rollDice(
            RollDiceMessage msg,
            @DestinationVariable String gameId) {

        Game game = rollDiceUseCase.execute(gameId, msg.getPlayerId());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    /**
     * Move a piece for the current player.
     * Send to: /app/game/{gameId}/move
     * Payload: { "playerId": "p1", "pieceId": "p1-piece-0" }
     * Broadcasts to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/move")
    public void movePiece(
            MovePieceMessage msg,
            @DestinationVariable String gameId) {

        Game game = movePieceUseCase.execute(gameId, msg.getPlayerId(), msg.getPieceId());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameResponse.from(game));
    }

    /**
     * Handles domain errors (wrong turn, invalid move, etc.) and sends
     * the error message back to the /topic/game/{gameId}/errors topic
     * so the client can display it without losing connection.
     */
    @MessageExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public void handleDomainError(RuntimeException ex) {
        // Best-effort: broadcast error to a general errors topic.
        // Clients can subscribe to /topic/errors for user-facing messages.
        messagingTemplate.convertAndSend("/topic/errors",
                Map.of("error", ex.getMessage()));
    }
}
