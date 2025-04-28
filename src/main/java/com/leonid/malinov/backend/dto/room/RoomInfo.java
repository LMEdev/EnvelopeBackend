package com.leonid.malinov.backend.dto.room;

import com.leonid.malinov.backend.dto.player.Player;
import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.model.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Schema(description = "Полная информация по комнате")
public class RoomInfo {
    @Schema(description = "ID комнаты")
    private String id;

    @Schema(description = "Статус комнаты")
    private RoomStatus status;

    @Schema(description = "Вместимость комнаты")
    private int capacity;

    @Schema(description = "Текущая тема")
    private String currentPrompt;

    @Schema(description = "Список игроков")
    private List<Player> players;

    @Schema(description = "Сырые ответы пользователей (userId → answer)")
    private Map<String, String> rawAnswers;

    @Schema(description = "Полные результаты раунда (userId → { userAnswer, result, gptAnswer })")
    private Map<String, PlayerRoundResult> roundResults;

    @Schema(description = "Статистика пользователей (userId → { survivedCount, diedCount })")
    private Map<String, PlayerStats> stats;
}
