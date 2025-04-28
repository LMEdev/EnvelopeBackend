package com.leonid.malinov.backend.dto.room;

import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.dto.player.Player;
import com.leonid.malinov.backend.model.RoomStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Data
@NoArgsConstructor
public class Room {
    private String id;
    private RoomStatus status;
    private int capacity;
    private String currentPrompt = "";

    private final List<Player> players = new CopyOnWriteArrayList<>();

    private final Map<WebSocketSession, Player> sessionUserMap = new ConcurrentHashMap<>();

    private final Map<String,String> answers = new ConcurrentHashMap<>();
    private final Map<String, PlayerRoundResult> roundResults = new ConcurrentHashMap<>();
    private final Map<String, PlayerStats> stats = new ConcurrentHashMap<>();

    public Room(String id, RoomStatus status, int capacity) {
        this.id = id;
        this.status = status;
        this.capacity = capacity;
    }

    public Player getPlayerById(String userId) {
        return players.stream()
                .filter(p -> p.getId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    public void registerSession(WebSocketSession session, Player player) {
        sessionUserMap.put(session, player);
    }
    public void unregisterSession(WebSocketSession session) {
        sessionUserMap.remove(session);
    }
    public Set<WebSocketSession> getSessions() {
        return sessionUserMap.keySet();
    }
    public WebSocketSession getAdminSession() {
        return sessionUserMap.entrySet().stream()
                .filter(e -> e.getValue().isAdmin())
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    public void setCurrentPrompt(String prompt) {
        this.currentPrompt = prompt;
        this.answers.clear();
        this.roundResults.clear();
    }

    public void saveAnswer(String userId, String answer) {
        this.answers.put(userId, answer);
    }

    public void saveRoundResult(String userId, PlayerRoundResult r) {

        roundResults.put(userId, r);

        stats.computeIfAbsent(userId, k -> new PlayerStats())
                .record("выжил".equals(r.getResult()));
    }

    public void rotateAdmin() {
        if (players.isEmpty()) return;
        int idx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).isAdmin()) {
                idx = i;
                players.get(i).setAdmin(false);
                break;
            }
        }
        int next = (idx + 1) % players.size();
        players.get(next).setAdmin(true);
    }
}