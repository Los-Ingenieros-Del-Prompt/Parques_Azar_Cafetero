package com.aguardientes.azarcafetero.parques_service.domain.ports;


public interface EventPublisher {
    void publish(Object event);
}