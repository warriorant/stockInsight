package com.example.stockanalysis.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ChartPatternAnalysisRequest(
        String symbol,
        String name,
        String market,
        LocalDate targetDate,
        Map<String, List<PricePointResponse>> priceDataByPeriod,
        FinancialDataResponse financialData
) {
}
