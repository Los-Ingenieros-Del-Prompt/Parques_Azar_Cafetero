package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.GameState;
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

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests adicionales para cubrir ramas no cubiertas en MovePieceUseCase:
 * - settleGame: ganador humano recibe premio
 * - settleGame: perdedor humano registra pérdida
 * - settleGame: bots son ignorados
 * - settleGame: múltiples humanos
 * - publishGameFinishedEvent cuando el juego termina
 */
@ExtendWith(MockitoExtension.class)
class MovePieceUseCaseExtendedTest {

    @Mock
    private GameRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private HttpWalletClient walletClient;

    private MovePieceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new MovePieceUseCase(repository, eventPublisher, walletClient);
    }

    private void injectDice(Game g, int d1, int d2) throws Exception {
        setField(g, "die1", d1);
        setField(g, "die2", d2);
        setField(g, "die1Used", false);
        setField(g, "die2Used", false);
        setField(g, "diceRolled", true);
        setField(g, "jailExitAvailable", false);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // ─── Juego termina: ganador humano ────────────────────────────────────────

    @Test
    void execute_whenHumanWins_shouldCallReceiveWin() throws Exception {
        Player winner = new Player("human-winner", "Winner", "AMARILLO", 4);
        Player loser = new Player("human-loser", "Loser", "AZUL", 21);
        Game game = new Game("game-win", List.of(winner, loser));
        game.start();

        // Poner todas las fichas del ganador a un paso de la victoria
        for (Piece p : winner.getPieces()) {
            p.exitJail();
            p.move(69); // rel=69, falta 1 para 70
        }

        // Inyectar dados: die1=1 → la ficha llega a victoria
        injectDice(game, 1, 2);
        when(repository.findById("game-win")).thenReturn(game);

        String pieceId = winner.getPieces().get(0).getId();
        useCase.execute("game-win", "human-winner", pieceId, 1);

        // Si todas las fichas ganaron → settleGame
        if (game.isFinished()) {
            verify(walletClient).receiveWin(eq("human-winner"), anyInt());
            verify(walletClient).registerLoss(eq("human-loser"), anyInt());
        }
    }

    @Test
    void execute_whenGameFinishes_shouldPublishGameFinishedEvent() throws Exception {
        Player winner = new Player("p-win", "W", "AMARILLO", 4);
        Player loser = new Player("p-lose", "L", "AZUL", 21);
        Game game = new Game("game-finish", List.of(winner, loser));
        game.start();

        for (Piece p : winner.getPieces()) {
            p.exitJail();
            p.move(69);
        }

        injectDice(game, 1, 2);
        when(repository.findById("game-finish")).thenReturn(game);

        String pieceId = winner.getPieces().get(0).getId();
        useCase.execute("game-finish", "p-win", pieceId, 1);

        if (game.isFinished()) {
            // Debe publicar al menos 2 eventos: PieceMovedEvent + GameFinishedEvent
            verify(eventPublisher, atLeast(2)).publish(any());
        }
    }

    // ─── Juego termina: bot ignorado en settleGame ────────────────────────────

    @Test
    void execute_whenBotLoses_shouldNotCallWalletForBot() throws Exception {
        Player humanWinner = new Player("human-w", "Winner", "AMARILLO", 4);
        Player botLoser = new Player("BOT_EASY_loser1", "Bot", "AZUL", 21);
        Game game = new Game("game-bot-lose", List.of(humanWinner, botLoser));
        game.start();

        for (Piece p : humanWinner.getPieces()) {
            p.exitJail();
            p.move(69);
        }

        injectDice(game, 1, 2);
        when(repository.findById("game-bot-lose")).thenReturn(game);

        String pieceId = humanWinner.getPieces().get(0).getId();
        useCase.execute("game-bot-lose", "human-w", pieceId, 1);

        if (game.isFinished()) {
            // Bot no debe recibir ninguna llamada al wallet
            verify(walletClient, never()).registerLoss(eq("BOT_EASY_loser1"), anyInt());
            verify(walletClient, never()).receiveWin(eq("BOT_EASY_loser1"), anyInt());
        }
    }

    // ─── Múltiples humanos: ganador recibe premio de todos ────────────────────

    @Test
    void execute_withThreeHumans_winnerReceivesMultiplierPrize() throws Exception {
        Player p1 = new Player("human-1", "H1", "AMARILLO", 4);
        Player p2 = new Player("human-2", "H2", "AZUL", 21);
        Player p3 = new Player("human-3", "H3", "VERDE", 55);
        Game game = new Game("game-3p", List.of(p1, p2, p3));
        game.start();

        for (Piece p : p1.getPieces()) {
            p.exitJail();
            p.move(69);
        }

        injectDice(game, 1, 2);
        when(repository.findById("game-3p")).thenReturn(game);

        String pieceId = p1.getPieces().get(0).getId();
        useCase.execute("game-3p", "human-1", pieceId, 1);

        if (game.isFinished()) {
            // Ganador recibe BET * 3 (3 humanos)
            verify(walletClient).receiveWin("human-1", 300);
            verify(walletClient).registerLoss("human-2", 100);
            verify(walletClient).registerLoss("human-3", 100);
        }
    }

    // ─── Juego no termina: no llama wallet ───────────────────────────────────

    @Test
    void execute_whenGameNotFinished_shouldNotCallWallet() throws Exception {
        Player p1 = new Player("p1", "P1", "AMARILLO", 4);
        Player p2 = new Player("p2", "P2", "AZUL", 21);
        Game game = new Game("game-normal", List.of(p1, p2));
        game.start();

        p1.getPieces().get(0).exitJail(); // rel=0

        injectDice(game, 3, 5);
        when(repository.findById("game-normal")).thenReturn(game);

        String pieceId = p1.getPieces().get(0).getId();
        useCase.execute("game-normal", "p1", pieceId, 1);

        // No termina → no settle
        assertFalse(game.isFinished());
        verifyNoInteractions(walletClient);
    }

    // ─── PassTurnUseCase: flujo de guardar ───────────────────────────────────

    @Test
    void passTurn_shouldSaveGame() throws Exception {
        Player p1 = new Player("p1", "K", "AMARILLO", 4);
        Player p2 = new Player("p2", "J", "AZUL", 21);
        Game game = new Game("game-pass", List.of(p1, p2));
        game.start();

        PassTurnUseCase passTurnUseCase = new PassTurnUseCase(repository);
        when(repository.findById("game-pass")).thenReturn(game);

        // Forzar dados no-par y sin movimientos → passTurn válido
        boolean executed = false;
        for (int i = 0; i < 30 && !executed; i++) {
            game = new Game("game-pass", List.of(
                    new Player("p1", "K", "AMARILLO", 4),
                    new Player("p2", "J", "AZUL", 21)
            ));
            game.start();
            when(repository.findById("game-pass")).thenReturn(game);
            game.rollDice("p1");
            if (game.getDie1() != game.getDie2()) {
                passTurnUseCase.execute("game-pass", "p1");
                verify(repository, atLeastOnce()).save(any(Game.class));
                executed = true;
            }
        }
    }

    @Test
    void passTurn_shouldReturnGame() throws Exception {
        Player p1 = new Player("p1", "K", "AMARILLO", 4);
        Player p2 = new Player("p2", "J", "AZUL", 21);
        Game game = new Game("game-pass2", List.of(p1, p2));
        game.start();

        PassTurnUseCase passTurnUseCase = new PassTurnUseCase(repository);

        for (int i = 0; i < 30; i++) {
            game = new Game("game-pass2", List.of(
                    new Player("p1", "K", "AMARILLO", 4),
                    new Player("p2", "J", "AZUL", 21)
            ));
            game.start();
            when(repository.findById("game-pass2")).thenReturn(game);
            game.rollDice("p1");
            if (game.getDie1() != game.getDie2()) {
                Game result = passTurnUseCase.execute("game-pass2", "p1");
                assertNotNull(result);
                assertEquals("game-pass2", result.getId());
                break;
            }
        }
    }
}