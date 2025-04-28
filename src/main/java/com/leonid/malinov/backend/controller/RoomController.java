package com.leonid.malinov.backend.controller;

import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.dto.room.Answer;
import com.leonid.malinov.backend.dto.room.RoomInfo;
import com.leonid.malinov.backend.dto.room.Theme;
import com.leonid.malinov.backend.model.RoomStatus;
import com.leonid.malinov.backend.service.RoomManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Room", description = "API для работы с конкретной комнатой")
@RestController
@RequestMapping("/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomManagerService svc;

    @Operation(summary = "Получить текущий статус комнаты")
    @GetMapping("/{roomId}/status")
    public RoomStatus getStatus(@PathVariable String roomId) {
        return svc.getRoom(roomId).getStatus();
    }

    @Operation(summary = "Получить текущую тему (prompt)")
    @GetMapping("/{roomId}/theme")
    public Theme getTheme(@PathVariable String roomId) {
        return new Theme(svc.getCurrentPrompt(roomId));
    }

    @Operation(summary = "Получить полные результаты раунда (ответ игрока + результат GPT + подсказка GPT)")
    @GetMapping("/{roomId}/answers")
    public Map<String, PlayerRoundResult> getRoundResults(@PathVariable String roomId) {
        return svc.getRoundResults(roomId);
    }

    @Operation(summary = "Получить «сырые» ответы пользователей (только их ответы)")
    @GetMapping("/{roomId}/raw-answers")
    public Map<String, String> getRawAnswers(@PathVariable String roomId) {
        return svc.getRawAnswers(roomId);
    }

    @Operation(summary = "Получить статистику по раундам (выжил/не выжил)")
    @GetMapping("/{roomId}/stats")
    public Map<String, PlayerStats> getStats(@PathVariable String roomId) {
        return svc.getStats(roomId);
    }

    @Operation(summary = "Получить полную информацию по комнате")
    @GetMapping("/{roomId}/info")
    public RoomInfo getRoomInfo(@PathVariable String roomId) {
        return svc.getRoomInfo(roomId);
    }
}
