package com.aguardientes.azarcafetero.parques_service.infrastructure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogEventPublisherTest {

    @Test
    void publish_shouldNotThrowForAnyObject() {
        LogEventPublisher publisher = new LogEventPublisher();
        assertDoesNotThrow(() -> publisher.publish("string event"));
        assertDoesNotThrow(() -> publisher.publish(42));
        assertDoesNotThrow(() -> publisher.publish(null));
    }

    @Test
    void publish_shouldNotThrowForCustomObject() {
        LogEventPublisher publisher = new LogEventPublisher();
        Object customEvent = new Object() {
            @Override
            public String toString() {
                return "CustomEvent{data=test}";
            }
        };
        assertDoesNotThrow(() -> publisher.publish(customEvent));
    }
}
