package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.GameState;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RollDicePassTurnExitJailUseCaseTest {

    @Mock
    private GameRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    private RollDiceUseCase rollDiceUseCase;
    private PassTurnUseCase passTurnUseCase;
    private ExitJailUseCase exitJailUseCase;

    private Game game;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        rollDiceUseCase = new RollDiceUseCase(repository, eventPublisher);
        passTurnUseCase = new PassTurnUseCase(repository);
        exitJailUseCase = new ExitJailUseCase(repository);

        p1 = new Player("p1", "Karol", "AMARILLO", 4);
        p2 = new Player("p2", "Juan", "AZUL", 21);
        game = new Game("game-1", List.of(p1, p2));
        game.start();
    }

    // ─── RollDiceUseCase ─────────────────────────────────────────────────────

    @Test
    void rollDice_shouldReturnGameWithDiceRolled() {
        when(repository.findById("game-1")).thenReturn(game);

        Game result = rollDiceUseCase.execute("game-1", "p1");

        assertTrue(result.isDiceRolled());
    }

    @Test
    void rollDice_shouldSaveGameAfterRoll() {
        when(repository.findById("game-1")).thenReturn(game);

        rollDiceUseCase.execute("game-1", "p1");

        verify(repository, times(1)).save(game);
    }

    @Test
    void rollDice_shouldPublishDiceRolledEvent() {
        when(repository.findById("game-1")).thenReturn(game);

        rollDiceUseCase.execute("game-1", "p1");

        verify(eventPublisher, times(1)).publish(any());
    }

    
    @Test
    void rollDice_shouldThrowWhenGameNotFound() {
        when(repository.findById("nonexistent"))
                .thenThrow(new IllegalArgumentException("Juego no encontrado"));

        assertThrows(IllegalArgumentException.class,
                () -> rollDiceUseCase.execute("nonexistent", "p1"));
    }

    @Test
    void rollDice_shouldProduceDiceValuesBetween1And6() {
        when(repository.findById("game-1")).thenReturn(game);

        Game result = rollDiceUseCase.execute("game-1", "p1");

        assertTrue(result.getDie1() >= 1 && result.getDie1() <= 6);
        assertTrue(result.getDie2() >= 1 && result.getDie2() <= 6);
    }

    // ─── PassTurnUseCase ─────────────────────────────────────────────────────

    @Test
    void passTurn_shouldThrowWhenDiceNotRolled() {
        when(repository.findById("game-1")).thenReturn(game);

        assertThrows(IllegalStateException.class,
                () -> passTurnUseCase.execute("game-1", "p1"));
    }

    @Test
    void passTurn_shouldThrowWhenNotYourTurn() {
        when(repository.findById("game-1")).thenReturn(game);

        assertThrows(IllegalStateException.class,
                () -> passTurnUseCase.execute("game-1", "p2"));
    }

   
    // ─── ExitJailUseCase ─────────────────────────────────────────────────────

    @Test
    void exitJail_shouldThrowWhenDiceNotRolled() {
        when(repository.findById("game-1")).thenReturn(game);

        assertThrows(IllegalStateException.class,
                () -> exitJailUseCase.execute("game-1", "p1"));
    }

    @Test
    void exitJail_shouldThrowWhenNotYourTurn() {
        when(repository.findById("game-1")).thenReturn(game);

        assertThrows(IllegalStateException.class,
                () -> exitJailUseCase.execute("game-1", "p2"));
    }

    

 
}
