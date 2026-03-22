package com.timemachine.game.stock;

import com.timemachine.kis.KisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCollectionService {

    @Value("${kis.base-url}")
    private String baseUrl;

    private final KisTokenService tokenService;
    private final StockDailyRepository stockDailyRepository;
    private final BatchProgressRepository batchProgressRepository;
    private final StockInfoRepository stockInfoRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter KIS_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter LOCAL_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String BATCH_TYPE = "DAILY";

    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    // ─── KIS API 호출 (FHKST03010100) ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void collectDailyData(String symbol, String startDate, String endDate) {
        int retryCount = 0;
        int maxRetry = 3;
        long backoffMs = 2000;

        while (retryCount <= maxRetry) {
            try {
                // 요청 간 300ms sleep
                Thread.sleep(300);

                var response = WebClient.create(baseUrl)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", symbol)
                        .queryParam("FID_INPUT_DATE_1", startDate)
                        .queryParam("FID_INPUT_DATE_2", endDate)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                    .header("authorization", "Bearer " + tokenService.getAccessToken())
                    .header("appkey", tokenService.getAppKey())
                    .header("appsecret", tokenService.getAppSecret())
                    .header("tr_id", "FHKST03010100")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

                if (response == null) {
                    log.warn("[{}] KIS API 응답 null", symbol);
                    return;
                }

                // output1: 종목 기본 정보
                Map<String, Object> output1 = (Map<String, Object>) response.get("output1");
                if (output1 != null) {
                    saveStockInfo(symbol, output1);
                }

                // output2: 일봉 데이터 목록
                List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.get("output2");
                if (output2 == null || output2.isEmpty()) {
                    log.info("[{}] output2 없음 (데이터 없음)", symbol);
                    return;
                }

                upsertDailyData(symbol, output2);
                log.info("[{}] 일봉 {}건 저장 완료", symbol, output2.size());
                return; // 성공

            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    retryCount++;
                    log.warn("[{}] 429 Too Many Requests, 재시도 {}/{}, {}ms 대기", symbol, retryCount, maxRetry, backoffMs);
                    if (retryCount > maxRetry) {
                        log.error("[{}] 최대 재시도 초과 - FAILED 처리", symbol);
                        throw new RuntimeException("KIS API 429 최대 재시도 초과: " + symbol);
                    }
                    sleepSilently(backoffMs);
                    backoffMs *= 2; // exponential backoff
                } else {
                    log.error("[{}] KIS API 오류 {}: {}", symbol, e.getStatusCode(), e.getMessage());
                    retryCount++;
                    if (retryCount > maxRetry) {
                        throw new RuntimeException("KIS API 오류 최대 재시도 초과: " + symbol);
                    }
                    sleepSilently(backoffMs);
                    backoffMs *= 2;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("배치 중단됨: " + symbol);
            } catch (Exception e) {
                retryCount++;
                log.error("[{}] 오류 발생 (재시도 {}/{}): {}", symbol, retryCount, maxRetry, e.getMessage());
                if (retryCount > maxRetry) {
                    throw new RuntimeException("최대 재시도 초과: " + symbol + " - " + e.getMessage());
                }
                sleepSilently(backoffMs);
                backoffMs *= 2;
            }
        }
    }

    protected void saveStockInfo(String symbol, Map<String, Object> output1) {
        String name = (String) output1.getOrDefault("hts_kor_isnm", symbol);
        jdbcTemplate.update("""
            INSERT INTO game_stock_info (symbol, name, market, sector)
            VALUES (?, ?, 'KR', '')
            ON DUPLICATE KEY UPDATE name = VALUES(name)
            """, symbol, name);
    }

    protected void upsertDailyData(String symbol, List<Map<String, Object>> output2) {
        for (Map<String, Object> item : output2) {
            String dateStr = (String) item.get("stck_bsop_date");
            if (dateStr == null || dateStr.isBlank()) continue;

            LocalDate tradeDate = LocalDate.parse(dateStr, LOCAL_DATE_FMT);
            BigDecimal openPrice  = parseBigDecimal(item.get("stck_oprc"));
            BigDecimal highPrice  = parseBigDecimal(item.get("stck_hgpr"));
            BigDecimal lowPrice   = parseBigDecimal(item.get("stck_lwpr"));
            BigDecimal closePrice = parseBigDecimal(item.get("stck_clpr"));
            Long volume           = parseLong(item.get("acml_vol"));
            BigDecimal changeRate = parseBigDecimal(item.get("prdy_ctrt"));

            if (closePrice == null || closePrice.compareTo(BigDecimal.ZERO) == 0) continue;

            jdbcTemplate.update("""
                INSERT INTO game_stock_daily (symbol, trade_date, open_price, high_price, low_price, close_price, volume, change_rate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    open_price = VALUES(open_price), high_price = VALUES(high_price),
                    low_price = VALUES(low_price), close_price = VALUES(close_price),
                    volume = VALUES(volume), change_rate = VALUES(change_rate)
                """, symbol, tradeDate, openPrice, highPrice, lowPrice, closePrice, volume, changeRate);
        }
    }

    // ─── 배치 초기화 ────────────────────────────────────────────────────────

    @Transactional
    public void initBatch(List<String> symbols, String startDate, String endDate) {
        for (String symbol : symbols) {
            Optional<BatchProgress> existing = batchProgressRepository.findBySymbolAndBatchType(symbol, BATCH_TYPE);
            if (existing.isPresent()) {
                BatchProgress bp = existing.get();
                bp.setStatus("PENDING");
                bp.setRetryCount(0);
                bp.setErrorMessage(null);
                batchProgressRepository.save(bp);
            } else {
                BatchProgress bp = BatchProgress.builder()
                    .symbol(symbol)
                    .batchType(BATCH_TYPE)
                    .status("PENDING")
                    .retryCount(0)
                    .build();
                batchProgressRepository.save(bp);
            }

            // StockInfo 미리 생성 (이름 없이)
            if (!stockInfoRepository.existsById(symbol)) {
                StockInfo info = StockInfo.builder()
                    .symbol(symbol)
                    .name(symbol)
                    .market("KR")
                    .sector("")
                    .build();
                stockInfoRepository.save(info);
            }
        }
        log.info("배치 초기화 완료: {}개 종목, {}~{}", symbols.size(), startDate, endDate);
    }

    // ─── 배치 실행 (비동기) ─────────────────────────────────────────────────

    @Async("batchExecutor")
    public void runBatch(String startDate, String endDate) {
        if (!batchRunning.compareAndSet(false, true)) {
            log.warn("배치가 이미 실행 중입니다.");
            return;
        }
        try {
            List<BatchProgress> pending = batchProgressRepository.findByBatchTypeAndStatusOrderByIdAsc(BATCH_TYPE, "PENDING");
            log.info("배치 시작: PENDING {}개 종목", pending.size());

            for (BatchProgress bp : pending) {
                String symbol = bp.getSymbol();
                bp.setStatus("IN_PROGRESS");
                batchProgressRepository.save(bp);

                try {
                    // KIS API 100행 한계 → 분기별(3개월) 분할 호출
                    LocalDate sDate = LocalDate.parse(startDate, LOCAL_DATE_FMT);
                    LocalDate eDate = LocalDate.parse(endDate, LOCAL_DATE_FMT);
                    LocalDate cursor = sDate;
                    while (!cursor.isAfter(eDate)) {
                        LocalDate chunkEnd = cursor.plusMonths(3).minusDays(1);
                        if (chunkEnd.isAfter(eDate)) chunkEnd = eDate;
                        collectDailyData(symbol, cursor.format(LOCAL_DATE_FMT), chunkEnd.format(LOCAL_DATE_FMT));
                        cursor = chunkEnd.plusDays(1);
                        if (!cursor.isAfter(eDate)) sleepSilently(500);
                    }
                    bp.setStatus("DONE");
                    bp.setLastCollectedDate(LocalDate.parse(endDate, LOCAL_DATE_FMT));
                    bp.setErrorMessage(null);
                    log.info("[{}] DONE", symbol);
                } catch (Exception e) {
                    bp.setRetryCount(bp.getRetryCount() + 1);
                    bp.setStatus("FAILED");
                    bp.setErrorMessage(e.getMessage());
                    log.error("[{}] FAILED: {}", symbol, e.getMessage());
                } finally {
                    batchProgressRepository.save(bp);
                }

                // 종목 간 1초 대기
                sleepSilently(1000);
            }
            log.info("배치 완료");
        } finally {
            batchRunning.set(false);
        }
    }

    // ─── 배치 상태 조회 ─────────────────────────────────────────────────────

    public Map<String, Object> getBatchStatus() {
        List<BatchProgress> all = batchProgressRepository.findByBatchTypeOrderByIdAsc(BATCH_TYPE);
        long pending     = all.stream().filter(b -> "PENDING".equals(b.getStatus())).count();
        long inProgress  = all.stream().filter(b -> "IN_PROGRESS".equals(b.getStatus())).count();
        long done        = all.stream().filter(b -> "DONE".equals(b.getStatus())).count();
        long failed      = all.stream().filter(b -> "FAILED".equals(b.getStatus())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("pending", pending);
        result.put("inProgress", inProgress);
        result.put("done", done);
        result.put("failed", failed);
        result.put("isRunning", batchRunning.get());
        result.put("details", all.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("symbol", b.getSymbol());
            m.put("status", b.getStatus());
            m.put("lastCollectedDate", b.getLastCollectedDate());
            m.put("retryCount", b.getRetryCount());
            m.put("errorMessage", b.getErrorMessage());
            return m;
        }).toList());
        return result;
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private BigDecimal parseBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        String s = val.toString().trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Long parseLong(Object val) {
        if (val == null) return 0L;
        String s = val.toString().trim();
        if (s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private void sleepSilently(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
