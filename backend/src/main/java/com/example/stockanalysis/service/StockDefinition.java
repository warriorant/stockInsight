package com.example.stockanalysis.service;

import java.math.BigDecimal;

public record StockDefinition(
        String symbol,
        String externalSymbol,
        String name,
        String market,
        String sector,
        String industry,
        BigDecimal fallbackPrice,
        BigDecimal fallbackChangeRate,
        String description
) {
}
