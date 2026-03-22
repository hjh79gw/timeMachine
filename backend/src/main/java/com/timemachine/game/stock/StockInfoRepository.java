package com.timemachine.game.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {

    @Query("""
        SELECT s FROM StockInfo s
        WHERE s.symbol LIKE :kw OR s.name LIKE :kw
        ORDER BY
          CASE WHEN s.name LIKE :exact THEN 0
               WHEN s.name LIKE :starts THEN 1
               ELSE 2 END,
          s.name
        LIMIT 50
    """)
    List<StockInfo> search(
        @Param("kw") String kw,
        @Param("exact") String exact,
        @Param("starts") String starts
    );
}
