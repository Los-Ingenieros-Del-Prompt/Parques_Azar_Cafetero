package com.aguardientes.azarcafetero.parques_service.infrastructure;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryGameRepositoryTest {

    private InMemoryGameRepository repository;
    private Game game;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGameRepository();
        game = new Game("game-1", List.of(
                new Player("p1", "Karol", "AMARILLO", 4),
                new Player("p2", "Juan", "AZUL", 21)
        ));
    }

    @Test
    void save_shouldPersistGame() {
        repository.save(game);
        Game found = repository.findById("game-1");
        assertNotNull(found);
        assertEquals("game-1", found.getId());
    }

    @Test
    void findById_shouldThrowWhenGameNotFound() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findById("nonexistent"));
    }

    @Test
    void findById_shouldReturnSameGameInstance() {
        repository.save(game);
        Game found = repository.findById("game-1");
        assertSame(game, found);
    }

    @Test
    void save_shouldOverwriteExistingGame() {
        repository.save(game);

        Game updated = new Game("game-1", List.of(
                new Player("p1", "Karol Updated", "AMARILLO", 4),
                new Player("p2", "Juan", "AZUL", 21)
        ));
        repository.save(updated);

        Game found = repository.findById("game-1");
        assertEquals("Karol Updated", found.getPlayers().get(0).getName());
    }

    @Test
    void save_shouldSupportMultipleGames() {
        Game game2 = new Game("game-2", List.of(
                new Player("p3", "Ana", "VERDE", 55),
                new Player("p4", "Luis", "ROJO", 38)
        ));

        repository.save(game);
        repository.save(game2);

        assertNotNull(repository.findById("game-1"));
        assertNotNull(repository.findById("game-2"));
    }

    @Test
    void findById_errorMessageShouldContainGameId() {
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> repository.findById("missing-id"));
        assertTrue(ex.getMessage().contains("missing-id"));
    }
}
