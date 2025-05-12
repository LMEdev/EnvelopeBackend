package com.leonid.malinov.backend.config;

import com.leonid.malinov.backend.dto.player.Player;
import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.dto.room.Room;
import com.leonid.malinov.backend.model.RoomStatus;
import com.leonid.malinov.backend.service.RoomEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import com.leonid.malinov.backend.service.RoomManagerService;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomManagerService manager;
    private final RoomEventBroadcaster broadcaster;
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    public WebSocketConfig(RoomManagerService manager,
                           RoomEventBroadcaster broadcaster) {
        this.manager     = manager;
        this.broadcaster = broadcaster;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(new GameSocketHandler(), "/ws/game")
                .setAllowedOrigins("*");
        registry
                .addHandler(new MonitorSocketHandler(), "/ws/rooms")
                .setAllowedOrigins("*");
    }

    /**
     * WS для мониторинга всех комнат (admins/ui with overview)
     */
    private class MonitorSocketHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            broadcaster.register(session);
        }
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            broadcaster.unregister(session);
        }
    }

    private class GameSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            URI uri = session.getUri();
            String roomId = null, userId = null;
            for (String p : Objects.requireNonNull(uri.getQuery()).split("&")) {
                String[] kv = p.split("=", 2);
                if ("roomId".equals(kv[0])) roomId = kv[1];
                if ("userId".equals(kv[0])) userId = kv[1];
            }
            if (roomId == null || userId == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            Room room = manager.getRoom(roomId);
            if (room == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            session.getAttributes().put("roomId", roomId);
            session.getAttributes().put("userId", userId);

            Player player = room.getPlayerById(userId);
            if (player == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            room.registerSession(session, player);
            manager.updateRoom(room);
            broadcaster.broadcast(roomId, "PLAYER_JOINED (" + player.getName() + ")");
            broadcastStatus(room);

            if (room.getStatus() == RoomStatus.MAIN_PLAYER_THINKING) {
                sendPromptRequests(room);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage msg) throws Exception {
            String roomId = (String) session.getAttributes().get("roomId");
            String userId = (String) session.getAttributes().get("userId");
            Room room    = manager.getRoom(roomId);
            Player sender= room.getPlayerById(userId);

            // 1) Admin задаёт тему
            if (room.getStatus() == RoomStatus.MAIN_PLAYER_THINKING && sender.isAdmin()) {
                room.setCurrentPrompt(msg.getPayload());
                room.setStatus(RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT);
                manager.updateRoom(room);
                broadcaster.broadcast(roomId, "PROMPT_SET (" + sender.getName() + ")");
                for (WebSocketSession s : room.getSessions()) {
                    if (!s.isOpen()) continue;
                    s.sendMessage(new TextMessage("[SYSTEM]: Ситуация: " + room.getCurrentPrompt()));
                }

                // 2) Игроки отвечают
            } else if (room.getStatus() == RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT) {
                room.saveAnswer(userId, msg.getPayload());
                manager.updateRoom(room);
                session.sendMessage(new TextMessage("[SYSTEM]: Ответ сохранён"));
                broadcaster.broadcast(roomId, "ANSWER_SUBMITTED (" + sender.getName() + ")");

                if (room.getAnswers().size() == room.getPlayers().size()) {
                    manager.evaluateAnswersWithGpt(roomId);
                    broadcaster.broadcast(roomId, "ANSWERS_EVALUATED");
                    for (WebSocketSession s : room.getSessions()) {
                        if (!s.isOpen()) continue;
                        Player p = room.getSessionUserMap().get(s);
                        PlayerRoundResult rr = room.getRoundResults().get(p.getId());
                        s.sendMessage(new TextMessage(
                                "[RESULT]: " + p.getName() + " → " + rr.getResult()
                                        + "\nGPT: " + rr.getGptAnswer()
                        ));
                    }
                    broadcastAllPlayerStats(room);
                    room.setStatus(RoomStatus.GAME_DONE);
                    manager.updateRoom(room);
                    broadcaster.broadcast(roomId, "ROUND_COMPLETED");
                    broadcastStatus(room);

                    WebSocketSession adminSess = room.getAdminSession();
                    if (adminSess != null && adminSess.isOpen()) {
                        adminSess.sendMessage(new TextMessage("[SYSTEM]: Вы хотите продолжить? [YES/NO]"));
                    }
                }

                // 3) Admin решает, продолжать или нет
            } else if (room.getStatus() == RoomStatus.GAME_DONE && sender.isAdmin()) {
                String choice = msg.getPayload().trim();
                if ("YES".equalsIgnoreCase(choice)) {
                    manager.continueGame(roomId);
                    broadcaster.broadcast(roomId, "CONTINUED");
                    Room updated = manager.getRoom(roomId);
                    broadcastStatus(updated);
                    sendPromptRequests(updated);
                } else {
                    room.setStatus(RoomStatus.CLOSED);
                    manager.updateRoom(room);
                    broadcaster.broadcast(roomId, "CLOSED");
                    broadcastStatus(room);
                    manager.closeGame(roomId);
                }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            // **никаких** unregisterSession / broadcast PLAYER_LEFT
            log.info("WS closed, session {}", session.getId());
        }

        private void sendPromptRequests(Room room) throws IOException {
            for (WebSocketSession s : room.getSessions()) {
                if (!s.isOpen()) continue;
                if (s.equals(room.getAdminSession())) {
                    s.sendMessage(new TextMessage("[SYSTEM]: Введите ситуацию"));
                } else {
                    s.sendMessage(new TextMessage("[SYSTEM]: Главный игрок вводит тему"));
                }
            }
        }

        private void broadcastStatus(Room room) {
            room.getSessions().forEach(s -> {
                if (!s.isOpen()) return;
                try {
                    s.sendMessage(new TextMessage("[SYSTEM]: Статус — " + room.getStatus()));
                } catch (IOException e) {
                    log.error("broadcastStatus error", e);
                }
            });
        }

        private void broadcastAllPlayerStats(Room room) {
            StringBuilder sb = new StringBuilder();
            room.getPlayers().forEach(p -> {
                PlayerStats ps = room.getStats().getOrDefault(p.getId(), new PlayerStats());
                sb.append(p.getName())
                        .append(": выжил ").append(ps.getSurvivedCount())
                        .append(", не выжил ").append(ps.getDiedCount())
                        .append(";\n");
            });
            String all = sb.toString().trim();
            room.getSessions().forEach(s -> {
                if (!s.isOpen()) return;
                try {
                    s.sendMessage(new TextMessage("[ALL_STATS]\n" + all));
                } catch (IOException e) {
                    log.error("broadcastAllPlayerStats error", e);
                }
            });
        }
    }
}

