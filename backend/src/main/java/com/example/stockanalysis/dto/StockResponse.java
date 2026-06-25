package com.example.stockanalysis.dto;

import java.math.BigDecimal;

public record StockResponse(
        String symbol,
        String name,
        String market,
        String sector,
        String industry,
        BigDecimal currentPrice,
        BigDecimal changeRate,
        String description
) {
}

