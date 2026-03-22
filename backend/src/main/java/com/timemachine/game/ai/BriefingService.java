package com.timemachine.game.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    private final BriefingCacheRepository briefingCacheRepository;
    private final NewsService newsService;
    private final NaverFinanceCrawlerService crawlerService;

    private static final String OPENAI_API_URL = "https://api.openai.com";

    /**
     * 특정 날짜의 브리핑 조회 또는 생성
     * 1. briefing_cache 먼저 조회
     * 2. 없으면: GPT API로 해당 시점 시장 상황 생성 → 캐시 저장
     */
    @Transactional
    public String getBriefingForDate(LocalDate date) {
        // 캐시 조회
        Optional<BriefingCache> cached = briefingCacheRepository.findByTargetDate(date);
        if (cached.isPresent()) {
            log.debug("[브리핑] 캐시 히트: date={}", date);
            return cached.get().getBriefingText();
        }

        log.info("[브리핑] 생성 시작: date={}", date);

        // 섹터별 뉴스 크롤링 (DB에 없으면)
        List<NewsArchive> news = crawlerService.crawlBySectors(date);
        log.info("[브리핑] 수집된 뉴스: {}건 (date={})", news.size(), date);

        String briefingText = generateBriefing(date, news);

        // 캐시 저장
        BriefingCache cache = BriefingCache.builder()
            .targetDate(date)
            .briefingText(briefingText)
            .build();
        briefingCacheRepository.save(cache);

        return briefingText;
    }

    /**
     * GPT API 호출로 브리핑 생성
     */
    private String generateBriefing(LocalDate date, List<NewsArchive> newsList) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.warn("[브리핑] OpenAI API 키 미설정, 기본 브리핑 반환");
            return generateDefaultBriefing(date, newsList);
        }

        List<NewsArchive> limited = newsList.stream().limit(30).toList();
        String newsContext = java.util.stream.IntStream.range(0, limited.size())
            .mapToObj(i -> {
                NewsArchive n = limited.get(i);
                String s = n.getSummary();
                boolean hasSummary = s != null && !s.isBlank() && !s.equals(n.getTitle());
                String body = hasSummary ? s : "";
                String sector = n.getCategory() != null ? n.getCategory() : "기타";
                return "[" + sector + "] 제목: " + n.getTitle()
                    + "\n    URL: " + n.getOriginalUrl()
                    + (body.isBlank() ? "" : "\n    내용: " + body);
            })
            .collect(Collectors.joining("\n"));

        log.info("[브리핑] GPT에 전달할 뉴스 {}건:\n{}", newsList.size(), newsContext);

        String prompt = buildPrompt(date, newsContext);

        try {
            Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                    Map.of("role", "system", "content",
                        "당신은 주식 투자 시뮬레이션 게임의 시장 동향 해설자 '나비'입니다. " +
                        "제공된 뉴스 기사 내용만을 근거로 시장 동향을 분석하세요. " +
                        "당신의 사전 학습 지식이나 기사에 없는 정보는 절대 사용하지 마세요. " +
                        "이모티콘을 사용하지 마세요."),
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 1500,
                "temperature", 0.7
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = WebClient.create(OPENAI_API_URL)
                .post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null) return generateDefaultBriefing(date, newsList);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return generateDefaultBriefing(date, newsList);

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) return generateDefaultBriefing(date, newsList);

            return (String) message.get("content");

        } catch (Exception e) {
            log.error("[브리핑] GPT API 오류: {}", e.getMessage());
            return generateDefaultBriefing(date, newsList);
        }
    }

    private String buildPrompt(LocalDate date, String newsContext) {
        return String.format("""
            아래는 %s 날짜의 금융 뉴스 기사들입니다.

            [뉴스 기사]
            %s

            규칙:
            - 위 기사들의 내용만 근거로 분석하세요.
            - 기사에 없는 내용을 추측하거나 덧붙이지 마세요.
            - 당신이 알고 있는 사전 지식은 절대 사용하지 마세요.
            - 각 주요 이슈 끝에 해당 기사의 URL을 괄호 안에 넣으세요.
            - 번호 표기("기사 1" 등)는 절대 사용하지 마세요.
            - 이모티콘을 사용하지 마세요.

            반드시 아래 형식으로 응답하세요:

            [시장 개요] (4~5문장, 전체 시장 동향을 구체적 수치와 함께 상세히 요약)
            [주요 이슈]
            - 가장 영향력 있는 섹터/이슈 (https://...)
            - 두 번째 영향력 있는 섹터/이슈 (https://...)
            - 세 번째 영향력 있는 섹터/이슈 (https://...)
            [투자 힌트] (1~2문장, 특정 종목명 언급 금지)

            주요 이슈는 기사들을 종합 분석하여 시장에 가장 영향력이 큰 3개만 선별하세요.
            """,
            date.toString(),
            newsContext
        );
    }

    private String generateDefaultBriefing(LocalDate date, List<NewsArchive> newsList) {
        if (newsList.isEmpty()) {
            return String.format("[%s 시장 브리핑]\n이 날짜의 뉴스 데이터가 없습니다. 주식 차트와 거래 데이터를 직접 분석하여 투자 결정을 내려보세요.", date);
        }

        String headlines = newsList.stream()
            .limit(3)
            .map(n -> "• " + n.getTitle())
            .collect(Collectors.joining("\n"));

        return String.format("[%s 시장 브리핑]\n\n주요 뉴스:\n%s\n\n차트와 거래량을 참고하여 신중하게 투자 결정을 내리세요.", date, headlines);
    }
}
