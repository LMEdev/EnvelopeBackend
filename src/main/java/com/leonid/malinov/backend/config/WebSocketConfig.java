package com.leonid.malinov.backend.config;

import com.leonid.malinov.backend.dto.player.Player;
import com.leonid.malinov.backend.dto.player.PlayerStats;
import com.leonid.malinov.backend.dto.result.PlayerRoundResult;
import com.leonid.malinov.backend.dto.room.Room;
import com.leonid.malinov.backend.model.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.leonid.malinov.backend.service.RoomManagerService;

import java.io.IOException;
import java.net.URI;
import java.util.Map;


@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final RoomManagerService roomManager;
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    public WebSocketConfig(RoomManagerService roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new GameSocketHandler(), "/ws/game").setAllowedOrigins("*");
    }

    private class GameSocketHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            URI uri = session.getUri();
            String roomId=null, userId=null;
            for(String p:uri.getQuery().split("&")){
                var kv=p.split("=",2);
                if(kv[0].equals("roomId")) roomId=kv[1];
                if(kv[0].equals("userId")) userId=kv[1];
            }
            if(roomId==null||userId==null){ session.close(CloseStatus.BAD_DATA); return; }

            Room room = roomManager.getRoom(roomId);
            if(room==null){ session.close(CloseStatus.NOT_ACCEPTABLE); return; }

            session.getAttributes().put("roomId", roomId);
            session.getAttributes().put("userId", userId);

            Player player = room.getPlayerById(userId);
            if(player==null){ session.close(CloseStatus.NOT_ACCEPTABLE); return; }

            room.registerSession(session, player);
            roomManager.updateRoom(room);
            broadcastStatus(room);

            if(room.getStatus()==RoomStatus.MAIN_PLAYER_THINKING){
                sendPromptRequests(room);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage msg) throws Exception {
            String roomId  = (String)session.getAttributes().get("roomId");
            String userId  = (String)session.getAttributes().get("userId");
            Room room = roomManager.getRoom(roomId);
            Player sender = room.getPlayerById(userId);

            // 1) Админ вводит тему
            if(room.getStatus()==RoomStatus.MAIN_PLAYER_THINKING && sender.isAdmin()) {
                room.setCurrentPrompt(msg.getPayload());
                room.setStatus(RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT);
                roomManager.updateRoom(room);
                for(var s: room.getSessions()) if(s.isOpen()){
                    s.sendMessage(new TextMessage(
                            "[SYSTEM]: Ситуация: " + room.getCurrentPrompt()
                    ));
                }

                // 2) Любой вводит свой ответ
            } else if(room.getStatus()==RoomStatus.WAITING_FOR_PLAYER_MESSAGE_AFTER_PROMPT) {
                room.saveAnswer(userId, msg.getPayload());
                roomManager.updateRoom(room);
                session.sendMessage(new TextMessage("[SYSTEM]: Ответ сохранён"));

                // 3) Если уже ВСЕ ответили → оцениваем GPT и рассылаем результаты
                if (room.getAnswers().size() == room.getPlayers().size()) {
                    // — Сначала оцениваем
                    roomManager.evaluateAnswersWithGpt(roomId);

                    // — Рассылаем по каждому участнику персональный результат
                    for (WebSocketSession s : room.getSessions()) {
                        if (!s.isOpen()) continue;
                        Player p = room.getSessionUserMap().get(s);
                        PlayerRoundResult rr = room.getRoundResults().get(p.getId());
                        s.sendMessage(new TextMessage(
                                "[RESULT]: " + p.getName() + " → " + rr.getResult()
                                        + "\nGPT: " + rr.getGptAnswer()
                        ));
                    }

                    // общая статистика
                    broadcastAllPlayerStats(room);

                    // переводим в GAME_DONE
                    room.setStatus(RoomStatus.GAME_DONE);
                    roomManager.updateRoom(room);
                    broadcastStatus(room);

                    WebSocketSession adminSess = room.getAdminSession();
                    if (adminSess != null && adminSess.isOpen()) {
                        adminSess.sendMessage(new TextMessage("[SYSTEM]: Вы хотите продолжить? [YES/NO]"));
                    }
                }
            }  else if (room.getStatus() == RoomStatus.GAME_DONE && sender.isAdmin()) {
                String choice = msg.getPayload().trim();
                if ("YES".equalsIgnoreCase(choice)) {
                    roomManager.continueGame(roomId);
                    Room updated = roomManager.getRoom(roomId);
                    broadcastStatus(updated);
                    sendPromptRequests(updated);
                } else {
                    // завершаем игру
                    room.setStatus(RoomStatus.CLOSED);
                    roomManager.updateRoom(room);
                    broadcastStatus(room);
                    roomManager.closeGame(roomId);
                }
            }
        }

        private void broadcastAllPlayerStats(Room room) {
            // Собираем одну строку с несколькими строчками
            StringBuilder sb = new StringBuilder();
            Map<String, PlayerStats> statsMap = room.getStats();
            for (Player p : room.getPlayers()) {
                PlayerStats ps = statsMap.getOrDefault(p.getId(), new PlayerStats());
                sb.append(p.getName())
                        .append(": выжил ").append(ps.getSurvivedCount())
                        .append(" раз(а), не выжил ").append(ps.getDiedCount())
                        .append(" раз(а);\n");
            }
            String allStats = sb.toString().trim();

            // Рассылаем каждому
            for (WebSocketSession s : room.getSessions()) {
                if (!s.isOpen()) continue;
                try {
                    s.sendMessage(new TextMessage("[ALL_STATS]\n" + allStats));
                } catch (IOException e) {
                    log.error("Не удалось разослать общую статистику", e);
                }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
            String roomId = (String)session.getAttributes().get("roomId");
            Room room = roomManager.getRoom(roomId);
            room.unregisterSession(session);
            roomManager.updateRoom(room);
            broadcastStatus(room);
        }

        private void sendPromptRequests(Room room) throws Exception {
            for(var s:room.getSessions()) if(s.isOpen()){
                if(s.equals(room.getAdminSession()))
                    s.sendMessage(new TextMessage("[SYSTEM]: Введите ситуацию"));
                else
                    s.sendMessage(new TextMessage("[SYSTEM]: Главный игрок вводит тему"));
            }
        }
        private void broadcastStatus(Room room){
            for(var s:room.getSessions()) if(s.isOpen()){
                try{ s.sendMessage(new TextMessage(
                        "[SYSTEM]: Статус — "+room.getStatus()
                )); }catch(Exception e){ log.error(e.getMessage(),e); }
            }
        }
    }
}
