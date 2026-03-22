package com.timemachine.game.core;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_participant",
    uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "current_cash", nullable = false)
    private Long currentCash;

    @Column(name = "total_asset")
    private Long totalAsset;

    @Column(name = "total_profit_rate", precision = 10, scale = 4)
    private BigDecimal totalProfitRate;

    @Column(name = "rank_num")
    private Integer rankNum;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @PrePersist
    void prePersist() { joinedAt = LocalDateTime.now(); }
}
