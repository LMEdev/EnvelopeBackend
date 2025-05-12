package com.leonid.malinov.backend.service;

import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.ConnectGameResult;
import com.leonid.malinov.backend.dto.result.CreateGameResult;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.dto.room.*;
import com.leonid.malinov.backend.dto.player.Player;
import org.springframework.stereotype.Service;

import com.leonid.malinov.backend.model.RoomStatus;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
public class RoomManagerService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomCacheService cache;
    private final OpenRouterGptService openRouterGptService;
    private final RoomEventBroadcaster broadcaster;

    public RoomManagerService(RoomCacheService cache,
                              OpenRouterGptService openRouterGptService,
                              RoomEventBroadcaster broadcaster) {
        this.cache = cache;
        this.openRouterGptService = openRouterGptService;
        this.broadcaster = broadcaster;
    }

    public CreateGameResult createGame(String nick, int capacity) {
        String roomId = UUID.randomUUID().toString();
        Room room = new Room(roomId, RoomStatus.WAITING_FOR_PLAYERS, capacity);

        String userId = UUID.randomUUID().toString();
        Player admin = new Player(userId, nick, true);
        room.getPlayers().add(admin);

        if (capacity == 1) {
            room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        }

        rooms.put(roomId, room);
        cache.save(room);
        broadcaster.broadcast(roomId, "CREATED");
        return new CreateGameResult(roomId, userId, true);
    }

    public ConnectGameResult connectToOpenedGame(String roomId, String nick) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        if (room.getPlayers().size() >= room.getCapacity()) {
            throw new IllegalStateException("Комната заполнена");
        }

        String userId = UUID.randomUUID().toString();
        Player player = new Player(userId, nick, false);
        room.getPlayers().add(player);

        if (room.getPlayers().size() >= room.getCapacity()) {
            room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        }

        cache.save(room);
        broadcaster.broadcast(roomId, "PLAYER_JOINED (" + nick + ")");
        return new ConnectGameResult(roomId, userId, false);
    }

    public void evaluateAnswersWithGpt(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));

        String prompt = room.getCurrentPrompt();
        for (var player : room.getPlayers()) {
            String uid = player.getId();
            String ua = room.getAnswers().getOrDefault(uid, "");
            PlayerRoundResult res = openRouterGptService.evaluate(prompt, ua);
            room.saveRoundResult(uid, res);
        }

        room.setStatus(RoomStatus.WAITING_FOR_ALL_ANSWERS_FROM_GPT);
        cache.save(room);
        broadcaster.broadcast(roomId, "ANSWERS_EVALUATED");
    }

    public void forceStartGame(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        cache.save(room);
        broadcaster.broadcast(roomId, "FORCE_STARTED");
    }

    public Map<String, PlayerStats> getStats(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        return Map.copyOf(room.getStats());
    }

    public void closeGame(String roomId) {
        cache.delete(roomId);
        rooms.remove(roomId);
        broadcaster.broadcast(roomId, "CLOSED");
    }

    public void continueGame(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));

        room.rotateAdmin();
        room.setCurrentPrompt("");
        room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        cache.save(room);
        broadcaster.broadcast(roomId, "CONTINUED");
    }

    public Map<String, RoomSummary> getRoomsSummary() {
        List<RoomCache> list = cache.findAllOpened();
        Map<String, RoomSummary> summary = new HashMap<>();
        for (RoomCache rc : list) {

            var players = rc.getPlayers().stream()
                    .map(Player::getName)
                    .toList();

            var admin = rc.getPlayers().stream()
                    .filter(Player::isAdmin)
                    .findFirst()
                    .map(Player::getName)
                    .orElse(null);
            summary.put(rc.getId(), new RoomSummary(players, admin));
        }
        return summary;
    }

    public void removeUserFromRoom(String roomId, String userId) throws IOException {
        Room room = getRoom(roomId);
        Player p = room.getPlayerById(userId);
        if (p == null) return;

        // 1) close WS session
        WebSocketSession toClose = room.getSessionUserMap().entrySet().stream()
                .filter(e -> e.getValue().getId().equals(userId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (toClose != null && toClose.isOpen()) {
            toClose.close(CloseStatus.NORMAL);
            room.unregisterSession(toClose);
        }

        // 2) remove from players
        room.getPlayers().remove(p);
        broadcaster.broadcast(roomId, "PLAYER_KICKED (" + p.getName() + ")");

        // 3) if was admin, rotate
        if (p.isAdmin() && !room.getPlayers().isEmpty()) {
            room.rotateAdmin();
            Player newAdmin = room.getPlayers().stream()
                    .filter(Player::isAdmin).findFirst().orElse(null);
            if (newAdmin != null) {
                broadcaster.broadcast(roomId, "ADMIN_CHANGED (" + newAdmin.getName() + ")");
                WebSocketSession adminSess = room.getSessionUserMap().entrySet().stream()
                        .filter(e -> e.getValue().getId().equals(newAdmin.getId()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if (adminSess != null && adminSess.isOpen()) {
                    adminSess.sendMessage(new TextMessage("[SYSTEM]: Вы — новый администратор"));
                }
            }
        }

        // 4) if waiting for answers and now answers == players, finish round
        if (room.getStatus() == RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT
                && room.getAnswers().size() == room.getPlayers().size()) {
            evaluateAnswersWithGpt(roomId);
            broadcaster.broadcast(roomId, "ANSWERS_EVALUATED");
            // ... broadcast [RESULT] and [ALL_STATS] as in WS handler ...
        }

        // 5) if empty, delete room
        if (room.getPlayers().isEmpty()) {
            closeGame(roomId);
            broadcaster.broadcast(roomId, "ROOM_DELETED");
            return;
        }

        // 6) save update
        updateRoom(room);
    }

    public void updateRoom(Room room) {
        cache.save(room);
    }

    public Map<String, String> getRawAnswers(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена: " + roomId));
        return Map.copyOf(room.getAnswers());
    }

    public RoomInfo getRoomInfo(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена: " + roomId));

        return new RoomInfo(
                room.getId(),
                room.getStatus(),
                room.getCapacity(),
                room.getCurrentPrompt(),
                List.copyOf(room.getPlayers()),
                Map.copyOf(room.getAnswers()),
                Map.copyOf(room.getRoundResults()),
                Map.copyOf(room.getStats())
        );
    }

    public List<RoomCache> getAllOpenedGames() {
        return cache.findAllOpened();
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Map<String, PlayerRoundResult> getRoundResults(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена: " + roomId));
        return Map.copyOf(room.getRoundResults());
    }

    public String getCurrentPrompt(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        return room.getCurrentPrompt();
    }
}

