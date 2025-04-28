package com.leonid.malinov.backend.dto.room;

import com.leonid.malinov.backend.dto.player.Player;
import com.leonid.malinov.backend.model.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Метаданные комнаты из Redis")
public class RoomCache {
    @Schema(description = "ID комнаты", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Статус комнаты")
    private RoomStatus status;

    @Schema(description = "Вместимость комнаты", example = "4")
    private int capacity;

    @Schema(description = "Список игроков")
    private List<Player> players;
}