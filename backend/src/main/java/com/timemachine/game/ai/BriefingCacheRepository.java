package com.timemachine.game.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface BriefingCacheRepository extends JpaRepository<BriefingCache, Long> {
    Optional<BriefingCache> findByTargetDate(LocalDate targetDate);
}
