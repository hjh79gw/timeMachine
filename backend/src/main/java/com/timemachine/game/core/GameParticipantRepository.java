package com.timemachine.game.core;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {
    Optional<GameParticipant> findByGameIdAndUserId(Long gameId, Long userId);
    List<GameParticipant> findByGameIdOrderByRankNumAsc(Long gameId);
    List<GameParticipant> findByGameId(Long gameId);
    List<GameParticipant> findByUserId(Long userId);
}
