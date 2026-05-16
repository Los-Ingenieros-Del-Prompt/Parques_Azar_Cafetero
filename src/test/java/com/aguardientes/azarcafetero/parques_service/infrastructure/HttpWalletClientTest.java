package com.aguardientes.azarcafetero.parques_service.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests para HttpWalletClient.
 *
 * Cubre:
 *  - Constructor (líneas 13-16)
 *  - placeBet, receiveWin, registerLoss (líneas 18-28)
 *  - método privado post(): rama try (intento de petición) y catch (error de red) (líneas 30-39)
 *
 * Estrategia: usamos una URL no accesible (puerto 1 reservado, host inválido o
 * dirección que no responde). El RestClient intenta la petición, falla con
 * excepción y el bloque catch loguea el error sin propagarlo. Eso ejercita
 * todas las líneas del método private post().
 */
class HttpWalletClientTest {

    /** URL que garantiza un fallo rápido de conexión (puerto 1 → ECONNREFUSED). */
    private static final String UNREACHABLE_URL = "http://127.0.0.1:1";

    @Test
    void constructor_shouldNotThrowWithValidUrl() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "test-key");
        assertNotNull(client);
    }

    @Test
    void placeBet_shouldNotThrowWhenServerUnreachable() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "test-key");
        // El RestClient falla, pero el catch interno traga la excepción y solo loguea.
        assertDoesNotThrow(() -> client.placeBet("user-1", 100));
    }

    @Test
    void receiveWin_shouldNotThrowWhenServerUnreachable() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "test-key");
        assertDoesNotThrow(() -> client.receiveWin("winner-1", 300));
    }

    @Test
    void registerLoss_shouldNotThrowWhenServerUnreachable() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "test-key");
        assertDoesNotThrow(() -> client.registerLoss("loser-1", 100));
    }

    @Test
    void multipleCalls_shouldAllBeSafelyCaught() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "another-key");
        // Múltiples llamadas para asegurar que el patrón try/catch
        // funciona consistentemente y no deja estado roto.
        assertDoesNotThrow(() -> {
            client.placeBet("u1", 50);
            client.placeBet("u2", 75);
            client.receiveWin("u1", 200);
            client.registerLoss("u3", 100);
        });
    }

    @Test
    void placeBet_withZeroAmount_shouldNotThrow() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "key");
        assertDoesNotThrow(() -> client.placeBet("u", 0));
    }

    @Test
    void registerLoss_withNegativeAmount_shouldNotThrow() {
        HttpWalletClient client = new HttpWalletClient(UNREACHABLE_URL, "key");
        // Los validadores de monto no son responsabilidad de este cliente
        // (lo valida el backend lobby). Aquí solo verificamos robustez.
        assertDoesNotThrow(() -> client.registerLoss("u", -50));
    }
}
