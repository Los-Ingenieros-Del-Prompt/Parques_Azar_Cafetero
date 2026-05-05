package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateGameUseCase {

    private static final int[] EXIT_POSITIONS = {0, 17, 32, 49};
    private static final String[] COLORS = {"AMARILLO", "AZUL", "VERDE", "ROJO"};

    private final GameRepository repository;

    public CreateGameUseCase(GameRepository repository) {
        this.repository = repository;
    }

    public Game execute(String gameId, List<PlayerInput> playerInputs) {
        if (playerInputs.size() < 1 || playerInputs.size() > 4) {
            throw new IllegalArgumentException("El juego requiere entre 1 y 4 jugadores");
        }

        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerInputs.size(); i++) {
            PlayerInput input = playerInputs.get(i);
            players.add(new Player(input.id(), input.name(), COLORS[i], EXIT_POSITIONS[i]));
        }

        Game game = new Game(gameId, players);  // ← usa el gameId recibido
        repository.save(game);
        return game;
    }

    // Mantener el método original para compatibilidad con REST
    public Game execute(List<PlayerInput> playerInputs) {
        return execute(UUID.randomUUID().toString(), playerInputs);
    }

    public record PlayerInput(String id, String name) {}
}
