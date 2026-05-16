package com.aguardientes.azarcafetero.parques_service.entrypoints;

import com.aguardientes.azarcafetero.parques_service.application.usecases.CreateGameUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.MovePieceUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.RollDiceUseCase;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock
    private CreateGameUseCase createGameUseCase;

    @Mock
    private RollDiceUseCase rollDiceUseCase;

    @Mock
    private MovePieceUseCase movePieceUseCase;

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private GameController controller;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game("game-1", List.of(
                new Player("p1", "Karol", "AMARILLO", 4),
                new Player("p2", "Juan", "AZUL", 21)
        ));
        game.start();
    }

    // ─── createGame ───────────────────────────────────────────────────────────

    @Test
    void createGame_shouldReturn200WithGameId() {
        when(createGameUseCase.execute(anyList())).thenReturn(game);

        CreateGameRequest request = buildRequest("p1", "Karol", "p2", "Juan");
        ResponseEntity<Map<String, String>> response = controller.createGame(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("game-1", response.getBody().get("gameId"));
    }

    @Test
    void createGame_shouldCallUseCaseWithCorrectInputs() {
        when(createGameUseCase.execute(anyList())).thenReturn(game);

        CreateGameRequest request = buildRequest("p1", "Karol", "p2", "Juan");
        controller.createGame(request);

        verify(createGameUseCase, times(1)).execute(anyList());
    }

    @Test
    void createGame_shouldPropagateExceptionFromUseCase() {
        when(createGameUseCase.execute(anyList()))
                .thenThrow(new IllegalArgumentException("El juego requiere entre 1 y 4 jugadores"));

        CreateGameRequest request = buildRequest("p1", "A", "p2", "B");
        assertThrows(IllegalArgumentException.class, () -> controller.createGame(request));
    }

    // ─── getGame ──────────────────────────────────────────────────────────────

    @Test
    void getGame_shouldReturn200WithGameResponse() {
        when(gameRepository.findById("game-1")).thenReturn(game);

        ResponseEntity<GameResponse> response = controller.getGame("game-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("game-1", response.getBody().getGameId());
    }

    @Test
    void getGame_shouldThrowWhenGameNotFound() {
        when(gameRepository.findById("bad-id"))
                .thenThrow(new IllegalArgumentException("Juego no encontrado: bad-id"));

        assertThrows(IllegalArgumentException.class, () -> controller.getGame("bad-id"));
    }

    @Test
    void getGame_shouldReturnCorrectNumberOfPlayers() {
        when(gameRepository.findById("game-1")).thenReturn(game);

        ResponseEntity<GameResponse> response = controller.getGame("game-1");

        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getPlayers().size());
    }

    // ─── rollDice ─────────────────────────────────────────────────────────────

    @Test
    void rollDice_shouldReturn200WithGameResponse() {
        when(rollDiceUseCase.execute("game-1", "p1")).thenReturn(game);

        ResponseEntity<GameResponse> response = controller.rollDice("game-1", "p1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void rollDice_shouldCallUseCaseWithCorrectParameters() {
        when(rollDiceUseCase.execute("game-1", "p1")).thenReturn(game);

        controller.rollDice("game-1", "p1");

        verify(rollDiceUseCase, times(1)).execute("game-1", "p1");
    }

    @Test
    void rollDice_shouldPropagateExceptionFromUseCase() {
        when(rollDiceUseCase.execute("game-1", "p2"))
                .thenThrow(new IllegalStateException("No es tu turno"));

        assertThrows(IllegalStateException.class,
                () -> controller.rollDice("game-1", "p2"));
    }

    // ─── movePiece ───────────────────────────────────────────────────────────

    @Test
    void movePiece_shouldReturn200WithGameResponse() {
        when(movePieceUseCase.execute("game-1", "p1", "piece-0", 1)).thenReturn(game);

        ResponseEntity<GameResponse> response =
                controller.movePiece("game-1", "p1", "piece-0", 1);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void movePiece_shouldCallUseCaseWithCorrectParameters() {
        when(movePieceUseCase.execute("game-1", "p1", "piece-0", 2)).thenReturn(game);

        controller.movePiece("game-1", "p1", "piece-0", 2);

        verify(movePieceUseCase, times(1)).execute("game-1", "p1", "piece-0", 2);
    }

    @Test
    void movePiece_shouldPropagateExceptionFromUseCase() {
        when(movePieceUseCase.execute("game-1", "p1", "piece-0", 1))
                .thenThrow(new IllegalStateException("Debes lanzar el dado primero"));

        assertThrows(IllegalStateException.class,
                () -> controller.movePiece("game-1", "p1", "piece-0", 1));
    }

    // ─── handleDomainErrors ───────────────────────────────────────────────────

    @Test
    void handleDomainErrors_shouldReturn400ForIllegalStateException() {
        ResponseEntity<Map<String, String>> response =
                controller.handleDomainErrors(new IllegalStateException("Error de estado"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Error de estado", response.getBody().get("error"));
    }

    @Test
    void handleDomainErrors_shouldReturn400ForIllegalArgumentException() {
        ResponseEntity<Map<String, String>> response =
                controller.handleDomainErrors(new IllegalArgumentException("Argumento inválido"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Argumento inválido", response.getBody().get("error"));
    }

    // ─── GameResponse mapping ─────────────────────────────────────────────────

    @Test
    void gameResponse_shouldContainCorrectState() {
        when(gameRepository.findById("game-1")).thenReturn(game);

        ResponseEntity<GameResponse> response = controller.getGame("game-1");

        assertNotNull(response.getBody());
        assertEquals("IN_PROGRESS", response.getBody().getState());
    }

    @Test
    void gameResponse_shouldContainCurrentPlayerId() {
        when(gameRepository.findById("game-1")).thenReturn(game);

        ResponseEntity<GameResponse> response = controller.getGame("game-1");

        assertNotNull(response.getBody());
        assertEquals("p1", response.getBody().getCurrentPlayerId());
    }

    @Test
    void gameResponse_playersShouldHaveFourPiecesEach() {
        when(gameRepository.findById("game-1")).thenReturn(game);

        ResponseEntity<GameResponse> response = controller.getGame("game-1");

        assertNotNull(response.getBody());
        response.getBody().getPlayers().forEach(p ->
                assertEquals(4, p.getPieces().size()));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private CreateGameRequest buildRequest(
            String id1, String name1, String id2, String name2) {
        CreateGameRequest request = new CreateGameRequest();

        CreateGameRequest.PlayerInfo pi1 = new CreateGameRequest.PlayerInfo();
        pi1.setId(id1);
        pi1.setName(name1);

        CreateGameRequest.PlayerInfo pi2 = new CreateGameRequest.PlayerInfo();
        pi2.setId(id2);
        pi2.setName(name2);

        request.setPlayers(List.of(pi1, pi2));
        return request;
    }
}
