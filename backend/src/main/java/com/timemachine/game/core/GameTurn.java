package com.timemachine.game.core;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_turn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "turn_number")
    private Integer turnNumber;

    @Column(name = "cur_date")
    private LocalDate currentDate;

    @Column(name = "ai_briefing", columnDefinition = "TEXT")
    private String aiBriefing;

    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE / COMPLETED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
