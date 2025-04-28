package com.leonid.malinov.backend.service;

import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class GptService {
    private final Random rnd = new Random();

    public PlayerRoundResult evaluate(String prompt, String userAnswer, String playerName) {
        boolean survived = rnd.nextBoolean();
        String result = survived ? "выжил" : "не выжил";
        String gptAnswer = String.format(
                "GPT: Игрок %s %s. Prompt='%s'. Ваш ответ: '%s'",
                playerName, result, prompt, userAnswer
        );

        return new PlayerRoundResult(userAnswer, result, gptAnswer);
    }
}
