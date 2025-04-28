package com.leonid.malinov.backend.dto.player;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO пользователя (игрока)")
public class Player {
    @Schema(description = "Уникальный идентификатор игрока",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private String id;

    @Schema(description = "Ник игрока",
            example = "Alice")
    private String name;

    @Schema(description = "Флаг администратора комнаты",
            example = "true")
    private boolean isAdmin;
}
