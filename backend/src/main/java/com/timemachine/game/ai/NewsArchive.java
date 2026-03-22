package com.timemachine.game.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_news_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 100)
    private String source;

    @Column(name = "original_url", length = 1000, unique = true)
    private String originalUrl;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(length = 50)
    private String category;

    @Column(length = 100)
    private String keyword;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
