package com.leonid.malinov.backend.dto.gpt;

import lombok.Data;

import java.util.List;

@Data
public class OpenRouterRequest {
    private String model;
    private List<OpenRouterMessage> messages;
}