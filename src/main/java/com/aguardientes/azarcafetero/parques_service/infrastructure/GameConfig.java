package com.aguardientes.azarcafetero.parques_service.infrastructure;

import com.aguardientes.azarcafetero.parques_service.application.usecases.CreateGameUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.MovePieceUseCase;
import com.aguardientes.azarcafetero.parques_service.application.usecases.RollDiceUseCase;
import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import com.aguardientes.azarcafetero.parques_service.domain.ports.GameRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfig {

    @Bean
    public GameRepository gameRepository() {
        return new InMemoryGameRepository();
    }

    @Bean
    public EventPublisher eventPublisher() {
        return new LogEventPublisher();
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
    public com.aguardientes.azarcafetero.parques_service.application.usecases.PassTurnUseCase passTurnUseCase(GameRepository gameRepository) {
        return new com.aguardientes.azarcafetero.parques_service.application.usecases.PassTurnUseCase(gameRepository);
    }
    @Bean
    public com.aguardientes.azarcafetero.parques_service.application.usecases.ExitJailUseCase exitJailUseCase(GameRepository gameRepository) {
        return new com.aguardientes.azarcafetero.parques_service.application.usecases.ExitJailUseCase(gameRepository);
    }
}
