package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PassTurnMessage {

    @JsonProperty("playerId")
    private String playerId;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
}
