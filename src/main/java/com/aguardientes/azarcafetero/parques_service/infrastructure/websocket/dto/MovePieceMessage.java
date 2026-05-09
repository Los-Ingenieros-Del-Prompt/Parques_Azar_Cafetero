package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MovePieceMessage {

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("pieceId")
    private String pieceId;

    @JsonProperty("diceSelection")
    private int diceSelection;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getPieceId() { return pieceId; }
    public void setPieceId(String pieceId) { this.pieceId = pieceId; }
    public int getDiceSelection() { return diceSelection; }
    public void setDiceSelection(int diceSelection) { this.diceSelection = diceSelection; }
}
