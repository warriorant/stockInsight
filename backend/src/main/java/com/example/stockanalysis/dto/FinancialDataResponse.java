package com.example.stockanalysis.dto;

import java.math.BigDecimal;

public record FinancialDataResponse(
        BigDecimal marketCap,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal roe,
        BigDecimal eps,
        BigDecimal revenueGrowth,
        BigDecimal dividendYield,
        BigDecimal debtRatio
) {
}

