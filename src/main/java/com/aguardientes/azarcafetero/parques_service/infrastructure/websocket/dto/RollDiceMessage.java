package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RollDiceMessage {

    @JsonProperty("playerId")
    private String playerId;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
}
