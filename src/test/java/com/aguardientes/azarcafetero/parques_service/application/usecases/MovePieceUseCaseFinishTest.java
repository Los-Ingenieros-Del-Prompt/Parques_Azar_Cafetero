package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.events.GameFinishedEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests que ACTUALMENTE hacen que el juego termine (a diferencia de
 * MovePieceUseCaseExtendedTest cuyas aserciones de settleGame estaban dentro
 * de un if (game.isFinished()) que nunca se cumplía: con 4 fichas en rel=69
 * y mover una sola con die=1 solo lleva a 1 ficha a victoria, las otras 3
 * siguen en 69, por lo que player.hasFinished() = false).
 *
 * Aquí preparamos 3 fichas ya en victoria (rel=70) y la 4ta en rel=69.
 * Al moverla con die=1 → llega a 70 → todas en victoria → hasFinished=true
 * → state=FINISHED → settleGame() se ejecuta de verdad.
 *
 * Cubre líneas NC 136-138 de Game.java (state=FINISHED; winnerId; return)
 * y las líneas de settleGame en MovePieceUseCase (42-57).
 */
@ExtendWith(MockitoExtension.class)
class MovePieceUseCaseFinishTest {

    @Mock private GameRepository repository;
    @Mock private EventPublisher eventPublisher;
    @Mock private HttpWalletClient walletClient;

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

    /**
     * Lleva 3 fichas a victoria (rel=70) y deja una en rel=69 lista para ganar
     * con die=1.
     */
    private void prepareNearVictory(Player player) {
        List<Piece> pieces = player.getPieces();
        for (int i = 0; i < 3; i++) {
            pieces.get(i).exitJail();
            pieces.get(i).move(70); // a victoria
        }
        pieces.get(3).exitJail();
        pieces.get(3).move(69); // a 1 paso
    }

    // ─── Ganador humano: recibe el premio ─────────────────────────────────────

    @Test
    void execute_humanWinsAgainstHuman_winnerReceivesBetTimesTwo() throws Exception {
        Player winner = new Player("h-win", "Winner", "AMARILLO", 4);
        Player loser  = new Player("h-lose", "Loser",  "AZUL", 21);
        Game game = new Game("game-2h", List.of(winner, loser));
        game.start();

        prepareNearVictory(winner);
        injectDice(game, 1, 2);
        when(repository.findById("game-2h")).thenReturn(game);

        String pieceId = winner.getPieces().get(3).getId();
        Game result = useCase.execute("game-2h", "h-win", pieceId, 1);

        // El juego SÍ terminó
        assertTrue(result.isFinished());
        assertEquals("h-win", result.getWinnerId());
        assertEquals(GameState.FINISHED, result.getState());

        // settleGame se ejecutó: ganador recibe BET * 2 humanos = 200
        verify(walletClient).receiveWin("h-win", 200);
        verify(walletClient).registerLoss("h-lose", 100);
    }

    @Test
    void execute_whenGameFinishes_shouldPublishGameFinishedEvent() throws Exception {
        Player winner = new Player("w1", "W", "AMARILLO", 4);
        Player loser  = new Player("l1", "L", "AZUL", 21);
        Game game = new Game("game-evt", List.of(winner, loser));
        game.start();

        prepareNearVictory(winner);
        injectDice(game, 1, 2);
        when(repository.findById("game-evt")).thenReturn(game);

        useCase.execute("game-evt", "w1", winner.getPieces().get(3).getId(), 1);

        // PieceMovedEvent + GameFinishedEvent → al menos 2 publish
        verify(eventPublisher, org.mockito.Mockito.atLeast(2))
                .publish(any());
        // Verificamos específicamente que se publicó GameFinishedEvent
        verify(eventPublisher).publish(any(GameFinishedEvent.class));
    }

    // ─── Ganador humano contra bot: bot no se liquida ─────────────────────────

    @Test
    void execute_humanWinsAgainstBot_botIsIgnoredInSettle() throws Exception {
        Player winner = new Player("h-w", "Winner", "AMARILLO", 4);
        Player bot    = new Player("BOT_EASY_xxx", "Bot", "AZUL", 21);
        Game game = new Game("game-hvb", List.of(winner, bot));
        game.start();

        prepareNearVictory(winner);
        injectDice(game, 1, 2);
        when(repository.findById("game-hvb")).thenReturn(game);

        useCase.execute("game-hvb", "h-w", winner.getPieces().get(3).getId(), 1);

        assertTrue(game.isFinished());
        // Premio = BET * (humanos = 1) = 100
        verify(walletClient).receiveWin("h-w", 100);
        // El bot no debe ser tocado
        verify(walletClient, never()).registerLoss(eq("BOT_EASY_xxx"), anyInt());
        verify(walletClient, never()).receiveWin(eq("BOT_EASY_xxx"), anyInt());
    }

    // ─── Bot gana: nadie del lado bot recibe, los humanos pierden ────────────

    @Test
    void execute_botWinsAgainstHumans_humansLoseAndBotIsNotPaid() throws Exception {
        Player botWinner = new Player("BOT_HARD_zzz", "BotW", "AMARILLO", 4);
        Player human1    = new Player("h1", "H1", "AZUL", 21);
        Player human2    = new Player("h2", "H2", "VERDE", 55);
        Game game = new Game("game-bvh", List.of(botWinner, human1, human2));
        game.start();

        prepareNearVictory(botWinner);
        injectDice(game, 1, 2);
        when(repository.findById("game-bvh")).thenReturn(game);

        useCase.execute("game-bvh", "BOT_HARD_zzz", botWinner.getPieces().get(3).getId(), 1);

        assertTrue(game.isFinished());
        assertEquals("BOT_HARD_zzz", game.getWinnerId());

        // El bot ganador NO debe recibir nada (continue en settleGame)
        verify(walletClient, never()).receiveWin(eq("BOT_HARD_zzz"), anyInt());
        // Los humanos pierden
        verify(walletClient).registerLoss("h1", 100);
        verify(walletClient).registerLoss("h2", 100);
    }

    // ─── Tres humanos: el ganador recibe el premio multiplicado ──────────────

    @Test
    void execute_threeHumans_winnerGetsTriplePrize() throws Exception {
        Player winner = new Player("hA", "A", "AMARILLO", 4);
        Player p2     = new Player("hB", "B", "AZUL", 21);
        Player p3     = new Player("hC", "C", "VERDE", 55);
        Game game = new Game("game-3h", List.of(winner, p2, p3));
        game.start();

        prepareNearVictory(winner);
        injectDice(game, 1, 2);
        when(repository.findById("game-3h")).thenReturn(game);

        useCase.execute("game-3h", "hA", winner.getPieces().get(3).getId(), 1);

        assertTrue(game.isFinished());
        verify(walletClient).receiveWin("hA", 300); // 100 * 3 humanos
        verify(walletClient).registerLoss("hB", 100);
        verify(walletClient).registerLoss("hC", 100);
    }
}
