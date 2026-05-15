package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.events.GameFinishedEvent;
import com.aguardientes.azarcafetero.parques_service.domain.events.PieceMovedEvent;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import com.aguardientes.azarcafetero.parques_service.infrastructure.HttpWalletClient;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDecisionService;
public class MovePieceUseCase {
    private static final int BET = 100;

    private final GameRepository repository;
    private final EventPublisher eventPublisher;
    private final HttpWalletClient walletClient;
    

    public MovePieceUseCase(GameRepository repository, 
                             EventPublisher eventPublisher,
                             HttpWalletClient walletClient) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.walletClient = walletClient;
    }

    public Game execute(String gameId, String playerId, String pieceId, int diceSelection) {
        Game game = repository.findById(gameId);
        game.movePiece(playerId, pieceId, diceSelection);
        repository.save(game);

        eventPublisher.publish(new PieceMovedEvent(gameId, playerId, pieceId, game.isFinished(), false));

        if (game.isFinished()) {
            eventPublisher.publish(new GameFinishedEvent(gameId, game.getWinnerId()));
            settleGame(game);  // ← liquidar aquí
        }

        return game;
    }

    private void settleGame(Game game) {
        String winnerId = game.getWinnerId();
        for (var player : game.getPlayers()) {
            String uid = player.getId();
            if (ParquesBotDecisionService.isBot(uid)) continue; // bots no tienen wallet

            if (uid.equals(winnerId)) {
                // El ganador recibe lo apostado por todos los humanos
                long humanCount = game.getPlayers().stream()
                    .filter(p -> !ParquesBotDecisionService.isBot(p.getId()))
                    .count();
                walletClient.receiveWin(uid, (int)(BET * humanCount));
            } else {
                walletClient.registerLoss(uid, BET);
            }
        }
    }
}
