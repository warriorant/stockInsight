package com.example.stockanalysis.dto;

import java.util.List;

public record AiAnalysisRequest(
        String symbol,
        String name,
        String market,
        List<PricePointResponse> priceData,
        FinancialDataResponse financialData
) {
}

