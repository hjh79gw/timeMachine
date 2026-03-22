package com.timemachine.game.core;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameOrderRepository extends JpaRepository<GameOrder, Long> {
    List<GameOrder> findByTurnIdAndUserId(Long turnId, Long userId);
    List<GameOrder> findByTurnId(Long turnId);
    List<GameOrder> findByTurnIdInAndUserId(List<Long> turnIds, Long userId);
}
