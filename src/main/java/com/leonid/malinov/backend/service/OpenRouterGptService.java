package com.leonid.malinov.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leonid.malinov.backend.client.OpenRouterClient;
import com.leonid.malinov.backend.dto.gpt.OpenRouterMessage;
import com.leonid.malinov.backend.dto.gpt.OpenRouterRequest;
import com.leonid.malinov.backend.dto.gpt.OpenRouterResponse;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenRouterGptService {

    private final OpenRouterClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openrouter.token}")
    private String token;

    @Value("${openrouter.model}")
    private String model;

    public PlayerRoundResult evaluate(String prompt, String userAnswer) {
        String content = String.format("""
        {
            "task": "Ты — участник юмористической игры \\"Выживи как сможешь\\". 
            Игроки моделируют необычные, абсурдные или опасные ситуации, а затем описывают, как они бы выжили. 
            Твоя задача — придумать смешной, логичный, но неожиданный исход, основываясь на предложенном способе выживания. 
            ❗️Важно: 
            - Отвечай строго в формате JSON из двух строк: 
                1. \\"status\\": \\"Выжил\\" или \\"Не выжил\\" 
                2. \\"comment\\": подробный и смешной комментарий (3–5 предложений), логично объясняющий, почему игрок выжил или нет. 
            - Комментарий должен быть с юмором: абсурд, ирония, черный юмор, гэги, случайные события — приветствуются. 
            - Не добавляй ничего, кроме JSON. Никаких пояснений, вступлений, markdown и т.д. 
            Пример структуры ответа: { \\"status\\": \\"Не выжил\\", \\"comment\\": \\" }. 

            "Ситуация": "%s",
            "Ответ игрока": "%s"
        }
        """, escape(prompt), escape(userAnswer));

        OpenRouterRequest req = new OpenRouterRequest();
        req.setModel(model);

        List<OpenRouterMessage> msgs = Collections.singletonList(
                new OpenRouterMessage("user", content)
        );
        req.setMessages(msgs);

        OpenRouterResponse resp = client.chat("Bearer " + token, req);
        String raw = resp.getChoices().get(0).getMessage().getContent();

        try {
            JsonNode node = mapper.readTree(raw);
            String status  = node.path("status").asText();
            String comment = node.path("comment").asText();
            return new PlayerRoundResult(userAnswer, status, comment);
        } catch (Exception e) {
            return new PlayerRoundResult(
                    userAnswer,
                    "Неизвестно",
                    "Ошибка разбора ответа от GPT"
            );
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
