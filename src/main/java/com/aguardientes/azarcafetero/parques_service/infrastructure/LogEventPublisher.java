package com.aguardientes.azarcafetero.parques_service.infrastructure;

import com.aguardientes.azarcafetero.parques_service.domain.ports.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LogEventPublisher.class);

    @Override
    public void publish(Object event) {
        log.info("[EVENTO] {}", event);
    }
}
