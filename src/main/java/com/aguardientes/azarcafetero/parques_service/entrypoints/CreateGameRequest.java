package com.aguardientes.azarcafetero.parques_service.entrypoints;

import java.util.List;

public class CreateGameRequest {

    private List<PlayerInfo> players;

    public List<PlayerInfo> getPlayers() { return players; }
    public void setPlayers(List<PlayerInfo> players) { this.players = players; }

    public static class PlayerInfo {
        private String id;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
