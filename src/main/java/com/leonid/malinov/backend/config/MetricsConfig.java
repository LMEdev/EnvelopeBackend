package com.leonid.malinov.backend.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private final MeterRegistry registry;

    public MetricsConfig(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void initCommonTags() {
        registry.config()
                .commonTags("application", "game-backend");
    }
}
