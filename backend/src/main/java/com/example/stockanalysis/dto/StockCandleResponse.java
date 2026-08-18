package com.example.stockanalysis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockCandleResponse(
        OffsetDateTime timestamp,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long volume
) {
}
