package com.aguardientes.azarcafetero.parques_service.application.usecases;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateGameUseCase {

    private static final int[] EXIT_POSITIONS = {0, 17, 34, 51};
    private static final String[] COLORS = {"AMARILLO", "AZUL", "ROJO", "VERDE"};

    private final GameRepository repository;

    public CreateGameUseCase(GameRepository repository) {
        this.repository = repository;
    }

    public Game execute(List<PlayerInput> playerInputs) {
        if (playerInputs.size() < 2 || playerInputs.size() > 4) {
            throw new IllegalArgumentException("El juego requiere entre 2 y 4 jugadores");
        }

        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerInputs.size(); i++) {
            PlayerInput input = playerInputs.get(i);
            players.add(new Player(input.id(), input.name(), COLORS[i], EXIT_POSITIONS[i]));
        }

        Game game = new Game(UUID.randomUUID().toString(), players);
        repository.save(game);
        return game;
    }

    public record PlayerInput(String id, String name) {}
}
