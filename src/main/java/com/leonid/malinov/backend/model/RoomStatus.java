package com.leonid.malinov.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Статус комнаты")
public enum RoomStatus {
    @Schema(description = "Ожидание подключения игроков")
    WAITING_FOR_PLAYERS,

    @Schema(description = "Все игроки набраны, админ думает над ситуацией в комнате")
    MAIN_PLAYER_THINKING,

    @Schema(description = "Ожидание сообщений игроков после того как админ комнаты ввел тему")
    WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT,

    @Schema(description = "Ожидание пока гпт обработает каждый ответ пользователя. Переходим сюда когда все абсолютно ответят.")
    WAITING_FOR_ALL_ANSWERS_FROM_GPT,

    @Schema(description = "Ожидание пока гпт обработает каждый ответ пользователя. Переходим сюда когда все абсолютно ответят.")
    GAME_DONE,

    @Schema(description = "Комната закрыта")
    CLOSED
}