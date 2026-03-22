package com.timemachine.game.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/game/stocks")
@RequiredArgsConstructor
public class StockDataController {

    private final StockInfoRepository stockInfoRepository;
    private final StockDailyRepository stockDailyRepository;

    /**
     * GET /api/game/stocks/search?keyword=삼성
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String keyword) {
        String kw     = "%" + keyword + "%";
        String exact  = keyword;
        String starts = keyword + "%";
        List<StockInfo> list = stockInfoRepository.search(kw, exact, starts);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/game/stocks/{symbol}/daily?from=2022-01-01&to=2022-12-31
     */
    @GetMapping("/{symbol}/daily")
    public ResponseEntity<?> daily(
        @PathVariable String symbol,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<StockDaily> list = stockDailyRepository
            .findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/game/stocks/{symbol}/weekly?from=2022-01-01&to=2022-12-31
     */
    @GetMapping("/{symbol}/weekly")
    public ResponseEntity<?> weekly(
        @PathVariable String symbol,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<StockDaily> list = stockDailyRepository
            .findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
        return ResponseEntity.ok(aggregateCandles(list, java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
    }

    /**
     * GET /api/game/stocks/{symbol}/monthly?from=2022-01-01&to=2022-12-31
     */
    @GetMapping("/{symbol}/monthly")
    public ResponseEntity<?> monthly(
        @PathVariable String symbol,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<StockDaily> list = stockDailyRepository
            .findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
        return ResponseEntity.ok(aggregateCandles(list, java.time.temporal.ChronoField.MONTH_OF_YEAR));
    }

    private List<Map<String, Object>> aggregateCandles(
        List<StockDaily> list, java.time.temporal.TemporalField groupField
    ) {
        java.util.TreeMap<String, List<StockDaily>> grouped = new java.util.TreeMap<>();
        for (StockDaily d : list) {
            LocalDate date = d.getTradeDate();
            String key;
            if (groupField == java.time.temporal.ChronoField.MONTH_OF_YEAR) {
                key = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            } else {
                java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
                int week = date.get(wf.weekOfWeekBasedYear());
                int year = date.get(wf.weekBasedYear());
                key = year + "-" + String.format("%02d", week);
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (List<StockDaily> group : grouped.values()) {
            if (group.isEmpty()) continue;
            StockDaily first = group.get(0);
            StockDaily last  = group.get(group.size() - 1);

            BigDecimal open  = first.getOpenPrice() != null ? first.getOpenPrice() : first.getClosePrice();
            BigDecimal close = last.getClosePrice();
            BigDecimal high  = group.stream().map(StockDaily::getHighPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(close);
            BigDecimal low   = group.stream().map(StockDaily::getLowPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(close);
            long volume      = group.stream().mapToLong(d -> d.getVolume() != null ? d.getVolume() : 0L).sum();

            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("tradeDate", last.getTradeDate().toString());
            candle.put("openPrice",  open);
            candle.put("highPrice",  high);
            candle.put("lowPrice",   low);
            candle.put("closePrice", close);
            candle.put("volume",     volume);
            result.add(candle);
        }
        return result;
    }

    /**
     * GET /api/game/stocks/{symbol}/price?date=2022-06-15
     */
    @GetMapping("/{symbol}/price")
    public ResponseEntity<?> price(
        @PathVariable String symbol,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Optional<StockDaily> opt = stockDailyRepository.findBySymbolAndTradeDate(symbol, date);
        if (opt.isEmpty()) {
            opt = stockDailyRepository.findLatestOnOrBefore(symbol, date);
        }
        return opt.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/game/stocks/{symbol}/simulated-candles?date=2022-06-15
     */
    @GetMapping("/{symbol}/simulated-candles")
    public ResponseEntity<?> simulatedCandles(
        @PathVariable String symbol,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Optional<StockDaily> opt = stockDailyRepository.findBySymbolAndTradeDate(symbol, date);
        if (opt.isEmpty()) {
            opt = stockDailyRepository.findLatestOnOrBefore(symbol, date);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        StockDaily daily = opt.get();
        List<Map<String, Object>> candles = generateSimulatedCandles(symbol, date, daily);
        return ResponseEntity.ok(candles);
    }

    // ─── 시뮬레이션 분봉 생성 로직 ─────────────────────────────────────────

    private List<Map<String, Object>> generateSimulatedCandles(
        String symbol, LocalDate date, StockDaily daily
    ) {
        long seed = (symbol + date.toString()).hashCode();
        Random rng = new Random(seed);

        BigDecimal open  = daily.getOpenPrice();
        BigDecimal high  = daily.getHighPrice();
        BigDecimal low   = daily.getLowPrice();
        BigDecimal close = daily.getClosePrice();
        long totalVolume = daily.getVolume() != null ? daily.getVolume() : 0L;

        if (open == null || open.compareTo(BigDecimal.ZERO) == 0) {
            open = close;
        }
        if (high == null || high.compareTo(BigDecimal.ZERO) == 0) high = open.max(close);
        if (low  == null || low.compareTo(BigDecimal.ZERO) == 0)  low  = open.min(close);

        int totalMinutes = 390;
        List<Map<String, Object>> candles = new ArrayList<>();

        int highIdx = (int)(Math.abs(rng.nextLong()) % (totalMinutes - 20)) + 10;
        int lowIdx  = (int)(Math.abs(rng.nextLong()) % (totalMinutes - 20)) + 10;
        if (highIdx == lowIdx) lowIdx = (lowIdx + 30) % totalMinutes;

        double openD  = open.doubleValue();
        double highD  = high.doubleValue();
        double lowD   = low.doubleValue();
        double closeD = close.doubleValue();

        double[] prices = interpolatePrices(openD, highD, lowD, closeD, highIdx, lowIdx, totalMinutes, rng);

        for (int i = 0; i < totalMinutes; i++) {
            int hour   = 9 + (i / 60);
            int minute = i % 60;
            String time = String.format("%02d:%02d", hour, minute);

            double candleClose = prices[i];
            double candleOpen  = (i == 0) ? openD : prices[i - 1];

            double range    = (highD - lowD) * 0.005;
            double noiseHigh = Math.abs(rng.nextGaussian()) * range;
            double noiseLow  = Math.abs(rng.nextGaussian()) * range;

            double candleHigh = Math.max(candleOpen, candleClose) + noiseHigh;
            double candleLow  = Math.min(candleOpen, candleClose) - noiseLow;

            candleHigh = Math.min(candleHigh, highD);
            candleLow  = Math.max(candleLow,  lowD);

            double volFactor = 0.5 + Math.abs(rng.nextGaussian()) * 0.3;
            long vol = (long)(totalVolume / totalMinutes * volFactor);

            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("time",   time);
            candle.put("open",   roundPrice(candleOpen));
            candle.put("high",   roundPrice(candleHigh));
            candle.put("low",    roundPrice(candleLow));
            candle.put("close",  roundPrice(candleClose));
            candle.put("volume", vol);
            candles.add(candle);
        }

        return candles;
    }

    private double[] interpolatePrices(
        double open, double high, double low, double close,
        int highIdx, int lowIdx, int total, Random rng
    ) {
        double[] prices = new double[total];

        int[] anchors;
        double[] anchorVals;

        if (highIdx < lowIdx) {
            anchors    = new int[]   {0,      highIdx, lowIdx,  total - 1};
            anchorVals = new double[]{open,   high,    low,     close};
        } else {
            anchors    = new int[]   {0,      lowIdx,  highIdx, total - 1};
            anchorVals = new double[]{open,   low,     high,    close};
        }

        double noiseScale = (high - low) * 0.003;
        for (int seg = 0; seg < anchors.length - 1; seg++) {
            int   startIdx = anchors[seg];
            int   endIdx   = anchors[seg + 1];
            double startV  = anchorVals[seg];
            double endV    = anchorVals[seg + 1];
            int    segLen  = endIdx - startIdx;

            for (int i = startIdx; i < endIdx; i++) {
                double t = (double)(i - startIdx) / segLen;
                double base = startV + (endV - startV) * t;
                double noise = rng.nextGaussian() * noiseScale;
                prices[i] = base + noise;
            }
        }
        prices[total - 1] = close;
        return prices;
    }

    private double roundPrice(double price) {
        return Math.round(price);
    }
}
