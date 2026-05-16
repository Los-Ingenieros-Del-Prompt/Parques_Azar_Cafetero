package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateGameUseCaseTest {

    @Mock
    private GameRepository repository;

    @InjectMocks
    private CreateGameUseCase useCase;

    private List<CreateGameUseCase.PlayerInput> twoPlayers;
    private List<CreateGameUseCase.PlayerInput> fourPlayers;

    @BeforeEach
    void setUp() {
        twoPlayers = List.of(
                new CreateGameUseCase.PlayerInput("p1", "Karol"),
                new CreateGameUseCase.PlayerInput("p2", "Juan")
        );
        fourPlayers = List.of(
                new CreateGameUseCase.PlayerInput("p1", "Karol"),
                new CreateGameUseCase.PlayerInput("p2", "Juan"),
                new CreateGameUseCase.PlayerInput("p3", "Ana"),
                new CreateGameUseCase.PlayerInput("p4", "Luis")
        );
    }

    // ─── execute con gameId ───────────────────────────────────────────────────

    @Test
    void execute_shouldCreateGameWithGivenId() {
        Game game = useCase.execute("game-abc", twoPlayers);
        assertEquals("game-abc", game.getId());
    }

    @Test
    void execute_shouldSaveGameToRepository() {
        useCase.execute("game-abc", twoPlayers);
        verify(repository, times(1)).save(any(Game.class));
    }

    @Test
    void execute_shouldCreateGameWithCorrectNumberOfPlayers() {
        Game game = useCase.execute("game-abc", twoPlayers);
        assertEquals(2, game.getPlayers().size());
    }

    @Test
    void execute_shouldAssignColorsInOrder() {
        Game game = useCase.execute("game-abc", twoPlayers);
        assertEquals("AMARILLO", game.getPlayers().get(0).getColor());
        assertEquals("AZUL", game.getPlayers().get(1).getColor());
    }

    @Test
    void execute_shouldAssignExitPositionsInOrder() {
        Game game = useCase.execute("game-abc", twoPlayers);
        assertEquals(4, game.getPlayers().get(0).getExitPosition());
        assertEquals(21, game.getPlayers().get(1).getExitPosition());
    }

    @Test
    void execute_shouldCreateGameWithFourPlayers() {
        Game game = useCase.execute("game-xyz", fourPlayers);
        assertEquals(4, game.getPlayers().size());
    }

    @Test
    void execute_shouldThrowWhenZeroPlayers() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("g", List.of()));
    }

    @Test
    void execute_shouldThrowWhenMoreThanFourPlayers() {
        List<CreateGameUseCase.PlayerInput> fivePlayers = List.of(
                new CreateGameUseCase.PlayerInput("p1", "A"),
                new CreateGameUseCase.PlayerInput("p2", "B"),
                new CreateGameUseCase.PlayerInput("p3", "C"),
                new CreateGameUseCase.PlayerInput("p4", "D"),
                new CreateGameUseCase.PlayerInput("p5", "E")
        );
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("g", fivePlayers));
    }

    // ─── execute sin gameId (genera UUID) ────────────────────────────────────

    @Test
    void execute_withoutGameId_shouldGenerateNonNullId() {
        Game game = useCase.execute(twoPlayers);
        assertNotNull(game.getId());
        assertFalse(game.getId().isBlank());
    }

    @Test
    void execute_withoutGameId_shouldGenerateUniqueIds() {
        Game game1 = useCase.execute(twoPlayers);
        Game game2 = useCase.execute(twoPlayers);
        assertNotEquals(game1.getId(), game2.getId());
    }

    @Test
    void execute_withoutGameId_shouldSaveGame() {
        useCase.execute(twoPlayers);
        verify(repository, atLeastOnce()).save(any(Game.class));
    }

    // ─── PlayerInput record ───────────────────────────────────────────────────

    @Test
    void playerInput_shouldHoldCorrectValues() {
        CreateGameUseCase.PlayerInput input =
                new CreateGameUseCase.PlayerInput("id-1", "TestName");
        assertEquals("id-1", input.id());
        assertEquals("TestName", input.name());
    }
}
