package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Piece;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import com.aguardientes.azarcafetero.parques_service.infrastructure.HttpWalletClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovePieceUseCaseTest {

    @Mock
    private GameRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private HttpWalletClient walletClient;

    private MovePieceUseCase useCase;

    private Game game;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        useCase = new MovePieceUseCase(repository, eventPublisher, walletClient);

        p1 = new Player("p1", "Karol", "AMARILLO", 4);
        p2 = new Player("p2", "Juan", "AZUL", 21);
        game = new Game("game-1", List.of(p1, p2));
        game.start();
    }

    // ─── Validaciones básicas ─────────────────────────────────────────────────

    @Test
    void execute_shouldThrowWhenDiceNotRolled() {
        when(repository.findById("game-1")).thenReturn(game);

        String pieceId = p1.getPieces().get(0).getId();
        assertThrows(IllegalStateException.class,
                () -> useCase.execute("game-1", "p1", pieceId, 1));
    }

    @Test
    void execute_shouldThrowWhenNotYourTurn() {
        when(repository.findById("game-1")).thenReturn(game);

        String pieceId = p2.getPieces().get(0).getId();
        assertThrows(IllegalStateException.class,
                () -> useCase.execute("game-1", "p2", pieceId, 1));
    }

    @Test
    void execute_shouldThrowWhenGameNotFound() {
        when(repository.findById("bad-id"))
                .thenThrow(new IllegalArgumentException("Juego no encontrado"));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("bad-id", "p1", "any-piece", 1));
    }

    // ─── Mover ficha activa ───────────────────────────────────────────────────

    @Test
    void execute_shouldSaveGameAfterMove() {
        // Ponemos ficha activa en p1
        p1.getPieces().get(0).exitJail();
        game.rollDice("p1");
        when(repository.findById("game-1")).thenReturn(game);

        Piece activePiece = p1.getPieces().get(0);
        int die1 = game.getDie1();

        if (activePiece.canMove(die1)) {
            useCase.execute("game-1", "p1", activePiece.getId(), 1);
            verify(repository, atLeastOnce()).save(any(Game.class));
        }
    }

    @Test
    void execute_shouldPublishPieceMovedEvent() {
        p1.getPieces().get(0).exitJail();
        game.rollDice("p1");
        when(repository.findById("game-1")).thenReturn(game);

        Piece activePiece = p1.getPieces().get(0);
        int die1 = game.getDie1();

        if (activePiece.canMove(die1)) {
            useCase.execute("game-1", "p1", activePiece.getId(), 1);
            verify(eventPublisher, atLeastOnce()).publish(any());
        }
    }

    @Test
    void execute_shouldReturnUpdatedGame() {
        p1.getPieces().get(0).exitJail();
        game.rollDice("p1");
        when(repository.findById("game-1")).thenReturn(game);

        Piece activePiece = p1.getPieces().get(0);
        int die1 = game.getDie1();

        if (activePiece.canMove(die1)) {
            Game result = useCase.execute("game-1", "p1", activePiece.getId(), 1);
            assertNotNull(result);
            assertEquals("game-1", result.getId());
        }
    }

    // ─── Dado 2 y suma ───────────────────────────────────────────────────────

    @Test
    void execute_shouldAllowMovingWithDie2() {
        p1.getPieces().get(0).exitJail();
        game.rollDice("p1");
        when(repository.findById("game-1")).thenReturn(game);

        Piece activePiece = p1.getPieces().get(0);
        int die2 = game.getDie2();

        if (activePiece.canMove(die2)) {
            Game result = useCase.execute("game-1", "p1", activePiece.getId(), 2);
            assertNotNull(result);
        }
    }

    @Test
    void execute_shouldAllowMovingWithSumOfBothDice() {
        p1.getPieces().get(0).exitJail();
        game.rollDice("p1");
        when(repository.findById("game-1")).thenReturn(game);

        Piece activePiece = p1.getPieces().get(0);
        int total = game.getDie1() + game.getDie2();

        if (activePiece.canMove(total)) {
            Game result = useCase.execute("game-1", "p1", activePiece.getId(), 3);
            assertNotNull(result);
        }
    }

    // ─── Juego finalizado ─────────────────────────────────────────────────────

    

    
}
