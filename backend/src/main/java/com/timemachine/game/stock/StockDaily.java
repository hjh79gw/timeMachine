package com.timemachine.game.stock;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "game_stock_daily",
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", precision = 18, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 18, scale = 2)
    private BigDecimal closePrice;

    @Column
    private Long volume;

    @Column(name = "change_rate", precision = 8, scale = 2)
    private BigDecimal changeRate;
}
