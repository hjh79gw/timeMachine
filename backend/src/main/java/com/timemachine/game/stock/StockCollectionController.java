package com.timemachine.game.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game/admin/batch")
@RequiredArgsConstructor
public class StockCollectionController {

    private final StockCollectionService stockCollectionService;

    /**
     * POST /api/game/admin/batch/init
     * Body: { "symbols": ["005930","000660",...], "startDate": "20220101", "endDate": "20241231" }
     */
    @PostMapping("/init")
    public ResponseEntity<?> init(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> symbols = (List<String>) body.get("symbols");
        String startDate = (String) body.get("startDate");
        String endDate   = (String) body.get("endDate");

        if (symbols == null || symbols.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbols 필요"));
        }
        if (startDate == null || endDate == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "startDate, endDate 필요"));
        }

        stockCollectionService.initBatch(symbols, startDate, endDate);
        return ResponseEntity.ok(Map.of(
            "message", "배치 초기화 완료",
            "symbols", symbols.size(),
            "startDate", startDate,
            "endDate", endDate
        ));
    }

    /**
     * POST /api/game/admin/batch/run
     * Body: { "startDate": "20220101", "endDate": "20241231" }
     */
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody Map<String, Object> body) {
        String startDate = (String) body.get("startDate");
        String endDate   = (String) body.get("endDate");

        if (startDate == null || endDate == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "startDate, endDate 필요"));
        }

        stockCollectionService.runBatch(startDate, endDate);
        return ResponseEntity.ok(Map.of("message", "배치 시작됨 (비동기)"));
    }

    /**
     * GET /api/game/admin/batch/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(stockCollectionService.getBatchStatus());
    }
}
