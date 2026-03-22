package com.timemachine.kis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisStockService {

    @Value("${kis.base-url}")
    private String baseUrl;

    private final KisTokenService tokenService;

    // 국내 주식 현재가 조회
    public StockPriceDto getCurrentPrice(String stockCode) {
        try {
            var response = WebClient.create(baseUrl)
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .build())
                .header("authorization", "Bearer " + tokenService.getAccessToken())
                .header("appkey", tokenService.getAppKey())
                .header("appsecret", tokenService.getAppSecret())
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null) return null;

            Map<String, Object> output = (Map<String, Object>) response.get("output");
            if (output == null) return null;

            String currentPrice = (String) output.get("stck_prpr");   // 현재가
            String stockName    = (String) output.get("hts_kor_isnm"); // 종목명
            String change       = (String) output.get("prdy_vrss");    // 전일 대비
            String changeRate   = (String) output.get("prdy_ctrt");    // 등락률
            String sign         = (String) output.get("prdy_vrss_sign"); // 1:상한 2:상승 3:보합 4:하한 5:하락

            return StockPriceDto.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(new BigDecimal(currentPrice))
                .change(new BigDecimal(change))
                .changeRate(new BigDecimal(changeRate))
                .sign(sign)
                .build();

        } catch (Exception e) {
            log.error("현재가 조회 실패 [{}]: {}", stockCode, e.getMessage());
            return null;
        }
    }

    // 국내 주식 일봉 차트 조회
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getChartData(String stockCode, String period) {
        // period: D(일), W(주), M(월)
        String endDate   = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String startDate = java.time.LocalDate.now().minusYears(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        try {
            var response = WebClient.create(baseUrl)
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_DATE_1", startDate)
                    .queryParam("FID_INPUT_DATE_2", endDate)
                    .queryParam("FID_PERIOD_DIV_CODE", period)
                    .queryParam("FID_ORG_ADJ_PRC", "0")
                    .build())
                .header("authorization", "Bearer " + tokenService.getAccessToken())
                .header("appkey", tokenService.getAppKey())
                .header("appsecret", tokenService.getAppSecret())
                .header("tr_id", "FHKST03010100")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null) return List.of();
            List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.get("output2");
            if (output2 == null) return List.of();

            return output2.stream().map(item -> {
                Map<String, Object> candle = new java.util.LinkedHashMap<>();
                candle.put("date",   item.get("stck_bsop_date")); // YYYYMMDD
                candle.put("open",   item.get("stck_oprc"));
                candle.put("high",   item.get("stck_hgpr"));
                candle.put("low",    item.get("stck_lwpr"));
                candle.put("close",  item.get("stck_clpr"));
                candle.put("volume", item.get("acml_vol"));
                return candle;
            }).toList();

        } catch (Exception e) {
            log.error("차트 조회 실패 [{}]: {}", stockCode, e.getMessage());
            return List.of();
        }
    }
}
