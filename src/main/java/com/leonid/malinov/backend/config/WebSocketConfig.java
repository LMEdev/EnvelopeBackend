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

    private final RoomManagerService roomManager;
    private final RoomEventBroadcaster broadcaster;
    private final Counter wsConnectCounter;
    private final Counter wsMessageCounter;
    private final Counter wsDisconnectCounter;
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    public WebSocketConfig(RoomManagerService roomManager,
                           RoomEventBroadcaster broadcaster,
                           MeterRegistry registry) {
        this.roomManager   = roomManager;
        this.broadcaster   = broadcaster;
        this.wsConnectCounter    = registry.counter("ws.connections.total");
        this.wsMessageCounter    = registry.counter("ws.messages.total");
        this.wsDisconnectCounter = registry.counter("ws.disconnections.total");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new GameSocketHandler(), "/ws/game")
                .setAllowedOrigins("*");
        registry.addHandler(new MonitorSocketHandler(), "/ws/rooms")
                .setAllowedOrigins("*");
    }

    // монитор всех комнат
    private class MonitorSocketHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            wsConnectCounter.increment();
            broadcaster.register(session);
        }
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            wsDisconnectCounter.increment();
            broadcaster.unregister(session);
        }
    }

    // игровой WS
    private class GameSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {

            wsConnectCounter.increment();

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
            Room room = roomManager.getRoom(roomId);
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
            roomManager.updateRoom(room);
            broadcaster.broadcast(roomId, "PLAYER_JOINED (" + player.getName() + ")");
            broadcastStatus(room);

            if (room.getStatus() == RoomStatus.MAIN_PLAYER_THINKING) {
                sendPromptRequests(room);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage msg) throws Exception {

            wsMessageCounter.increment();

            String roomId = session.getAttributes().get("roomId").toString();
            String userId = session.getAttributes().get("userId").toString();
            Room room = roomManager.getRoom(roomId);
            Player sender = room.getPlayerById(userId);

            // 1) Админ задаёт тему
            if (room.getStatus() == RoomStatus.MAIN_PLAYER_THINKING && sender.isAdmin()) {
                room.setCurrentPrompt(msg.getPayload());
                room.setStatus(RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT);
                roomManager.updateRoom(room);
                broadcaster.broadcast(roomId, "PROMPT_SET (" + sender.getName() + ")");
                for (WebSocketSession s : room.getSessions()) {
                    if (!s.isOpen()) continue;
                    s.sendMessage(new TextMessage("[SYSTEM]: Ситуация: " + room.getCurrentPrompt()));
                }

                // 2) Игроки отвечают
            } else if (room.getStatus() == RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT) {
                room.saveAnswer(userId, msg.getPayload());
                roomManager.updateRoom(room);
                session.sendMessage(new TextMessage("[SYSTEM]: Ответ сохранён"));
                broadcaster.broadcast(roomId, "ANSWER_SUBMITTED (" + sender.getName() + ")");

                if (room.getAnswers().size() == room.getPlayers().size()) {
                    // запускаем оценку
                    roomManager.evaluateAnswersWithGpt(roomId);
                    broadcaster.broadcast(roomId, "ANSWERS_EVALUATED");

                    for (WebSocketSession s : room.getSessions()) {
                        if (!s.isOpen()) continue;
                        Player p = room.getSessionUserMap().get(s);
                        PlayerRoundResult rr = room.getRoundResults().get(p.getId());
                        s.sendMessage(new TextMessage(
                                "[RESULT]: " + p.getName() + " → " + rr.getResult() +
                                        "\nGPT: " + rr.getGptAnswer()
                        ));
                    }
                    broadcastAllPlayerStats(room);

                    room.setStatus(RoomStatus.GAME_DONE);
                    roomManager.updateRoom(room);
                    broadcaster.broadcast(roomId, "ROUND_COMPLETED");
                    broadcastStatus(room);

                    WebSocketSession adminSess = room.getAdminSession();
                    if (adminSess != null && adminSess.isOpen()) {
                        adminSess.sendMessage(new TextMessage("[SYSTEM]: Вы хотите продолжить? [YES/NO]"));
                    }
                }

                // 3) Админ YES/NO
            } else if (room.getStatus() == RoomStatus.GAME_DONE && sender.isAdmin()) {
                String choice = msg.getPayload().trim();
                if ("YES".equalsIgnoreCase(choice)) {
                    roomManager.continueGame(roomId);
                    broadcaster.broadcast(roomId, "CONTINUED");
                    broadcastStatus(roomManager.getRoom(roomId));
                    sendPromptRequests(roomManager.getRoom(roomId));
                } else {
                    room.setStatus(RoomStatus.CLOSED);
                    roomManager.updateRoom(room);
                    broadcaster.broadcast(roomId, "CLOSED");
                    broadcastStatus(room);
                    roomManager.closeGame(roomId);
                }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

            wsDisconnectCounter.increment();

            String roomId = session.getAttributes().get("roomId").toString();
            String userId = session.getAttributes().get("userId").toString();
            Room room = roomManager.getRoom(roomId);
            if (room == null) return;

            // Сохраняем для имени и админ-флага
            Player removed = room.getPlayerById(userId);

            // 1) удаляем WS-сессию
            room.unregisterSession(session);

            // 2) удаляем из игроков + событие
            if (removed != null) {
                room.getPlayers().remove(removed);
                broadcaster.broadcast(roomId, "PLAYER_LEFT (" + removed.getName() + ")");
            }

            // 3) если был админ → ротация, событие и, если надо, запрос темы новому
            if (removed != null && removed.isAdmin() && !room.getPlayers().isEmpty()) {
                room.rotateAdmin();
                Player newAdmin = room.getPlayers().stream()
                        .filter(Player::isAdmin).findFirst().orElse(null);
                if (newAdmin != null) {
                    broadcaster.broadcast(roomId, "ADMIN_CHANGED (" + newAdmin.getName() + ")");
                    if (room.getStatus() == RoomStatus.MAIN_PLAYER_THINKING) {
                        try {
                            sendPromptRequests(room);
                        } catch (IOException e) {
                            log.error("Ошибка при запросе темы новому админу", e);
                        }
                    }
                }
            }

            // 4) если никого не осталось — удаляем комнату
            if (room.getPlayers().isEmpty()) {
                roomManager.closeGame(roomId);
                broadcaster.broadcast(roomId, "ROOM_DELETED");
                return;
            }

            // 5) авто-оценка, если ждали ответа и остался ровно 1 игрок
            if (room.getStatus() == RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT
                    && room.getAnswers().size() == room.getPlayers().size()) {
                try {
                    roomManager.evaluateAnswersWithGpt(roomId);
                    broadcaster.broadcast(roomId, "ANSWERS_EVALUATED");
                    for (WebSocketSession s : room.getSessions()) {
                        if (!s.isOpen()) continue;
                        Player p = room.getSessionUserMap().get(s);
                        PlayerRoundResult rr = room.getRoundResults().get(p.getId());
                        s.sendMessage(new TextMessage(
                                "[RESULT]: " + p.getName() + " → " + rr.getResult() +
                                        "\nGPT: " + rr.getGptAnswer()
                        ));
                    }
                    broadcastAllPlayerStats(room);
                    room.setStatus(RoomStatus.GAME_DONE);
                    roomManager.updateRoom(room);
                    broadcaster.broadcast(roomId, "ROUND_COMPLETED");
                    broadcastStatus(room);
                } catch (Exception e) {
                    log.error("Ошибка авто-оценки после отключения", e);
                }
            }

            // 6) сохраняем и шлём статус
            roomManager.updateRoom(room);
            broadcastStatus(room);
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
            for (WebSocketSession s : room.getSessions()) {
                if (!s.isOpen()) continue;
                try {
                    s.sendMessage(new TextMessage("[SYSTEM]: Статус — " + room.getStatus()));
                } catch (IOException e) {
                    log.error("broadcastStatus error", e);
                }
            }
        }

        private void broadcastAllPlayerStats(Room room) {
            StringBuilder sb = new StringBuilder();
            for (Player p : room.getPlayers()) {
                PlayerStats ps = room.getStats().getOrDefault(p.getId(), new PlayerStats());
                sb.append(p.getName())
                        .append(": выжил ").append(ps.getSurvivedCount())
                        .append(", не выжил ").append(ps.getDiedCount())
                        .append(";\n");
            }
            String allStats = sb.toString().trim();
            for (WebSocketSession s : room.getSessions()) {
                if (!s.isOpen()) continue;
                try {
                    s.sendMessage(new TextMessage("[ALL_STATS]\n" + allStats));
                } catch (IOException e) {
                    log.error("broadcastAllPlayerStats error", e);
                }
            }
        }
    }
}
