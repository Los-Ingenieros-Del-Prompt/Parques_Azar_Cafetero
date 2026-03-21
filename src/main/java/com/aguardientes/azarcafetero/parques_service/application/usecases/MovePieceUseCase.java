package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.events.GameFinishedEvent;
import com.aguardientes.azarcafetero.parques_service.domain.events.PieceMovedEvent;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;

public class MovePieceUseCase {

    private final GameRepository repository;
    private final EventPublisher eventPublisher;

    public MovePieceUseCase(GameRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public Game execute(String gameId, String playerId, String pieceId) {
        Game game = repository.findById(gameId);
        game.movePiece(playerId, pieceId);
        repository.save(game);

        eventPublisher.publish(new PieceMovedEvent(gameId, playerId, pieceId, game.isFinished(), false));

        if (game.isFinished()) {
            eventPublisher.publish(new GameFinishedEvent(gameId, game.getWinnerId()));
        }

        return game;
    }
}
