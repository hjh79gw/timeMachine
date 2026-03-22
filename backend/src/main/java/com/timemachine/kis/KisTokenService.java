package com.timemachine.kis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisTokenService {

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    @Value("${kis.base-url}")
    private String baseUrl;

    private String accessToken;

    // 앱 시작 시 토큰 발급
    @jakarta.annotation.PostConstruct
    public void init() {
        issueToken();
    }

    // 매일 오전 1시 갱신 (KIS 토큰 유효기간 24시간)
    @Scheduled(cron = "0 0 1 * * *")
    public void refresh() {
        issueToken();
    }

    private void issueToken() {
        try {
            var body = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "appsecret", appSecret
            );

            var response = WebClient.create(baseUrl)
                .post()
                .uri("/oauth2/tokenP")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && response.containsKey("access_token")) {
                this.accessToken = (String) response.get("access_token");
                log.info("KIS Access Token 발급 완료");
            }
        } catch (Exception e) {
            log.error("KIS Access Token 발급 실패: {}", e.getMessage());
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getAppKey() { return appKey; }
    public String getAppSecret() { return appSecret; }
}
