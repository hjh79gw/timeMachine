package com.timemachine.game.core;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GamePortfolioSnapshotRepository extends JpaRepository<GamePortfolioSnapshot, Long> {
    List<GamePortfolioSnapshot> findByTurnIdAndUserId(Long turnId, Long userId);

    // 게임의 모든 포트폴리오 히스토리 (turn을 통해)
    List<GamePortfolioSnapshot> findByUserIdOrderBySnapshotDateAsc(Long userId);
}
