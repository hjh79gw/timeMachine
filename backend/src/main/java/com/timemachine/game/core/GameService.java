package com.timemachine.game.core;

import com.timemachine.game.stock.StockDaily;
import com.timemachine.game.stock.StockDailyRepository;
import com.timemachine.game.stock.StockInfo;
import com.timemachine.game.stock.StockInfoRepository;
import com.timemachine.auth.User;
import com.timemachine.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameSessionRepository gameSessionRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GameOrderRepository gameOrderRepository;
    private final GameHoldingRepository gameHoldingRepository;
    private final GamePortfolioSnapshotRepository portfolioSnapshotRepository;
    private final StockDailyRepository stockDailyRepository;
    private final StockInfoRepository stockInfoRepository;
    private final UserRepository userRepository;

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.00015"); // 0.015%
    private static final BigDecimal SELL_TAX_RATE   = new BigDecimal("0.0018");  // 0.18%

    // ─── 게임 시작 ─────────────────────────────────────────────────────────

    @Transactional
    public GameSession startGame(Long userId, Long seedMoney, String duration, Integer moveDays, String difficulty) {
        // stock_daily에서 데이터 있는 날짜 범위 확인
        LocalDate minDate = stockDailyRepository.findMinTradeDate()
            .orElseThrow(() -> new RuntimeException("주식 데이터가 없습니다. 배치를 먼저 실행해주세요."));
        LocalDate maxDate = stockDailyRepository.findMaxTradeDate()
            .orElseThrow(() -> new RuntimeException("주식 데이터가 없습니다."));

        // duration 파싱
        LocalDate endDateLimit;
        switch (duration) {
            case "3M" -> endDateLimit = minDate.plusMonths(3);
            case "6M" -> endDateLimit = minDate.plusMonths(6);
            case "1Y" -> endDateLimit = minDate.plusYears(1);
            default   -> throw new RuntimeException("잘못된 duration: " + duration + " (3M/6M/1Y)");
        }

        // 랜덤 시작일 결정: minDate ~ (maxDate - duration)
        List<LocalDate> tradeDates = stockDailyRepository.findAllTradeDates();
        if (tradeDates.isEmpty()) {
            throw new RuntimeException("주식 거래일 데이터가 없습니다.");
        }

        // 종료일 기준으로 유효한 시작일 필터링
        long durationDays = switch (duration) {
            case "3M" -> 90;
            case "6M" -> 180;
            case "1Y" -> 365;
            default   -> 90;
        };
        LocalDate latestStart = maxDate.minusDays(durationDays);
        List<LocalDate> validStarts = tradeDates.stream()
            .filter(d -> !d.isAfter(latestStart))
            .toList();

        if (validStarts.isEmpty()) {
            throw new RuntimeException("데이터 범위가 부족합니다. 더 긴 기간의 데이터를 수집해주세요.");
        }

        Random rng = new Random();
        LocalDate startDate = validStarts.get(rng.nextInt(validStarts.size()));
        LocalDate endDate = startDate.plusDays(durationDays);

        // 게임 세션 생성
        GameSession session = GameSession.builder()
            .familyId(null)
            .seedMoney(seedMoney)
            .gameDuration(duration)
            .startDate(startDate)
            .endDate(endDate)
            .moveDays(moveDays)
            .difficulty(difficulty)
            .currentDate(startDate)
            .status("IN_PROGRESS")
            .build();
        gameSessionRepository.save(session);

        // 참가자 생성
        GameParticipant participant = GameParticipant.builder()
            .gameId(session.getId())
            .userId(userId)
            .currentCash(seedMoney)
            .totalAsset(seedMoney)
            .totalProfitRate(BigDecimal.ZERO)
            .build();
        gameParticipantRepository.save(participant);

        // 첫 번째 턴 생성
        GameTurn turn = GameTurn.builder()
            .gameId(session.getId())
            .turnNumber(1)
            .currentDate(startDate)
            .status("ACTIVE")
            .build();
        gameTurnRepository.save(turn);

        log.info("게임 시작: gameId={}, userId={}, {}~{}", session.getId(), userId, startDate, endDate);
        return session;
    }

    // ─── 다음 턴으로 이동 ─────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> moveToNextTurn(Long gameId, Long userId) {
        GameSession session = gameSessionRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("게임 없음: " + gameId));

        if ("FINISHED".equals(session.getStatus())) {
            throw new RuntimeException("이미 종료된 게임입니다.");
        }

        // 현재 활성 턴 조회
        GameTurn currentTurn = gameTurnRepository.findByGameIdAndStatus(gameId, "ACTIVE")
            .orElseThrow(() -> new RuntimeException("활성 턴 없음"));

        GameParticipant participant = gameParticipantRepository.findByGameIdAndUserId(gameId, userId)
            .orElseThrow(() -> new RuntimeException("참가자 없음"));

        // 보유 종목 평가금 계산
        long holdingsValue = calculateHoldingsValue(gameId, userId, currentTurn.getCurrentDate());

        // 포트폴리오 스냅샷 저장
        long totalAsset = participant.getCurrentCash() + holdingsValue;
        BigDecimal profitRate = BigDecimal.ZERO;
        if (session.getSeedMoney() > 0) {
            profitRate = BigDecimal.valueOf(totalAsset - session.getSeedMoney())
                .divide(BigDecimal.valueOf(session.getSeedMoney()), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        GamePortfolioSnapshot snapshot = GamePortfolioSnapshot.builder()
            .turnId(currentTurn.getId())
            .userId(userId)
            .cash(participant.getCurrentCash())
            .holdingsValue(holdingsValue)
            .totalAsset(totalAsset)
            .profitRate(profitRate)
            .snapshotDate(currentTurn.getCurrentDate())
            .build();
        portfolioSnapshotRepository.save(snapshot);

        // 참가자 정보 업데이트
        participant.setTotalAsset(totalAsset);
        participant.setTotalProfitRate(profitRate);
        gameParticipantRepository.save(participant);

        // 현재 턴 완료 처리
        currentTurn.setStatus("COMPLETED");
        gameTurnRepository.save(currentTurn);

        // 다음 날짜 계산 (영업일 기준)
        List<LocalDate> tradeDates = stockDailyRepository.findAllTradeDates();
        LocalDate nextDate = findNextBusinessDate(currentTurn.getCurrentDate(), session.getMoveDays(), tradeDates);

        // 종료일 초과 시 게임 종료
        if (nextDate == null || nextDate.isAfter(session.getEndDate())) {
            session.setStatus("FINISHED");
            session.setCurrentDate(currentTurn.getCurrentDate());
            gameSessionRepository.save(session);
            log.info("게임 종료: gameId={}", gameId);
            return Map.of(
                "status", "FINISHED",
                "message", "게임이 종료되었습니다.",
                "totalAsset", totalAsset,
                "profitRate", profitRate
            );
        }

        // 새 턴 생성
        int nextTurnNumber = currentTurn.getTurnNumber() + 1;
        GameTurn newTurn = GameTurn.builder()
            .gameId(gameId)
            .turnNumber(nextTurnNumber)
            .currentDate(nextDate)
            .status("ACTIVE")
            .build();
        gameTurnRepository.save(newTurn);

        session.setCurrentDate(nextDate);
        gameSessionRepository.save(session);

        return Map.of(
            "status", "IN_PROGRESS",
            "turnId", newTurn.getId(),
            "turnNumber", nextTurnNumber,
            "currentDate", nextDate.toString(),
            "cash", participant.getCurrentCash(),
            "holdingsValue", holdingsValue,
            "totalAsset", totalAsset,
            "profitRate", profitRate
        );
    }

    // ─── 주문 처리 ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> submitOrder(Long turnId, Long userId, String symbol,
                                           String orderType, String priceType,
                                           BigDecimal orderPrice, Integer quantity) {
        GameTurn turn = gameTurnRepository.findById(turnId)
            .orElseThrow(() -> new RuntimeException("턴 없음: " + turnId));

        if (!"ACTIVE".equals(turn.getStatus())) {
            throw new RuntimeException("활성 상태의 턴이 아닙니다.");
        }

        GameParticipant participant = gameParticipantRepository.findByGameIdAndUserId(turn.getGameId(), userId)
            .orElseThrow(() -> new RuntimeException("참가자 없음"));

        // 해당일 일봉 조회
        Optional<StockDaily> dailyOpt = stockDailyRepository.findBySymbolAndTradeDate(symbol, turn.getCurrentDate());
        if (dailyOpt.isEmpty()) {
            dailyOpt = stockDailyRepository.findLatestOnOrBefore(symbol, turn.getCurrentDate());
        }
        StockDaily daily = dailyOpt.orElseThrow(() ->
            new RuntimeException("해당 종목/날짜 데이터 없음: " + symbol + "/" + turn.getCurrentDate()));

        BigDecimal openPrice  = daily.getOpenPrice();
        BigDecimal highPrice  = daily.getHighPrice();
        BigDecimal lowPrice   = daily.getLowPrice();

        // 체결 가격 결정
        BigDecimal executedPrice;
        boolean executed = false;

        if ("MARKET".equals(priceType)) {
            executedPrice = openPrice; // 시장가: 시가로 체결
            executed = true;
        } else if ("LIMIT".equals(priceType)) {
            if ("BUY".equals(orderType)) {
                // 매수 지정가: 지정가 >= 저가이면 체결 (지정가로 체결)
                if (orderPrice.compareTo(lowPrice) >= 0) {
                    executedPrice = orderPrice;
                    executed = true;
                } else {
                    executedPrice = orderPrice;
                }
            } else { // SELL
                // 매도 지정가: 지정가 <= 고가이면 체결 (지정가로 체결)
                if (orderPrice.compareTo(highPrice) <= 0) {
                    executedPrice = orderPrice;
                    executed = true;
                } else {
                    executedPrice = orderPrice;
                }
            }
        } else {
            throw new RuntimeException("잘못된 priceType: " + priceType);
        }

        if (!executed) {
            GameOrder order = GameOrder.builder()
                .turnId(turnId)
                .userId(userId)
                .symbol(symbol)
                .orderType(orderType)
                .priceType(priceType)
                .orderPrice(orderPrice)
                .executedPrice(executedPrice)
                .quantity(quantity)
                .commission(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .status("REJECTED")
                .build();
            gameOrderRepository.save(order);
            return Map.of("status", "REJECTED", "reason", "지정가 미체결");
        }

        // 수수료/세금 계산
        BigDecimal tradeAmount = executedPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal commission  = tradeAmount.multiply(COMMISSION_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal tax         = "SELL".equals(orderType)
            ? tradeAmount.multiply(SELL_TAX_RATE).setScale(0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal totalCost = tradeAmount.add(commission).add(tax);

        if ("BUY".equals(orderType)) {
            long required = totalCost.longValue();
            if (participant.getCurrentCash() < required) {
                throw new RuntimeException("잔고 부족. 필요: " + required + ", 보유: " + participant.getCurrentCash());
            }
            participant.setCurrentCash(participant.getCurrentCash() - required);

            // 보유 종목 업데이트
            GameHolding holding = gameHoldingRepository
                .findByGameIdAndUserIdAndSymbol(turn.getGameId(), userId, symbol)
                .orElse(GameHolding.builder()
                    .gameId(turn.getGameId())
                    .userId(userId)
                    .symbol(symbol)
                    .quantity(0)
                    .avgBuyPrice(BigDecimal.ZERO)
                    .build());
            holding.buy(quantity, executedPrice);
            gameHoldingRepository.save(holding);

        } else { // SELL
            GameHolding holding = gameHoldingRepository
                .findByGameIdAndUserIdAndSymbol(turn.getGameId(), userId, symbol)
                .orElseThrow(() -> new RuntimeException("보유 종목 없음: " + symbol));

            if (holding.getQuantity() < quantity) {
                throw new RuntimeException("보유 수량 부족. 보유: " + holding.getQuantity() + ", 매도 요청: " + quantity);
            }

            holding.sell(quantity);
            gameHoldingRepository.save(holding);

            long received = tradeAmount.subtract(commission).subtract(tax).longValue();
            participant.setCurrentCash(participant.getCurrentCash() + received);
        }

        gameParticipantRepository.save(participant);

        // 주문 저장
        GameOrder order = GameOrder.builder()
            .turnId(turnId)
            .userId(userId)
            .symbol(symbol)
            .orderType(orderType)
            .priceType(priceType)
            .orderPrice(orderPrice)
            .executedPrice(executedPrice)
            .quantity(quantity)
            .commission(commission)
            .tax(tax)
            .status("EXECUTED")
            .build();
        gameOrderRepository.save(order);

        return Map.of(
            "status", "EXECUTED",
            "orderId", order.getId(),
            "symbol", symbol,
            "orderType", orderType,
            "executedPrice", executedPrice,
            "quantity", quantity,
            "commission", commission,
            "tax", tax,
            "remainingCash", participant.getCurrentCash()
        );
    }

    // ─── 잔고 조회 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getBalance(Long gameId, Long userId) {
        GameParticipant participant = gameParticipantRepository.findByGameIdAndUserId(gameId, userId)
            .orElseThrow(() -> new RuntimeException("참가자 없음"));
        GameSession session = gameSessionRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("게임 없음"));

        long holdingsValue = calculateHoldingsValue(gameId, userId, session.getCurrentDate());
        long totalAsset = participant.getCurrentCash() + holdingsValue;

        BigDecimal profitRate = BigDecimal.ZERO;
        if (session.getSeedMoney() > 0) {
            profitRate = BigDecimal.valueOf(totalAsset - session.getSeedMoney())
                .divide(BigDecimal.valueOf(session.getSeedMoney()), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cash", participant.getCurrentCash());
        result.put("holdingsValue", holdingsValue);
        result.put("totalAsset", totalAsset);
        result.put("profitRate", profitRate);
        result.put("seedMoney", session.getSeedMoney());
        return result;
    }

    // ─── 보유 종목 조회 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHoldings(Long gameId, Long userId) {
        GameSession session = gameSessionRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("게임 없음"));
        List<GameHolding> holdings = gameHoldingRepository.findByGameIdAndUserId(gameId, userId);

        return holdings.stream()
            .filter(h -> h.getQuantity() > 0)
            .map(h -> {
                Optional<StockDaily> daily = stockDailyRepository.findLatestOnOrBefore(h.getSymbol(), session.getCurrentDate());
                BigDecimal currentPrice = daily.map(StockDaily::getClosePrice).orElse(h.getAvgBuyPrice());
                BigDecimal evalAmount   = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
                BigDecimal buyAmount    = h.getAvgBuyPrice().multiply(BigDecimal.valueOf(h.getQuantity()));
                BigDecimal pnl          = evalAmount.subtract(buyAmount);
                BigDecimal pnlRate      = buyAmount.compareTo(BigDecimal.ZERO) > 0
                    ? pnl.divide(buyAmount, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

                String stockName = stockInfoRepository.findById(h.getSymbol())
                    .map(StockInfo::getName)
                    .orElse(h.getSymbol());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("symbol", h.getSymbol());
                item.put("stockName", stockName);
                item.put("quantity", h.getQuantity());
                item.put("avgBuyPrice", h.getAvgBuyPrice());
                item.put("currentPrice", currentPrice);
                item.put("evalAmount", evalAmount.longValue());
                item.put("pnl", pnl.longValue());
                item.put("profitRate", pnlRate.setScale(2, RoundingMode.HALF_UP));
                return item;
            }).toList();
    }

    // ─── 게임 종료 ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> finishGame(Long gameId, Long userId) {
        GameSession session = gameSessionRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("게임 없음"));

        if ("FINISHED".equals(session.getStatus())) {
            return Map.of("message", "이미 종료된 게임");
        }

        // 현재 활성 턴 완료 처리
        gameTurnRepository.findByGameIdAndStatus(gameId, "ACTIVE").ifPresent(turn -> {
            turn.setStatus("COMPLETED");
            gameTurnRepository.save(turn);
        });

        // 참가자 최종 자산 업데이트 + 최종 스냅샷 저장
        List<GameParticipant> participants = gameParticipantRepository.findByGameId(gameId);
        // 최종 완료된 턴 조회 (스냅샷 저장용)
        GameTurn lastTurn = gameTurnRepository.findByGameIdOrderByTurnNumberAsc(gameId).stream()
            .filter(t -> "COMPLETED".equals(t.getStatus()))
            .reduce((a, b) -> b).orElse(null);

        for (GameParticipant p : participants) {
            long holdingsValue = calculateHoldingsValue(gameId, p.getUserId(), session.getCurrentDate());
            long totalAsset = p.getCurrentCash() + holdingsValue;
            BigDecimal profitRate = session.getSeedMoney() > 0
                ? BigDecimal.valueOf(totalAsset - session.getSeedMoney())
                    .divide(BigDecimal.valueOf(session.getSeedMoney()), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
            p.setTotalAsset(totalAsset);
            p.setTotalProfitRate(profitRate);
            gameParticipantRepository.save(p);

            // 마지막 턴의 최종 스냅샷이 없으면 저장
            if (lastTurn != null) {
                List<GamePortfolioSnapshot> existing = portfolioSnapshotRepository.findByTurnIdAndUserId(lastTurn.getId(), p.getUserId());
                if (existing.isEmpty()) {
                    GamePortfolioSnapshot snap = GamePortfolioSnapshot.builder()
                        .turnId(lastTurn.getId())
                        .userId(p.getUserId())
                        .cash(p.getCurrentCash())
                        .holdingsValue(holdingsValue)
                        .totalAsset(totalAsset)
                        .profitRate(profitRate)
                        .snapshotDate(session.getCurrentDate())
                        .build();
                    portfolioSnapshotRepository.save(snap);
                }
            }
        }

        // 랭킹 업데이트
        participants.sort((a, b) -> Long.compare(
            b.getTotalAsset() != null ? b.getTotalAsset() : 0L,
            a.getTotalAsset() != null ? a.getTotalAsset() : 0L
        ));
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).setRankNum(i + 1);
            gameParticipantRepository.save(participants.get(i));
        }

        session.setStatus("FINISHED");
        gameSessionRepository.save(session);

        return Map.of(
            "message", "게임 종료",
            "gameId", gameId,
            "participants", participants.size()
        );
    }

    // ─── 랭킹 조회 ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRanking(Long gameId) {
        GameSession session = gameSessionRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("게임 없음"));
        List<GameParticipant> participants = gameParticipantRepository.findByGameId(gameId);

        List<Map<String, Object>> result = participants.stream().map(p -> {
            long holdingsValue = calculateHoldingsValue(gameId, p.getUserId(), session.getCurrentDate());
            long totalAsset = p.getCurrentCash() + holdingsValue;
            BigDecimal profitRate = session.getSeedMoney() > 0
                ? BigDecimal.valueOf(totalAsset - session.getSeedMoney())
                    .divide(BigDecimal.valueOf(session.getSeedMoney()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            Map<String, Object> m = new LinkedHashMap<>();
            String userName = userRepository.findById(p.getUserId())
                .map(User::getUsername)
                .orElse("플레이어" + p.getUserId());
            m.put("userId", p.getUserId());
            m.put("userName", userName);
            m.put("totalAsset", totalAsset);
            m.put("profitRate", profitRate.setScale(2, RoundingMode.HALF_UP));
            m.put("rankNum", p.getRankNum());
            return m;
        }).sorted((a, b) -> Long.compare(
            (Long) b.get("totalAsset"), (Long) a.get("totalAsset")
        )).toList();

        return result;
    }

    // ─── 포트폴리오 히스토리 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPortfolioHistory(Long gameId, Long userId) {
        List<GameTurn> turns = gameTurnRepository.findByGameIdOrderByTurnNumberAsc(gameId);
        List<Map<String, Object>> history = new ArrayList<>();

        for (GameTurn turn : turns) {
            List<GamePortfolioSnapshot> snapshots = portfolioSnapshotRepository.findByTurnIdAndUserId(turn.getId(), userId);
            for (GamePortfolioSnapshot snap : snapshots) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("turnNumber", turn.getTurnNumber());
                item.put("date", snap.getSnapshotDate());
                item.put("cash", snap.getCash());
                item.put("holdingsValue", snap.getHoldingsValue());
                item.put("totalAsset", snap.getTotalAsset());
                item.put("profitRate", snap.getProfitRate());
                history.add(item);
            }
        }
        return history;
    }

    // ─── 매매 내역 조회 ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTradeHistory(Long gameId, Long userId) {
        List<GameTurn> turns = gameTurnRepository.findByGameIdOrderByTurnNumberAsc(gameId);
        List<Long> turnIds = turns.stream().map(GameTurn::getId).toList();
        List<GameOrder> orders = gameOrderRepository.findByTurnIdInAndUserId(turnIds, userId);

        Map<Long, LocalDate> turnDateMap = turns.stream()
            .collect(java.util.stream.Collectors.toMap(GameTurn::getId, GameTurn::getCurrentDate));

        return orders.stream()
            .filter(o -> "EXECUTED".equals(o.getStatus()))
            .map(o -> {
                String stockName = stockInfoRepository.findById(o.getSymbol())
                    .map(StockInfo::getName)
                    .orElse(o.getSymbol());
                BigDecimal price = o.getExecutedPrice() != null ? o.getExecutedPrice() : o.getOrderPrice();
                long amount = price != null ? price.multiply(BigDecimal.valueOf(o.getQuantity())).longValue() : 0L;

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getId());
                m.put("tradeDate", turnDateMap.getOrDefault(o.getTurnId(), LocalDate.now()).toString());
                m.put("symbol", o.getSymbol());
                m.put("stockName", stockName);
                m.put("orderType", o.getOrderType());
                m.put("quantity", o.getQuantity());
                m.put("price", price);
                m.put("amount", amount);
                return m;
            }).toList();
    }

    // ─── AI 분석 리포트 ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalysis(Long gameId, Long userId) {
        GameSession session = gameSessionRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("게임 없음"));

        // 최종 자산 계산
        GameParticipant participant = gameParticipantRepository.findByGameIdAndUserId(gameId, userId)
            .orElseThrow(() -> new RuntimeException("참가자 없음"));
        long holdingsValue = calculateHoldingsValue(gameId, userId, session.getCurrentDate());
        long totalAsset = participant.getCurrentCash() + holdingsValue;
        BigDecimal profitRate = session.getSeedMoney() > 0
            ? BigDecimal.valueOf(totalAsset - session.getSeedMoney())
                .divide(BigDecimal.valueOf(session.getSeedMoney()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // 매매 내역
        List<Map<String, Object>> trades = getTradeHistory(gameId, userId);
        int totalTrades = trades.size();

        // 턴별 브리핑 조회
        List<GameTurn> turns = gameTurnRepository.findByGameIdOrderByTurnNumberAsc(gameId);

        // GPT 분석 리포트
        String report = generateAnalysisReport(session, trades, turns, totalAsset, profitRate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAsset", totalAsset);
        result.put("finalProfitRate", profitRate.setScale(2, RoundingMode.HALF_UP));
        result.put("totalTrades", totalTrades);
        result.put("report", report);
        return result;
    }

    private String generateAnalysisReport(GameSession session, List<Map<String, Object>> trades,
                                           List<GameTurn> turns, long totalAsset, BigDecimal profitRate) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return "AI 분석을 위한 API 키가 설정되지 않았습니다.";
        }

        // 날짜 → 브리핑 맵 구성
        Map<String, String> briefingByDate = turns.stream()
            .filter(t -> t.getAiBriefing() != null && !t.getAiBriefing().isBlank())
            .collect(Collectors.toMap(
                t -> t.getCurrentDate().toString(),
                GameTurn::getAiBriefing,
                (a, b) -> a
            ));

        // 날짜 → 해당 날 매매 목록
        Map<String, List<Map<String, Object>>> tradesByDate = trades.stream()
            .collect(Collectors.groupingBy(t -> t.get("tradeDate").toString()));

        // 브리핑이 있는 날짜 순서로 컨텍스트 구성
        StringBuilder contextBuilder = new StringBuilder();
        List<String> sortedDates = new ArrayList<>(briefingByDate.keySet());
        Collections.sort(sortedDates);

        for (String date : sortedDates) {
            String briefing = briefingByDate.get(date);
            List<Map<String, Object>> dayTrades = tradesByDate.getOrDefault(date, Collections.emptyList());

            contextBuilder.append("\n[").append(date).append(" 시장 동향]\n");
            String briefingShort = briefing.length() > 300 ? briefing.substring(0, 300) + "..." : briefing;
            contextBuilder.append(briefingShort).append("\n");

            if (!dayTrades.isEmpty()) {
                contextBuilder.append("→ 이 날 매매: ");
                String dayTradeSummary = dayTrades.stream()
                    .map(t -> String.format("%s %s %s주 @%s",
                        t.get("stockName"),
                        "BUY".equals(t.get("orderType")) ? "매수" : "매도",
                        t.get("quantity"), t.get("price")))
                    .collect(Collectors.joining(", "));
                contextBuilder.append(dayTradeSummary).append("\n");
            } else {
                contextBuilder.append("→ 이 날 매매 없음\n");
            }
        }

        // 브리핑 없는 날의 매매도 추가
        String extraTrades = trades.stream()
            .filter(t -> !briefingByDate.containsKey(t.get("tradeDate").toString()))
            .map(t -> String.format("%s %s %s %s주 @%s",
                t.get("tradeDate"), t.get("stockName"),
                "BUY".equals(t.get("orderType")) ? "매수" : "매도",
                t.get("quantity"), t.get("price")))
            .collect(Collectors.joining("\n"));

        String contextSection = contextBuilder.toString().isBlank() ? "시장 동향 데이터 없음" : contextBuilder.toString();
        if (!extraTrades.isBlank()) {
            contextSection += "\n[기타 매매 내역]\n" + extraTrades;
        }
        if (trades.isEmpty()) contextSection += "\n매매 내역 없음";

        String prompt = String.format("""
            아래는 주식 투자 시뮬레이션 게임의 시장 동향과 매매 내역입니다.
            게임 기간: %s ~ %s
            시드머니: %s원 / 최종 자산: %s원 / 수익률: %s%%

            %s

            위의 각 날짜별 시장 동향과 해당 날의 매매를 비교 분석해주세요.
            특히 시장 상황에 비추어 각 매매가 적절했는지 평가해주세요.
            반드시 아래 형식으로 한국어로 작성하세요. 이모티콘은 사용하지 마세요:

            [투자 성과] (2문장)
            [투자 패턴 분석] 시장 동향과 매매 결정을 연결하여 분석 (3~4문장)
            [개선 제안] 시장 상황 대비 더 나은 결정이 가능했던 시점 언급 (2~3문장)
            """,
            session.getStartDate(), session.getCurrentDate(),
            session.getSeedMoney(), totalAsset,
            profitRate.setScale(2, RoundingMode.HALF_UP),
            contextSection
        );

        try {
            Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o",
                "messages", List.of(
                    Map.of("role", "system", "content", "당신은 나비라는 이름의 주식 투자 코치입니다. 이모티콘 없이 투자자의 매매 내역을 분석하여 유익한 피드백을 제공합니다."),
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 900,
                "temperature", 0.7
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = org.springframework.web.reactive.function.client.WebClient.create("https://api.openai.com")
                .post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve().bodyToMono(Map.class).block();

            if (response == null) return "분석 실패";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return "분석 실패";
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return msg != null ? (String) msg.get("content") : "분석 실패";
        } catch (Exception e) {
            log.error("[분석] GPT 오류: {}", e.getMessage());
            return "AI 분석 중 오류가 발생했습니다.";
        }
    }

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    // ─── 게임 이력 조회 ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGameHistory(Long userId) {
        List<GameParticipant> myGames = gameParticipantRepository.findByUserId(userId);
        return myGames.stream()
            .sorted(Comparator.comparingLong(p -> -(p.getId() != null ? p.getId() : 0L)))
            .map(p -> gameSessionRepository.findById(p.getGameId()).map(s -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", s.getId());
                item.put("seedMoney", s.getSeedMoney());
                item.put("gameDuration", s.getGameDuration());
                item.put("startDate", s.getStartDate() != null ? s.getStartDate().toString() : null);
                item.put("endDate", s.getEndDate() != null ? s.getEndDate().toString() : null);
                item.put("status", s.getStatus());
                item.put("profitRate", p.getTotalProfitRate());
                return item;
            }).orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────

    private long calculateHoldingsValue(Long gameId, Long userId, LocalDate date) {
        List<GameHolding> holdings = gameHoldingRepository.findByGameIdAndUserId(gameId, userId);
        long total = 0L;
        for (GameHolding h : holdings) {
            if (h.getQuantity() <= 0) continue;
            Optional<StockDaily> daily = stockDailyRepository.findLatestOnOrBefore(h.getSymbol(), date);
            BigDecimal price = daily.map(StockDaily::getClosePrice).orElse(h.getAvgBuyPrice());
            total += price.multiply(BigDecimal.valueOf(h.getQuantity())).longValue();
        }
        return total;
    }

    private LocalDate findNextBusinessDate(LocalDate from, int moveDays, List<LocalDate> tradeDates) {
        // tradeDates는 정렬된 목록. from 이후 moveDays번째 영업일 찾기
        int count = 0;
        for (LocalDate d : tradeDates) {
            if (d.isAfter(from)) {
                count++;
                if (count >= moveDays) return d;
            }
        }
        return null; // 더 이상 영업일 없음
    }
}
