package com.aguardientes.azarcafetero.parques_service.infrastructure.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CreateGameMessage {

    @JsonProperty("players")
    private List<PlayerInfo> players;

    public List<PlayerInfo> getPlayers() { return players; }
    public void setPlayers(List<PlayerInfo> players) { this.players = players; }

    public static class PlayerInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
