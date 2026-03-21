package com.aguardientes.azarcafetero.parques_service.infrastructure;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGameRepository implements GameRepository {

    private final Map<String, Game> store = new ConcurrentHashMap<>();

    @Override
    public Game findById(String id) {
        Game game = store.get(id);
        if (game == null) {
            throw new IllegalArgumentException("Juego no encontrado: " + id);
        }
        return game;
    }

    @Override
    public void save(Game game) {
        store.put(game.getId(), game);
    }
}
