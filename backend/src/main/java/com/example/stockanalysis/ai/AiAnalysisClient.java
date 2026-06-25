package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.AiAnalysisRequest;
import com.example.stockanalysis.dto.AiAnalysisResponse;

public interface AiAnalysisClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request);
}

