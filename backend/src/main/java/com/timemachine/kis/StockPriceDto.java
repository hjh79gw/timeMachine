package com.timemachine.kis;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@Builder
public class StockPriceDto {
    private String stockCode;
    private String stockName;
    private BigDecimal currentPrice;
    private BigDecimal change;       // 전일 대비 금액
    private BigDecimal changeRate;   // 등락률
    private String sign;             // 2:상승, 3:보합, 5:하락
}
