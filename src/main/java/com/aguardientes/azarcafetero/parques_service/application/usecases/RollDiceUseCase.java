package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.events.DiceRolledEvent;
import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;

public class RollDiceUseCase {

    private final GameRepository repository;
    private final EventPublisher eventPublisher;

    public RollDiceUseCase(GameRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public Game execute(String gameId, String playerId) {
        Game game = repository.findById(gameId);
        game.rollDice(playerId);
        repository.save(game);
        eventPublisher.publish(new DiceRolledEvent(gameId, playerId, game.getDie1(), game.getDie2()));
        return game;
    }
}
