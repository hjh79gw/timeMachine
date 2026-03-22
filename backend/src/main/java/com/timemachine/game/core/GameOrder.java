package com.timemachine.game.core;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turn_id", nullable = false)
    private Long turnId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 20)
    private String symbol;

    @Column(name = "order_type", length = 10)
    private String orderType; // BUY / SELL

    @Column(name = "price_type", length = 10)
    private String priceType; // MARKET / LIMIT

    @Column(name = "order_price", precision = 18, scale = 2)
    private BigDecimal orderPrice;

    @Column(name = "executed_price", precision = 18, scale = 2)
    private BigDecimal executedPrice;

    @Column
    private Integer quantity;

    @Column(precision = 18, scale = 4)
    private BigDecimal commission;

    @Column(precision = 18, scale = 4)
    private BigDecimal tax;

    @Column(length = 20)
    @Builder.Default
    private String status = "EXECUTED"; // EXECUTED / REJECTED

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    @PrePersist
    void prePersist() { orderedAt = LocalDateTime.now(); }
}
