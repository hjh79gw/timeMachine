package com.timemachine.game.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    @Value("${naver.client-id:}")
    private String naverClientId;

    @Value("${naver.client-secret:}")
    private String naverClientSecret;

    private final NewsArchiveRepository newsArchiveRepository;

    private static final String NAVER_NEWS_URL = "https://openapi.naver.com";
    private static final int MAX_NEWS_PER_KEYWORD = 5;

    /**
     * 네이버 뉴스 API 호출 후 DB 저장 (온디맨드)
     * 키워드당 최대 5건
     */
    @Transactional
    public List<NewsArchive> fetchAndSaveNews(String keyword, LocalDate from, LocalDate to) {
        if (naverClientId == null || naverClientId.isBlank()) {
            log.warn("네이버 API 키가 설정되지 않았습니다.");
            return List.of();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = WebClient.create(NAVER_NEWS_URL)
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1/search/news.json")
                    .queryParam("query", keyword)
                    .queryParam("display", MAX_NEWS_PER_KEYWORD)
                    .queryParam("sort", "date")
                    .build())
                .header("X-Naver-Client-Id", naverClientId)
                .header("X-Naver-Client-Secret", naverClientSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null) {
                log.warn("[뉴스] 응답 null: keyword={}", keyword);
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null || items.isEmpty()) {
                log.info("[뉴스] 결과 없음: keyword={}", keyword);
                return List.of();
            }

            return items.stream()
                .map(item -> saveNewsItemIfInRange(item, keyword, from, to))
                .filter(n -> n != null)
                .toList();

        } catch (Exception e) {
            log.error("[뉴스] 조회 실패: keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    private NewsArchive saveNewsItemIfInRange(Map<String, Object> item, String keyword, LocalDate from, LocalDate to) {
        String pubDateStr = (String) item.getOrDefault("pubDate", "");
        LocalDateTime publishedAt = parseNaverDate(pubDateStr);
        LocalDate pubDate = publishedAt.toLocalDate();
        if (pubDate.isBefore(from) || pubDate.isAfter(to)) {
            log.debug("[뉴스] 날짜 범위 제외: pubDate={}, from={}, to={}", pubDate, from, to);
            return null;
        }
        return saveNewsItem(item, keyword, publishedAt);
    }

    private NewsArchive saveNewsItem(Map<String, Object> item, String keyword, LocalDateTime publishedAt) {
        String link        = cleanHtml((String) item.getOrDefault("link", ""));
        String originalLink = cleanHtml((String) item.getOrDefault("originallink", link));
        String url         = originalLink.isBlank() ? link : originalLink;

        if (url.isBlank()) return null;

        if (newsArchiveRepository.existsByOriginalUrl(url)) {
            return newsArchiveRepository.findByOriginalUrl(url).orElse(null);
        }

        String title       = cleanHtml((String) item.getOrDefault("title", ""));
        String description = cleanHtml((String) item.getOrDefault("description", ""));
        String summary = description.isBlank() ? title : title + "\n" + description;

        NewsArchive news = NewsArchive.builder()
            .title(title)
            .summary(summary)
            .source("naver")
            .originalUrl(url)
            .publishedAt(publishedAt)
            .category("경제")
            .keyword(keyword)
            .build();

        try {
            return newsArchiveRepository.save(news);
        } catch (Exception e) {
            log.debug("[뉴스] 중복 저장 무시: url={}", url);
            return null;
        }
    }

    /**
     * DB에서 date 기준 daysBefore일 전 뉴스 조회
     */
    public List<NewsArchive> getNewsForDate(LocalDate date, int daysBefore) {
        LocalDate from = date.minusDays(daysBefore);
        return newsArchiveRepository.findByPublishedDateBetween(from, date);
    }

    /**
     * 해당 날짜 뉴스 DB에 있는지 확인
     */
    public boolean hasNewsForDate(LocalDate date) {
        return newsArchiveRepository.existsByPublishedDate(date);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private String cleanHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").trim();
    }

    private LocalDateTime parseNaverDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return LocalDateTime.now();
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH);
            return LocalDateTime.parse(pubDate, fmt);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
