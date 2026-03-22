package com.timemachine.game.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockDailyRepository extends JpaRepository<StockDaily, Long> {

    List<StockDaily> findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
        String symbol, LocalDate from, LocalDate to);

    Optional<StockDaily> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    @Query("SELECT d FROM StockDaily d WHERE d.symbol = :symbol AND d.tradeDate <= :date ORDER BY d.tradeDate DESC LIMIT 1")
    Optional<StockDaily> findLatestOnOrBefore(@Param("symbol") String symbol, @Param("date") LocalDate date);

    @Query("SELECT MIN(d.tradeDate) FROM StockDaily d")
    Optional<LocalDate> findMinTradeDate();

    @Query("SELECT MAX(d.tradeDate) FROM StockDaily d")
    Optional<LocalDate> findMaxTradeDate();

    @Query("SELECT DISTINCT d.tradeDate FROM StockDaily d ORDER BY d.tradeDate ASC")
    List<LocalDate> findAllTradeDates();

    @Modifying
    @Query(value = """
        INSERT INTO game_stock_daily (symbol, trade_date, open_price, high_price, low_price, close_price, volume, change_rate)
        VALUES (:symbol, :tradeDate, :openPrice, :highPrice, :lowPrice, :closePrice, :volume, :changeRate)
        ON DUPLICATE KEY UPDATE
            open_price = VALUES(open_price),
            high_price = VALUES(high_price),
            low_price = VALUES(low_price),
            close_price = VALUES(close_price),
            volume = VALUES(volume),
            change_rate = VALUES(change_rate)
    """, nativeQuery = true)
    void upsert(
        @Param("symbol") String symbol,
        @Param("tradeDate") LocalDate tradeDate,
        @Param("openPrice") java.math.BigDecimal openPrice,
        @Param("highPrice") java.math.BigDecimal highPrice,
        @Param("lowPrice") java.math.BigDecimal lowPrice,
        @Param("closePrice") java.math.BigDecimal closePrice,
        @Param("volume") Long volume,
        @Param("changeRate") java.math.BigDecimal changeRate
    );

    boolean existsBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
