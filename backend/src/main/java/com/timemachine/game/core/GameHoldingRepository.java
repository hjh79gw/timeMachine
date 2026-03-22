package com.timemachine.game.core;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameHoldingRepository extends JpaRepository<GameHolding, Long> {
    List<GameHolding> findByGameIdAndUserId(Long gameId, Long userId);
    Optional<GameHolding> findByGameIdAndUserIdAndSymbol(Long gameId, Long userId, String symbol);
}
