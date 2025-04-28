package com.leonid.malinov.backend.dto.gpt;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OpenRouterMessage {
    private String role;
    private String content;
}
