package com.timemachine.game.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverFinanceCrawlerService {

    private final NewsArchiveRepository newsArchiveRepository;

    private static final String BASE_URL = "https://finance.naver.com/news/mainnews.naver";
    private static final String SEARCH_URL = "https://finance.naver.com/news/news_search.naver";
    private static final int MAX_ARTICLES = 10;
    private static final int ARTICLES_PER_SECTOR = 3;
    private static final long CRAWL_DELAY_MS = 400;

    private static final List<String[]> SECTORS = List.of(
        new String[]{"반도체", "반도체"},
        new String[]{"IT", "IT 소프트웨어"},
        new String[]{"바이오", "바이오 제약"},
        new String[]{"2차전지", "2차전지 배터리"},
        new String[]{"금융", "은행 금융"},
        new String[]{"자동차", "자동차"},
        new String[]{"에너지", "에너지 화학"},
        new String[]{"유통", "유통 소비재"},
        new String[]{"건설", "건설 부동산"},
        new String[]{"엔터", "엔터 미디어"}
    );

    /**
     * 특정 날짜 네이버 금융 증권 뉴스 크롤링 후 DB 저장
     * DB에 해당 날짜 뉴스가 이미 있으면 스킵
     */
    @Transactional
    public List<NewsArchive> crawlAndSave(LocalDate date) {
        if (newsArchiveRepository.existsByPublishedDate(date)) {
            log.debug("[크롤링] 이미 존재: date={}", date);
            return newsArchiveRepository.findByPublishedDateBetween(date, date);
        }

        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD
        String url = BASE_URL + "?date=" + dateStr;

        try {
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10_000)
                .get();

            // 주요뉴스 본문 영역 (속보 ticker 제외)
            Elements links = doc.select("div.mainNewsList dd.articleSubject a");

            log.info("[크롤링] date={}, 기사 {}건 발견", date, links.size());

            List<NewsArchive> saved = new ArrayList<>();
            int count = 0;
            for (Element link : links) {
                if (count >= MAX_ARTICLES) break;

                String title = link.text().trim();
                if (title.isBlank()) continue;

                String href = link.absUrl("href");
                if (href.isBlank()) href = "https://finance.naver.com" + link.attr("href");
                if (href.isBlank()) continue;

                if (newsArchiveRepository.existsByOriginalUrl(href)) {
                    newsArchiveRepository.findByOriginalUrl(href).ifPresent(saved::add);
                    count++;
                    continue;
                }

                String summary = fetchArticleBody(href, title);

                NewsArchive news = NewsArchive.builder()
                    .title(title)
                    .summary(summary)
                    .source("naver_finance")
                    .originalUrl(href)
                    .publishedAt(date.atTime(9, 0))
                    .category("증권")
                    .keyword("증권")
                    .build();

                try {
                    saved.add(newsArchiveRepository.save(news));
                    count++;
                } catch (Exception e) {
                    log.debug("[크롤링] 중복 무시: url={}", href);
                }
            }

            log.info("[크롤링] date={}, {}건 저장", date, saved.size());
            return saved;

        } catch (Exception e) {
            log.error("[크롤링] 실패: date={}, error={}", date, e.getMessage());
            return List.of();
        }
    }

    /**
     * 섹터별 키워드 검색으로 뉴스 크롤링 (10섹터 × 3건 = 30건)
     */
    @Transactional
    public List<NewsArchive> crawlBySectors(LocalDate date) {
        // 이미 해당 날짜에 섹터별 뉴스가 있으면 스킵
        List<NewsArchive> existing = newsArchiveRepository.findByPublishedDateBetween(date, date);
        if (existing.size() >= 20) {
            log.debug("[섹터크롤링] 이미 충분한 뉴스 존재: date={}, {}건", date, existing.size());
            return existing;
        }

        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<NewsArchive> allSaved = new ArrayList<>();

        for (String[] sector : SECTORS) {
            String sectorName = sector[0];
            String keyword = sector[1];

            try {
                String searchUrl = SEARCH_URL + "?q=" + java.net.URLEncoder.encode(keyword, "EUC-KR")
                    + "&pd=4&stDateStart=" + dateStr + "&stDateEnd=" + dateStr;

                Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(10_000)
                    .get();

                Elements links = doc.select("dd.articleSubject a");
                log.info("[섹터크롤링] sector={}, date={}, 기사 {}건 발견", sectorName, date, links.size());

                int count = 0;
                for (Element link : links) {
                    if (count >= ARTICLES_PER_SECTOR) break;

                    String title = link.text().trim();
                    if (title.isBlank()) continue;

                    String href = link.absUrl("href");
                    if (href.isBlank()) href = "https://finance.naver.com" + link.attr("href");
                    if (href.isBlank()) continue;

                    if (newsArchiveRepository.existsByOriginalUrl(href)) {
                        newsArchiveRepository.findByOriginalUrl(href).ifPresent(allSaved::add);
                        count++;
                        continue;
                    }

                    Thread.sleep(CRAWL_DELAY_MS);
                    String summary = fetchArticleBody(href, title);

                    NewsArchive news = NewsArchive.builder()
                        .title(title)
                        .summary(summary)
                        .source("naver_finance")
                        .originalUrl(href)
                        .publishedAt(date.atTime(9, 0))
                        .category(sectorName)
                        .keyword(keyword)
                        .build();

                    try {
                        allSaved.add(newsArchiveRepository.save(news));
                        count++;
                    } catch (Exception e) {
                        log.debug("[섹터크롤링] 중복 무시: url={}", href);
                    }
                }

                Thread.sleep(CRAWL_DELAY_MS);

            } catch (Exception e) {
                log.error("[섹터크롤링] 실패: sector={}, date={}, error={}", sectorName, date, e.getMessage());
            }
        }

        log.info("[섹터크롤링] date={}, 총 {}건 저장", date, allSaved.size());
        return allSaved;
    }

    /**
     * 기사 본문 크롤링
     */
    private String fetchArticleBody(String articleUrl, String fallback) {
        try {
            String naverNewsUrl = convertToNaverNewsUrl(articleUrl);

            Document doc = Jsoup.connect(naverNewsUrl)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .timeout(8_000)
                .get();

            Element body = doc.selectFirst("#dic_area");
            if (body == null) body = doc.selectFirst(".newsct_article");
            if (body == null) return fallback;

            return body.text().trim();

        } catch (Exception e) {
            log.debug("[크롤링] 본문 조회 실패: url={}", articleUrl);
            return fallback;
        }
    }

    /**
     * /news/news_read.naver?article_id=X&office_id=Y → https://n.news.naver.com/mnews/article/Y/X
     */
    private String convertToNaverNewsUrl(String url) {
        try {
            String articleId = extractParam(url, "article_id");
            String officeId = extractParam(url, "office_id");
            if (!articleId.isBlank() && !officeId.isBlank()) {
                return "https://n.news.naver.com/mnews/article/" + officeId + "/" + articleId;
            }
        } catch (Exception ignored) {}
        return url;
    }

    private String extractParam(String url, String param) {
        int idx = url.indexOf(param + "=");
        if (idx < 0) return "";
        int start = idx + param.length() + 1;
        int end = url.indexOf("&", start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }
}
