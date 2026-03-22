package com.timemachine.game.core;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameSessionRepository gameSessionRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GameOrderRepository gameOrderRepository;

    // ─── 게임 생성/조회 ───────────────────────────────────────────────────

    /**
     * POST /api/game/start
     * Body: { "seedMoney": 10000000, "duration": "3M", "moveDays": 5, "difficulty": "EASY" }
     */
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();

        Long seedMoney   = Long.parseLong(body.get("seedMoney").toString());
        String duration  = (String) body.getOrDefault("gameDuration", body.getOrDefault("duration", "6M"));
        Integer moveDays = Integer.parseInt(body.getOrDefault("moveDays", "7").toString());
        String difficulty = (String) body.getOrDefault("difficulty", "EASY");

        GameSession gameSession = gameService.startGame(userId, seedMoney, duration, moveDays, difficulty);
        return ResponseEntity.ok(gameSession);
    }

    /**
     * GET /api/game/history
     */
    @GetMapping("/history")
    public ResponseEntity<?> history(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getGameHistory(userId));
    }

    /**
     * GET /api/game/{gameId}
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<?> getGame(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return gameSessionRepository.findById(gameId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/game/{gameId}/current-turn
     */
    @GetMapping("/{gameId}/current-turn")
    public ResponseEntity<?> getCurrentTurn(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return gameTurnRepository.findByGameIdAndStatus(gameId, "ACTIVE")
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/game/{gameId}/move
     */
    @PostMapping("/{gameId}/move")
    public ResponseEntity<?> move(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        Map<String, Object> result = gameService.moveToNextTurn(gameId, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/game/{gameId}/finish
     */
    @PostMapping("/{gameId}/finish")
    public ResponseEntity<?> finish(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.finishGame(gameId, userId));
    }

    /**
     * GET /api/game/{gameId}/ranking
     */
    @GetMapping("/{gameId}/ranking")
    public ResponseEntity<?> ranking(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getRanking(gameId));
    }

    // ─── 주문 ────────────────────────────────────────────────────────────

    /**
     * POST /api/game/turns/{turnId}/orders
     * Body: { "symbol": "005930", "orderType": "BUY", "priceType": "MARKET", "orderPrice": 70000, "quantity": 10 }
     */
    @PostMapping("/turns/{turnId}/orders")
    public ResponseEntity<?> submitOrder(
        @PathVariable Long turnId,
        @RequestBody Map<String, Object> body,
        HttpSession session
    ) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();

        String symbol     = (String) body.get("symbol");
        String orderType  = (String) body.get("orderType");
        String priceType  = (String) body.get("priceType");
        BigDecimal price  = body.get("orderPrice") != null
            ? new BigDecimal(body.get("orderPrice").toString())
            : BigDecimal.ZERO;
        int quantity      = Integer.parseInt(body.get("quantity").toString());

        Map<String, Object> result = gameService.submitOrder(turnId, userId, symbol, orderType, priceType, price, quantity);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/game/turns/{turnId}/orders
     */
    @GetMapping("/turns/{turnId}/orders")
    public ResponseEntity<?> getOrders(@PathVariable Long turnId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameOrderRepository.findByTurnIdAndUserId(turnId, userId));
    }

    // ─── 포트폴리오 ──────────────────────────────────────────────────────

    /**
     * GET /api/game/{gameId}/holdings
     */
    @GetMapping("/{gameId}/holdings")
    public ResponseEntity<?> holdings(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getHoldings(gameId, userId));
    }

    /**
     * GET /api/game/{gameId}/balance
     */
    @GetMapping("/{gameId}/balance")
    public ResponseEntity<?> balance(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getBalance(gameId, userId));
    }

    /**
     * GET /api/game/{gameId}/portfolio-history
     */
    @GetMapping("/{gameId}/portfolio-history")
    public ResponseEntity<?> portfolioHistory(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getPortfolioHistory(gameId, userId));
    }

    /**
     * GET /api/game/{gameId}/trades
     */
    @GetMapping("/{gameId}/trades")
    public ResponseEntity<?> trades(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getTradeHistory(gameId, userId));
    }

    /**
     * GET /api/game/{gameId}/analysis
     */
    @GetMapping("/{gameId}/analysis")
    public ResponseEntity<?> analysis(@PathVariable Long gameId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(gameService.getAnalysis(gameId, userId));
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private Long getUserId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));
    }
}
