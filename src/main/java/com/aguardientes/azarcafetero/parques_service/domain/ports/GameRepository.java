package com.aguardientes.azarcafetero.parques_service.domain.ports;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;

public interface GameRepository {
    Game findById(String id);
    void save(Game game);
}
