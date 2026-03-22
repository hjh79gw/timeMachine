package com.timemachine.game.core;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "seed_money")
    private Long seedMoney;

    @Column(name = "game_duration", length = 10)
    private String gameDuration; // 3M / 6M / 1Y

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "move_days")
    private Integer moveDays;

    @Column(length = 10)
    private String difficulty; // EASY / HARD

    @Column(name = "cur_date")
    private LocalDate currentDate;

    @Column(length = 20)
    @Builder.Default
    private String status = "IN_PROGRESS"; // IN_PROGRESS / FINISHED

    @Column(name = "final_analysis", columnDefinition = "TEXT")
    private String finalAnalysis;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
