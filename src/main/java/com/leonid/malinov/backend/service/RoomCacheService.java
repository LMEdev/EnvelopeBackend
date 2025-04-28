package com.leonid.malinov.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leonid.malinov.backend.dto.room.Room;
import com.leonid.malinov.backend.dto.room.RoomCache;
import com.leonid.malinov.backend.model.RoomStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class RoomCacheService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RoomCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private String key(String roomId) {
        return "room:" + roomId;
    }

    public void save(Room room) {
        try {
            RoomCache dto = new RoomCache(
                    room.getId(),
                    room.getStatus(),
                    room.getCapacity(),
                    List.copyOf(room.getPlayers())
            );
            String json = mapper.writeValueAsString(dto);
            redis.opsForValue().set(key(room.getId()), json);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации RoomCache", e);
        }
    }

    public boolean delete(String roomId) {
        return Boolean.TRUE.equals(redis.delete(key(roomId)));
    }

    public List<RoomCache> findAllOpened() {
        Set<String> keys = Optional.ofNullable(redis.keys("room:*")).orElse(Collections.emptySet());
        if (keys.isEmpty()) return List.of();

        List<String> jsons = redis.opsForValue().multiGet(keys);
        return jsons.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return mapper.readValue(s, RoomCache.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка парсинга RoomCache из JSON", e);
                    }
                })
                .filter(dto -> dto.getStatus() != RoomStatus.CLOSED)
                .collect(Collectors.toList());
    }
}