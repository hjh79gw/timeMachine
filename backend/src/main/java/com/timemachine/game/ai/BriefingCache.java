package com.timemachine.game.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_briefing_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BriefingCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_date", unique = true, nullable = false)
    private LocalDate targetDate;

    @Column(name = "briefing_text", columnDefinition = "TEXT")
    private String briefingText;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
