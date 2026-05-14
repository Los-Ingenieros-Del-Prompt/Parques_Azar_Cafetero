package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;

public class PassTurnUseCase {

    private final GameRepository repository;

    public PassTurnUseCase(GameRepository repository) {
        this.repository = repository;
    }

    public Game execute(String gameId, String playerId) {
        Game game = repository.findById(gameId);
        game.passTurn(playerId);
        repository.save(game);
        return game;
    }
}
