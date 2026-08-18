package com.example.stockanalysis.dto;

import java.time.LocalDate;
import java.util.List;

public record ManualChartPatternAnalysisRequest(
        LocalDate targetDate,
        List<StockCandleResponse> candles
) {
}
