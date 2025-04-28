package com.leonid.malinov.backend.client;

import com.leonid.malinov.backend.dto.gpt.OpenRouterRequest;
import com.leonid.malinov.backend.dto.gpt.OpenRouterResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "openrouter",
        url  = "${openrouter.url}"
)
public interface OpenRouterClient {
    @PostMapping
    OpenRouterResponse chat(
            @RequestHeader("Authorization") String authorization,
            @RequestBody OpenRouterRequest body
    );
}
