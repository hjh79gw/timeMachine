package com.timemachine.game.core;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameTurnRepository extends JpaRepository<GameTurn, Long> {
    List<GameTurn> findByGameIdOrderByTurnNumberAsc(Long gameId);
    Optional<GameTurn> findByGameIdAndStatus(Long gameId, String status);
    Optional<GameTurn> findTopByGameIdOrderByTurnNumberDesc(Long gameId);
}
