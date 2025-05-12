package com.leonid.malinov.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoomEventBroadcaster.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void broadcast(String roomId, String action) {
        String payload = roomId + " : " + action;
        sessions.removeIf(s -> !s.isOpen());
        for (WebSocketSession s : sessions) {
            try {
                s.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.warn("Failed to send room-event to session {}", s.getId(), e);
            }
        }
    }
}
