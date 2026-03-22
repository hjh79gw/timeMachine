package com.timemachine.game.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsArchiveRepository extends JpaRepository<NewsArchive, Long> {

    Optional<NewsArchive> findByOriginalUrl(String originalUrl);

    boolean existsByOriginalUrl(String originalUrl);

    @Query("SELECT n FROM NewsArchive n WHERE n.publishedAt BETWEEN :from AND :to ORDER BY n.publishedAt DESC")
    List<NewsArchive> findByDateRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(n) > 0 FROM NewsArchive n WHERE DATE(n.publishedAt) = :date")
    boolean existsByPublishedDate(@Param("date") LocalDate date);

    @Query("SELECT n FROM NewsArchive n WHERE DATE(n.publishedAt) >= :from AND DATE(n.publishedAt) <= :to ORDER BY n.publishedAt DESC")
    List<NewsArchive> findByPublishedDateBetween(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
