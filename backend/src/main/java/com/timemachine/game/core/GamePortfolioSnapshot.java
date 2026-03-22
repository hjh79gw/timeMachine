package com.timemachine.game.core;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "game_portfolio_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamePortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turn_id", nullable = false)
    private Long turnId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column
    private Long cash;

    @Column(name = "holdings_value")
    private Long holdingsValue;

    @Column(name = "total_asset")
    private Long totalAsset;

    @Column(name = "profit_rate", precision = 10, scale = 4)
    private BigDecimal profitRate;

    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;
}
