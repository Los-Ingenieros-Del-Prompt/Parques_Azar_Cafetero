package com.aguardientes.azarcafetero.parques_service.entrypoints;

import com.aguardientes.azarcafetero.parques_service.application.usecases.CreateGameUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.MovePieceUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.RollDiceUseCase;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Endpoints for managing Parqués game sessions")
public class GameController {

    private final CreateGameUseCase createGameUseCase;
    private final RollDiceUseCase rollDiceUseCase;
    private final MovePieceUseCase movePieceUseCase;
    private final GameRepository gameRepository;

    public GameController(CreateGameUseCase createGameUseCase,
                          RollDiceUseCase rollDiceUseCase,
                          MovePieceUseCase movePieceUseCase,
                          GameRepository gameRepository) {
        this.createGameUseCase = createGameUseCase;
        this.rollDiceUseCase = rollDiceUseCase;
        this.movePieceUseCase = movePieceUseCase;
        this.gameRepository = gameRepository;
    }

    @Operation(
            summary = "Create a new game",
            description = "Creates a new Parqués game session with 2 to 4 players. " +
                    "The first turn is assigned randomly. Players are listed in clockwise order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Game created successfully",
                    content = @Content(schema = @Schema(example = "{\"gameId\": \"uuid-here\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid number of players (must be 2–4)",
                    content = @Content(schema = @Schema(example = "{\"error\": \"El juego requiere entre 2 y 4 jugadores\"}")))
    })
    @PostMapping
    public ResponseEntity<Map<String, String>> createGame(@RequestBody CreateGameRequest request) {
        List<CreateGameUseCase.PlayerInput> inputs = request.getPlayers().stream()
                .map(p -> new CreateGameUseCase.PlayerInput(p.getId(), p.getName()))
                .toList();
        Game game = createGameUseCase.execute(inputs);
        return ResponseEntity.ok(Map.of("gameId", game.getId()));
    }

    @Operation(
            summary = "Get game state",
            description = "Returns the full state of the game: current turn, dice values, " +
                    "each player's pieces, jail attempts remaining, and consecutive pairs count."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Game state returned successfully"),
            @ApiResponse(responseCode = "400", description = "Game not found",
                    content = @Content(schema = @Schema(example = "{\"error\": \"Juego no encontrado: uuid-here\"}")))
    })
    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> getGame(
            @Parameter(description = "Unique game identifier", required = true)
            @PathVariable String gameId) {
        Game game = gameRepository.findById(gameId);
        return ResponseEntity.ok(GameResponse.from(game));
    }

    @Operation(
            summary = "Roll the dice",
            description = "Rolls two dice for the current player. " +
                    "Rules applied automatically: " +
                    "(1) A pair with 2+ pieces in jail releases 2 pieces; 1-1 or 6-6 releases all. " +
                    "(2) A 5 on either die releases 1 piece from jail. " +
                    "(3) With all pieces in jail and no 5, a jail attempt is consumed; on the 3rd failed attempt a piece exits automatically. " +
                    "(4) Three consecutive pairs send the most advanced piece home. " +
                    "The response includes die1, die2, moveValue, and diceRolled=true when a move is expected."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dice rolled successfully — full game state returned"),
            @ApiResponse(responseCode = "400", description = "Not your turn, dice already rolled, or game is over",
                    content = @Content(schema = @Schema(example = "{\"error\": \"No es tu turno\"}")))
    })
    @PostMapping("/{gameId}/roll")
    public ResponseEntity<GameResponse> rollDice(
            @Parameter(description = "Unique game identifier", required = true)
            @PathVariable String gameId,
            @Parameter(description = "ID of the player rolling the dice", required = true)
            @RequestParam String playerId) {
        Game game = rollDiceUseCase.execute(gameId, playerId);
        return ResponseEntity.ok(GameResponse.from(game));
    }

    @Operation(
            summary = "Move a piece",
            description = "Moves the selected piece using the value from the last dice roll. " +
                    "Rules applied automatically: " +
                    "(1) A piece in jail can only exit if jailExitAvailable=true (a 5 was rolled). " +
                    "(2) If a kill was possible and was not made, the moved piece goes to jail. " +
                    "(3) Killing one opponent satisfies the obligation even if more kills were possible. " +
                    "(4) Exit squares (0, 17, 34, 51) are not safe — pieces there can be captured. " +
                    "(5) A piece can only enter the home column with an exact number. " +
                    "Must call /roll before /move on each turn."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Piece moved successfully — full game state returned"),
            @ApiResponse(responseCode = "400", description = "Invalid move: wrong turn, dice not rolled, exact number required, or piece in jail without exit available",
                    content = @Content(schema = @Schema(example = "{\"error\": \"Necesitas exactamente 3 para entrar a la casa\"}")))
    })
    @PostMapping("/{gameId}/move")
    public ResponseEntity<GameResponse> movePiece(
            @Parameter(description = "Unique game identifier", required = true)
            @PathVariable String gameId,
            @Parameter(description = "ID of the player moving a piece", required = true)
            @RequestParam String playerId,
            @Parameter(description = "ID of the piece to move (e.g. p1-piece-0, p1-piece-1)", required = true)
            @RequestParam String pieceId,
            @Parameter(description = "Dice selection (which dice result to use for the move)", required = true)
            @RequestParam int diceSelection) {
        Game game = movePieceUseCase.execute(gameId, playerId, pieceId, diceSelection);
        return ResponseEntity.ok(GameResponse.from(game));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleDomainErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}