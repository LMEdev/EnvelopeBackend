package com.leonid.malinov.backend.dto.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Результат одного раунда: ответ игрока, результат GPT и сам ответ GPT")
public class PlayerRoundResult {
    @Schema(description = "Что ввёл игрок", example = "I would hide in a bunker")
    private String userAnswer;

    @Schema(description = "Оценка GPT: выжил или нет", example = "выжил")
    private String result;

    @Schema(description = "Что предложил GPT", example = "The bunker is secure, you survive")
    private String gptAnswer;
}

