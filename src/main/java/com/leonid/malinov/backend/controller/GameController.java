package com.leonid.malinov.backend.controller;


import com.leonid.malinov.backend.dto.result.ConnectGameResult;
import com.leonid.malinov.backend.dto.result.CreateGameResult;
import com.leonid.malinov.backend.dto.room.RoomCache;
import com.leonid.malinov.backend.dto.room.RoomInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import com.leonid.malinov.backend.service.RoomManagerService;

import java.util.List;

@Tag(name = "Games", description = "API для создания, подключения и списка игровых комнат")
@RestController
@RequestMapping("/games")
@CrossOrigin(origins="*")
public class GameController {
    private final RoomManagerService manager;

    public GameController(RoomManagerService manager) {
        this.manager = manager;
    }

    @Operation(summary = "Создать новую игру (комнату)")
    @PostMapping("/create")
    public CreateGameResult createGame(@RequestParam String nick,
                                       @RequestParam int capacity) {
        return manager.createGame(nick, capacity);
    }

    @Operation(summary = "Подключиться к открытой игре (комнате)")
    @PostMapping("/connect")
    public ConnectGameResult connectToOpenedGame(@RequestParam String roomId,
                                                 @RequestParam String nick) {
        return manager.connectToOpenedGame(roomId, nick);
    }

    @Operation(summary = "Получить все открытые игры (комнаты)")
    @GetMapping("/open")
    public List<RoomCache> getAllOpenedGames() {
        @SuppressWarnings("unchecked")
        List<RoomCache> list = (List<RoomCache>) manager.getAllOpenedGames();
        return list;
    }

    @Operation(summary = "Принудительно стартовать игру (комнату) (даже если не все игроки пришли)")
    @PostMapping("/forceStart")
    public void startGame(@RequestParam String roomId) {
        manager.forceStartGame(roomId);
    }

    @Operation(summary = "Закрыть игру (комнату) и удалить из кэша")
    @PostMapping("/close")
    public void closeGame(@RequestParam String roomId) {
        manager.closeGame(roomId);
    }
}