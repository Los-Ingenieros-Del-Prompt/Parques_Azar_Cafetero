package com.aguardientes.azarcafetero.parques_service.infrastructure;

import com.aguardientes.azarcafetero.parques_service.application.usecases.*;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import com.aguardientes.azarcafetero.parques_service.domain.service.ParquesBotDecisionService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfig {

    @Bean
    public GameRepository gameRepository() { return new InMemoryGameRepository(); }

    @Bean
    public EventPublisher eventPublisher() { return new LogEventPublisher(); }

    @Bean
    public ParquesBotDecisionService parquesBotDecisionService() {
        return new ParquesBotDecisionService();
    }

    @Bean
    public CreateGameUseCase createGameUseCase(GameRepository gameRepository) {
        return new CreateGameUseCase(gameRepository);
    }

    @Bean
    public RollDiceUseCase rollDiceUseCase(GameRepository gameRepository, EventPublisher eventPublisher) {
        return new RollDiceUseCase(gameRepository, eventPublisher);
    }

    @Bean
    public MovePieceUseCase movePieceUseCase(GameRepository gameRepository, EventPublisher eventPublisher) {
        return new MovePieceUseCase(gameRepository, eventPublisher);
    }

    @Bean
    public PassTurnUseCase passTurnUseCase(GameRepository gameRepository) {
        return new PassTurnUseCase(gameRepository);
    }

    @Bean
    public ExitJailUseCase exitJailUseCase(GameRepository gameRepository) {
        return new ExitJailUseCase(gameRepository);
    }
    @Value("${lobby.service.url}")
    private String lobbyUrl;

    @Value("${internal.api.key:}")
    private String internalApiKey;

    @Bean
    public HttpWalletClient httpWalletClient() {
        return new HttpWalletClient(lobbyUrl, internalApiKey);
    }
}