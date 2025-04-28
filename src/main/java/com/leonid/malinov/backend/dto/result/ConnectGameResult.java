package com.leonid.malinov.backend.dto.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Результат создания игры (комнаты)")
public class ConnectGameResult {
    @Schema(description = "Идентификатор комнаты",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String roomId;

    @Schema(description = "Сгенерированный ID создателя комнаты",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private String userId;

    @Schema(description = "Признак администратора (всегда false)",
            example = "false")
    private boolean isAdmin;
}