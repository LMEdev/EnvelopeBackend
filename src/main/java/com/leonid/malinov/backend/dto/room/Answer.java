package com.leonid.malinov.backend.dto.room;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Ответ игрока на текущую тему")
public class Answer {
    @Schema(description = "ID игрока", example = "123e4567-e89b-12d3-a456-426614174000")
    private String userId;

    @Schema(description = "Текст ответа", example = "I would build a fortress")
    private String answer;
}
