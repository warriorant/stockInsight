package com.example.stockanalysis.ai;

import java.math.BigDecimal;

public record PatternPredictionResponse(
        Integer patternId,
        String rawPattern,
        BigDecimal confidence
) {
}
