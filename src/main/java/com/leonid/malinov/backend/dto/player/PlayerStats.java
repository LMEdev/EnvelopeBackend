package com.leonid.malinov.backend.dto.player;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@NoArgsConstructor
@Schema(description="Статистика игрока по раундам")
public class PlayerStats {

    @Schema(description="Сколько раз выжил", example="3")
    private int survivedCount = 0;

    @Schema(description="Сколько раз не выжил", example="1")
    private int diedCount = 0;

    public void record(boolean survived) {
        if (survived) this.survivedCount++;
        else this.diedCount++;
    }
}
