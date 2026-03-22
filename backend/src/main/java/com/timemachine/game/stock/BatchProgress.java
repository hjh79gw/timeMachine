package com.timemachine.game.stock;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_batch_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "batch_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String symbol;

    @Column(name = "batch_type", length = 20, nullable = false)
    private String batchType;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING / IN_PROGRESS / DONE / FAILED

    @Column(name = "last_collected_date")
    private LocalDate lastCollectedDate;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
