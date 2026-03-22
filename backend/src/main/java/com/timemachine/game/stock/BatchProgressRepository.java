package com.timemachine.game.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BatchProgressRepository extends JpaRepository<BatchProgress, Long> {

    Optional<BatchProgress> findBySymbolAndBatchType(String symbol, String batchType);

    List<BatchProgress> findByBatchTypeAndStatusOrderByIdAsc(String batchType, String status);

    List<BatchProgress> findByBatchTypeOrderByIdAsc(String batchType);

    long countByBatchTypeAndStatus(String batchType, String status);
}
