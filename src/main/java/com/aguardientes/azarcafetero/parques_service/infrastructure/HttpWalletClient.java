package com.aguardientes.azarcafetero.parques_service.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import java.util.Map;

public class HttpWalletClient {
    private static final Logger log = LoggerFactory.getLogger(HttpWalletClient.class);
    private final RestClient restClient;
    private final String apiKey;

    public HttpWalletClient(String lobbyUrl, String apiKey) {
        this.restClient = RestClient.builder().baseUrl(lobbyUrl).build();
        this.apiKey = apiKey;
    }

    public void placeBet(String userId, int amount) {
        post("/api/player/bet", userId, amount);
    }

    public void receiveWin(String userId, int amount) {
        post("/api/player/win", userId, amount);
    }

    public void registerLoss(String userId, int amount) {
        post("/api/player/loss", userId, amount);
    }

    private void post(String uri, String userId, int amount) {
        try {
            restClient.post().uri(uri)
                .header("X-Internal-Key", apiKey)
                .body(Map.of("userId", userId, "amount", new java.math.BigDecimal(amount)))
                .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("Wallet error {} userId={}: {}", uri, userId, e.getMessage());
        }
    }
}