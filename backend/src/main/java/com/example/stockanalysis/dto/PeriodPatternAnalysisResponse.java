package com.example.stockanalysis.dto;

import java.math.BigDecimal;
import java.util.List;

public record PeriodPatternAnalysisResponse(
        String period,
        Integer patternId,
        String rawPattern,
        String patternName,
        String patternCategory,
        BigDecimal confidence,
        String patternDescription,
        List<PatternBacktestMetricResponse> referenceReturns,
        Boolean imageGenerated,
        String chartImageDataUrl
) {
}
