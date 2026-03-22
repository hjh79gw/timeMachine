package com.timemachine.game.ai;

import com.timemachine.game.core.GameTurn;
import com.timemachine.game.core.GameTurnRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game/turns")
@RequiredArgsConstructor
public class AiController {

    private final BriefingService briefingService;
    private final GameTurnRepository gameTurnRepository;

    /**
     * GET /api/game/turns/{turnId}/briefing
     * 브리핑 조회 (없으면 생성)
     */
    @GetMapping("/{turnId}/briefing")
    public ResponseEntity<?> getBriefing(
        @PathVariable Long turnId,
        HttpSession session
    ) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));
        }

        GameTurn turn = gameTurnRepository.findById(turnId)
            .orElse(null);
        if (turn == null) {
            return ResponseEntity.notFound().build();
        }

        // 캐시된 브리핑이 있으면 즉시 반환
        if (turn.getAiBriefing() != null && !turn.getAiBriefing().isBlank()) {
            return ResponseEntity.ok(Map.of(
                "turnId", turnId,
                "date", turn.getCurrentDate(),
                "briefing", turn.getAiBriefing()
            ));
        }

        // 없으면 생성 (온디맨드)
        String briefing = briefingService.getBriefingForDate(turn.getCurrentDate());

        // 턴에 브리핑 저장
        turn.setAiBriefing(briefing);
        gameTurnRepository.save(turn);

        return ResponseEntity.ok(Map.of(
            "turnId", turnId,
            "date", turn.getCurrentDate(),
            "briefing", briefing
        ));
    }
}
