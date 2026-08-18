package com.example.stockanalysis.dto;

import java.math.BigDecimal;

public record PatternBacktestMetricResponse(
        String period,
        BigDecimal averageReturn,
        BigDecimal medianReturn,
        BigDecimal positiveRate,
        BigDecimal worstReturn
) {
}
