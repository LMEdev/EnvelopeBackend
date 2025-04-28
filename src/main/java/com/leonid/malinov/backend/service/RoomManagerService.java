package com.leonid.malinov.backend.service;

import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.ConnectGameResult;
import com.leonid.malinov.backend.dto.result.CreateGameResult;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.dto.room.Answer;
import com.leonid.malinov.backend.dto.room.Room;
import com.leonid.malinov.backend.dto.player.Player;
import com.leonid.malinov.backend.dto.room.RoomCache;
import com.leonid.malinov.backend.dto.room.RoomInfo;
import org.springframework.stereotype.Service;

import com.leonid.malinov.backend.model.RoomStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
public class RoomManagerService {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomCacheService cache;
    private final OpenRouterGptService openRouterGptService;

    public RoomManagerService(RoomCacheService cache, OpenRouterGptService openRouterGptService) {
        this.cache = cache;
        this.openRouterGptService = openRouterGptService;
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
        return new CreateGameResult(roomId, userId, true);
    }

    public ConnectGameResult connectToOpenedGame(String roomId, String nick) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        if (room.getPlayers().size() >= room.getCapacity())
            throw new IllegalStateException("Комната заполнена");

        String userId = UUID.randomUUID().toString();
        Player player = new Player(userId, nick, false);
        room.getPlayers().add(player);

        if (room.getPlayers().size() >= room.getCapacity()) {
            room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        }

        cache.save(room);
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
    }

    public Map<String, PlayerStats> getStats(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        return Map.copyOf(room.getStats());
    }


    public Map<String, String> getRawAnswers(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена: " + roomId));
        return Map.copyOf(room.getAnswers());
    }


    public void forceStartGame(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена"));
        room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        cache.save(room);
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

    public void closeGame(String roomId) {
        cache.delete(roomId);
        rooms.remove(roomId);
    }
    public void updateRoom(Room room) {
        cache.save(room);
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

    public void continueGame(String roomId) {
        Room room = Optional.ofNullable(rooms.get(roomId))
                .orElseThrow(() -> new NoSuchElementException("Комната не найдена: " + roomId));

        room.rotateAdmin();
        room.setCurrentPrompt("");
        room.setStatus(RoomStatus.MAIN_PLAYER_THINKING);
        cache.save(room);
    }
}

