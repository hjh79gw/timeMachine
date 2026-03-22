package com.timemachine.game.core;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "game_holding",
    uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "user_id", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 20, nullable = false)
    private String symbol;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "avg_buy_price", precision = 18, scale = 2)
    private BigDecimal avgBuyPrice;

    public void buy(int qty, BigDecimal price) {
        BigDecimal totalCost = this.avgBuyPrice.multiply(BigDecimal.valueOf(this.quantity))
            .add(price.multiply(BigDecimal.valueOf(qty)));
        this.quantity += qty;
        this.avgBuyPrice = totalCost.divide(BigDecimal.valueOf(this.quantity), 2, java.math.RoundingMode.HALF_UP);
    }

    public void sell(int qty) {
        this.quantity -= qty;
    }
}
