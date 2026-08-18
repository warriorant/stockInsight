package com.example.stockanalysis.ai;

import java.util.Optional;

public interface PatternPredictionClient {

    Optional<PatternPredictionResponse> predict(ChartImageResponse chartImage);
}
