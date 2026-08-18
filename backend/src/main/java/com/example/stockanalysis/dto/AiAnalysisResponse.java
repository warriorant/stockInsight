package com.example.stockanalysis.dto;

public record AiAnalysisResponse(
        String summary,
        String technicalAnalysis,
        String fundamentalAnalysis,
        String risk
) {
}
