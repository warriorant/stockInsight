package com.example.stockanalysis.dto;

import java.math.BigDecimal;
import java.util.List;

public record ChartPatternAnalysisResponse(
        String symbol,
        String name,
        Integer patternId,
        String rawPattern,
        String analysisMode,
        String patternName,
        String patternCategory,
        BigDecimal confidence,
        String summary,
        String patternDescription,
        List<PatternBacktestMetricResponse> backtests,
        List<PeriodPatternAnalysisResponse> periodAnalyses,
        List<SimilarPatternCaseResponse> similarCases,
        List<String> checkPoints,
        String disclaimer
) {
}
