package com.timemachine.game.chat;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameChatController {

    private final SimpMessagingTemplate messagingTemplate;

    // 게임별 채팅 히스토리 (인메모리, 최대 100개)
    private final Map<Long, CopyOnWriteArrayList<Map<String, Object>>> chatHistory = new ConcurrentHashMap<>();

    // ─── REST: 채팅 히스토리 조회 ─────────────────────────────────────────

    @GetMapping("/api/game/{gameId}/chat")
    public ResponseEntity<?> getChat(@PathVariable Long gameId) {
        List<Map<String, Object>> msgs = chatHistory.getOrDefault(gameId, new CopyOnWriteArrayList<>());
        return ResponseEntity.ok(msgs);
    }

    // ─── REST: 메시지 전송 (WebSocket 미연결 fallback) ─────────────────────

    @PostMapping("/api/game/{gameId}/chat")
    public ResponseEntity<?> postChat(
        @PathVariable Long gameId,
        @RequestBody Map<String, Object> body,
        HttpSession session
    ) {
        String userName = session.getAttribute("username") != null
            ? session.getAttribute("username").toString()
            : "익명";
        Map<String, Object> msg = buildMessage(gameId, userName, body.getOrDefault("message", "").toString());
        broadcast(gameId, msg);
        return ResponseEntity.ok(msg);
    }

    // ─── STOMP: /app/game/{gameId}/chat ───────────────────────────────────

    @MessageMapping("/game/{gameId}/chat")
    public void handleStompChat(
        @DestinationVariable Long gameId,
        @Payload Map<String, Object> body
    ) {
        String userName = body.getOrDefault("userName", "익명").toString();
        String text     = body.getOrDefault("message", "").toString();
        if (text.isBlank()) return;
        Map<String, Object> msg = buildMessage(gameId, userName, text);
        broadcast(gameId, msg);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private Map<String, Object> buildMessage(Long gameId, String userName, String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", UUID.randomUUID().toString());
        msg.put("gameId", gameId);
        msg.put("userName", userName);
        msg.put("message", text);
        msg.put("sentAt", LocalDateTime.now().toString());
        return msg;
    }

    private void broadcast(Long gameId, Map<String, Object> msg) {
        CopyOnWriteArrayList<Map<String, Object>> history =
            chatHistory.computeIfAbsent(gameId, k -> new CopyOnWriteArrayList<>());
        history.add(msg);
        if (history.size() > 100) history.remove(0);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", msg);
        log.debug("게임 채팅 [{}]: {}", gameId, msg.get("message"));
    }
}
