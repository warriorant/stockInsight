package com.example.stockanalysis.dto;

import java.math.BigDecimal;

public record SimilarPatternCaseResponse(
        String symbol,
        String name,
        String detectedDate,
        BigDecimal returnAfter1M,
        BigDecimal returnAfter3M,
        BigDecimal returnAfter1Y
) {
}
