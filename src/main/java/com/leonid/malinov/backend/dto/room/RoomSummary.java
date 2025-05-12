package com.leonid.malinov.backend.dto.room;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "Сводка по комнате: список игроков и ник админа")
public class RoomSummary {

    @Schema(description = "Список ников всех игроков")
    private List<String> players;

    @Schema(description = "Ник администратора комнаты")
    private String admin;
}
